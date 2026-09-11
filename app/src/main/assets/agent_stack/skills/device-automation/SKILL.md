---
name: device-automation
description: Master skill for precise Android device control through the accessibility service or ADB. Covers semantic UI hierarchy parsing, bounds calculation, gestures, Unicode text input, key events, intents, and verification.
---

# Android Device Automation Skill

This skill defines the exact mechanisms for interacting with the Android OS and apps through the device tool gateway.

Two backends serve the same tool names. An on-device accessibility service handles observation and touch without any ADB connection; wireless ADB handles shell, file transfer, installs and anything the accessibility API cannot reach, and covers for the accessibility service when it is switched off. The application routes each call — you never pick. The `source` field in an observation says which backend answered (`accessibility` or `uiautomator`), and `stable:false` means the screen had not settled when it was read.

A failure with `errorType` `backend_unavailable`, `a11y_unavailable`, `key_unsupported` or `no_text_focus` means **nothing happened on the device**. Read the `remedy` field and act on it instead of repeating the call.

Availability is **per operation**, never one global switch. The turn's runtime snapshot lists the tools you can call now and the tools with no live backend. Only the ADB-served operations — `shell`, `push_file`, `pull_file`, `install_apk` — need Wireless Debugging. `read_ui`, `screenshot`, `tap`, `tap_node`, `swipe`, `scroll_node`, `key`, `type_text`, `set_text`, `open_app`, `wait_for_change`, `resolve_intent` and `open_intent` are all served by the accessibility service with no ADB connection at all. A disconnected ADB is never a reason to refuse one of them, and an ordinary deep link is not an ADB operation.

---

## 1. Compact Semantic Observation (`read_ui`)

Always call `read_ui` to inspect screen elements before tapping.

### Result Structure
The normal result is compact JSON. Raw XML is available only with `raw=true` for debugging:
```json
{"ok":true,"revision":12,"activePackage":"com.example","stable":true,"nodes":[{"nodeId":"n3","text":"Search","resourceId":"com.example:id/search_box","contentDescription":"Search query","bounds":[72,140,936,260],"clickable":true,"enabled":true}]}
```

### Focused Queries and Paging
A busy screen does not fit in one reply. Never treat that as "the rest is not
there" — narrow the question, or page through it.

| Argument | Effect |
|---|---|
| `text` | Node whose `text` or `contentDescription` contains this (case-insensitive). |
| `resourceId` | Node whose `resourceId` contains this. |
| `class` | Node whose class name contains this, e.g. `EditText`, `RecyclerView`. |
| `package` | Node from this package, e.g. `package="whatsapp"`. |
| `rootNodeId` | That node and every node under it, and nothing else. |
| `clickableOnly` / `scrollableOnly` | Only what can be tapped, or only what can be scrolled. |
| `offset` | Skip this many matches. Use the `nextOffset` the previous reply handed you. |
| `maxNodes` / `maxChars` | Lower the caps for a small, cheap reply. |

Filters combine with AND. They change only what is **listed**: every node is
still on screen, and its `nodeId` still works with `tap_node`, `set_text` and
`scroll_node`.

Every reply reports what it left out:
```json
{"ok":true,"revision":12,"truncated":true,"totalNodes":812,"returnedNodes":96,"matchedNodes":240,
 "query":{"package":"whatsapp"},"nextOffset":96,"hint":"Nodes 1-96 of 240 matching. ...","nodes":[...]}
```
- `truncated:true` with `nextOffset` means there is more. Call `read_ui` again
  with `offset=<nextOffset>`, or ask a narrower question.
- `matchedNodes:0` means your filter matched nothing while `totalNodes` were on
  screen. That is a bad filter, not an empty screen.
- `rootNodeId` naming a node that is not on screen fails with
  `errorType:"ui_unknown_node"` instead of returning an empty list.

Worked example — find one contact in a long chat list:
```text
read_ui(package="whatsapp", text="Amir")   -> the row and its labels only
read_ui(rootNodeId="n42")                  -> everything inside that row
read_ui(clickableOnly=true, maxNodes=40)   -> just what can be tapped
```

### Unchanged Screens
When the screen and the query are both identical to the previous observation,
the node list is not resent:
```json
{"ok":true,"revision":13,"activePackage":"com.example","stable":true,"unchanged":true,"unchangedSinceRevision":12,"nodeCount":41}
```
Reuse the nodes from revision 12; they are still valid. This is diagnostic
information, not an error. If the action before it was meant to change the
screen, the action did not land — pick a different target or dismiss whatever is
covering it rather than repeating the same tap. Use `force=true` only when the
earlier node list is no longer available to you. Changing the query is enough on
its own to get a fresh reply, so a different filter or `offset` is never
suppressed as unchanged.

### Addressing Rules
1. **Search Criteria**: Look for elements where:
   - `text` contains or equals your target label.
   - `contentDescription` matches the accessibility label.
   - `resourceId` matches the Android or app view ID.
2. **Bounds Center Formula**:
   From `bounds:[x1,y1,x2,y2]`:
   - $x_{center} = \lfloor (x_1 + x_2) / 2 \rfloor$
   - $y_{center} = \lfloor (y_1 + y_2) / 2 \rfloor$
   - Example: `[72,140][936,260]` $\rightarrow x = (72+936)/2 = 504$, $y = (140+260)/2 = 200$.
   - Action: `tap(x=504, y=200)`.
3. **Clickable Ancestor Rule**:
   If the matched node has `clickable:false`, use `clickableAncestor.bounds` when supplied instead of guessing a parent.

### Typed Read Failures
- `ui_timeout` and `ui_idle_failure` are bounded failures. Do not repeat the same read in a loop.
- Use `screenshot` when visual state is enough, or perform one bounded retry only after a real state change.
- `ui_parse_failure` means semantic parsing failed safely. Use `read_ui(raw=true)` only to debug it.
- `ui_unknown_node` means the `rootNodeId` you passed is not on the current screen. Re-read without it and use an id from that reply.

---

## 1b. Node Addressing (only when these tools appear in your tool list)

`tap_node`, `set_text`, `scroll_node` and `wait_for_change` are served by the
accessibility backend. **They exist only in chats started after they shipped.**
If they are not in your tool list, this whole section does not apply — use the
bounds-centre maths in section 1 instead. Never call a tool you were not given.

When they are available, prefer them over coordinates: they act on the node
itself, so they cannot miss because the screen scrolled a few pixels.

- `tap_node(nodeId, observationId)` — both ids are required. `observationId`
  comes from the `read_ui` reply the node was listed in. A node from a stale
  observation is refused with an explanation rather than tapped blindly.
  After an `"unchanged":true` reply, the `observationId` you already hold is
  still accepted.
- `set_text(nodeId, observationId, text, submit?)` — replaces the field's whole
  contents. Check `verified` in the reply: some chat and Compose inputs accept
  the action and keep their old value. If `verified` is false, fall back to
  tapping the field and using `type_text`.
- `scroll_node(nodeId, observationId, direction)` — `forward`, `backward`, `up`,
  `down`, `left`, `right`. More reliable inside a list than a swipe gesture.
  `success:false` usually means the list is already at that end.
- `wait_for_change(timeoutMs?)` — blocks until the screen changes and settles.
  Use it after an action that starts a transition instead of polling `read_ui`.
  `changed:false` means nothing moved, so the previous action did not land.

## 2. Text Input & IME (`type_text`)

The app provides a dedicated Unicode Input Method Service (`AgentInputMethodService`).

### Standard Input Sequence
1. **Focus First**: Always `tap` the center of the `EditText` node before calling `type_text` to guarantee cursor focus.
2. **Dispatch Text**: Call `type_text(text="...", submit=false)` (or `submit=true` if pressing Enter should execute the search/send).
3. **Unicode Support**: The IME bridge handles full UTF-8, Hebrew, Arabic, CJK, special symbols, and emoji seamlessly without shell escaping errors.
4. **Verification**: After typing, call `read_ui` to verify that the text appears in the input field.

---

## 3. Scrolling & Gestures (`swipe`)

Android coordinate system: $(0,0)$ is top-left, $(W, H)$ is bottom-right.

### Scroll Directions
- **Scroll Down (Reveal content below)**:
  - Swipe finger from bottom towards top:
  - `swipe(x1=540, y1=1600, x2=540, y2=600, durationMs=350)`
- **Scroll Up (Reveal content above)**:
  - Swipe finger from top towards bottom:
  - `swipe(x1=540, y1=600, x2=540, y2=1600, durationMs=350)`
- **Swipe Left / Right (Carousels & Tabs)**:
  - Swipe Left (next tab/page): `swipe(x1=900, y1=1000, x2=180, y2=1000, durationMs=300)`
  - Swipe Right (previous tab/page): `swipe(x1=180, y1=1000, x2=900, y2=1000, durationMs=300)`

### Verification
Always re-dump the UI (`read_ui`) after a swipe to verify that the viewport scrolled and target elements became visible.

---

## 4. System Keyevents (`key`)

Use standard Android key events for reliable system navigation:
- `key(keycode="BACK")`: Close keyboards, dismiss dropdowns/popups, or return to previous screen.
- `key(keycode="HOME")`: Return to device launcher/homescreen.
- `key(keycode="ENTER")`: Submit focused form or search.
- `key(keycode="APP_SWITCH")`: Open Android overview/recent apps.

---

## 5. App Lifecycle (`open_app`)

- To open an app by package: `open_app(package="com.example.app")`.
- When an app is already open but in the background, `open_app` brings it directly to the foreground without resetting state.

---

## 6. Deep Links and Intents (`resolve_intent`, `open_intent`)

A deep link that lands on the target beats `open_app` plus a sequence of taps. Use `resolve_intent` first when you are not sure the link is supported.

### Prefilled message bodies

Pass the body as `text`. **Do not build `?text=` into the uri yourself** — an unencoded space or `&` either truncates the message at the first separator or fails uri parsing outright:

```text
open_intent(uri="https://wa.me/972500000000", text="on my way & almost there", package="com.whatsapp")
```

The body is percent-encoded and attached for you. `text` needs a uri to attach to, is capped at 400 characters, and is refused if the uri already carries a payload (`text`, `body`, `subject`, `message`, `amount`, `cc`, `bcc`) — two payloads is ambiguous, so pass one or the other, never both.

### Approvals block the call

Anything that acts on the user's behalf — a prefilled message, a payment, any `sms:`/`mailto:`/`SENDTO` destination — pauses on an approval **the user must answer inside the Hey Mike app**. The app is raised to the front when this happens, and the floating card reads "Approve in Hey Mike".

`open_intent` does not return until they answer, so **say that you are waiting before you call it**. Adding `text` to a link that opened instantly without it is exactly what turns it into an approval, so expect the pause.

Three different outcomes, and they mean different things:

| `errorType` | Meaning | What to do |
|---|---|---|
| `intent_denied` | The user said no. | Do not retry. Ask what they want instead. |
| `approval_timeout` | Nobody answered in time. | Tell the user it is waiting in the app, then call again once they have answered. |
| `intent_not_approved` | The run stopped first. | Nothing was launched. |

A successful launch only means the intent was dispatched. Confirm with `read_ui` that the expected screen actually opened.
