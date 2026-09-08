---
name: device-automation
description: Master skill for precise Android device control via ADB. Covers semantic UI hierarchy parsing, bounds calculation, gestures, Unicode text input, key events, and verification.
---

# Android Device Automation Skill

This skill defines the exact mechanisms for interacting with the Android OS and apps through the device tool gateway.

Two backends serve the same tool names. An on-device accessibility service handles observation and touch without any ADB connection; wireless ADB handles shell, file transfer, installs and anything the accessibility API cannot reach, and covers for the accessibility service when it is switched off. The application routes each call — you never pick. The `source` field in an observation says which backend answered (`accessibility` or `uiautomator`), and `stable:false` means the screen had not settled when it was read.

A failure with `errorType` `backend_unavailable`, `a11y_unavailable`, `key_unsupported` or `no_text_focus` means **nothing happened on the device**. Read the `remedy` field and act on it instead of repeating the call.

---

## 1. Compact Semantic Observation (`read_ui`)

Always call `read_ui` to inspect screen elements before tapping.

### Result Structure
The normal result is compact JSON. Raw XML is available only with `raw=true` for debugging:
```json
{"ok":true,"revision":12,"activePackage":"com.example","stable":true,"nodes":[{"nodeId":"n3","text":"Search","resourceId":"com.example:id/search_box","contentDescription":"Search query","bounds":[72,140,936,260],"clickable":true,"enabled":true}]}
```

### Unchanged Screens
When the screen is identical to the previous observation, the node list is not
resent:
```json
{"ok":true,"revision":13,"activePackage":"com.example","stable":true,"unchanged":true,"unchangedSinceRevision":12,"nodeCount":41}
```
Reuse the nodes from revision 12; they are still valid. This is diagnostic
information, not an error. If the action before it was meant to change the
screen, the action did not land — pick a different target or dismiss whatever is
covering it rather than repeating the same tap. Use `force=true` only when the
earlier node list is no longer available to you.

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

---

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
