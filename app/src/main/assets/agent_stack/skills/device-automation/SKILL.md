---
name: device-automation
description: Master skill for precise Android device control via ADB. Covers semantic UI hierarchy parsing, bounds calculation, gestures, Unicode text input, key events, and verification.
---

# Android Device Automation Skill

This skill defines the exact mechanisms for interacting with the Android OS and apps through the `AndroidDeviceTools` gateway.

---

## 1. Semantic Hierarchy Parsing (`read_ui`)

Always call `read_ui` to inspect screen elements before tapping.

### Node Structure
A typical uiautomator node looks like:
```xml
<node index="3" text="Search…" resource-id="com.example:id/search_box" class="android.widget.EditText" package="com.example" content-desc="Search query" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[72,140][936,260]" />
```

### Addressing Rules
1. **Search Criteria**: Look for elements where:
   - `text` contains or equals your target label (e.g. `text="Danny"`).
   - `content-desc` matches the accessibility label (e.g. `content-desc="Voice message"`, `content-desc="Navigate up"`).
   - `resource-id` matches the standard Android or app view ID (e.g. `com.whatsapp:id/entry`, `id/search_button`).
2. **Bounds Center Formula**:
   From `bounds="[x1,y1][x2,y2]"`:
   - $x_{center} = \lfloor (x_1 + x_2) / 2 \rfloor$
   - $y_{center} = \lfloor (y_1 + y_2) / 2 \rfloor$
   - Example: `[72,140][936,260]` $\rightarrow x = (72+936)/2 = 504$, $y = (140+260)/2 = 200$.
   - Action: `tap(x=504, y=200)`.
3. **Clickable Ancestor Rule**:
   If the matched text or image node has `clickable="false"`, inspect its parent or enclosing `<node>` elements in the XML. If an enclosing container has `clickable="true"`, tap the center coordinates of that clickable parent container instead of the unclickable child.

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
