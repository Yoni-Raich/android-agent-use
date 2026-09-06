---
name: device-automation
description: Master skill for precise Android device control via ADB. Covers semantic UI hierarchy parsing, bounds calculation, gestures, Unicode text input, key events, and verification.
---

# Android Device Automation Skill

1. **Semantic Hierarchy**: Call `read_ui`. Find matching node (`text`, `content-desc`, `resource-id`).
   Calculate center: x = (x1 + x2) / 2, y = (y1 + y2) / 2 from `bounds="[x1,y1][x2,y2]"`.
   If node is `clickable="false"`, tap its clickable parent container.
2. **Unicode Text Input**: Focus input field with `tap` first, then call `type_text(text="...", submit=false)`.
   The bundled IME handles full Unicode (Hebrew, Arabic, Emoji, etc.).
3. **Swiping**:
   - Scroll down: `swipe(540, 1600, 540, 600, 350)`
   - Scroll up: `swipe(540, 600, 540, 1600, 350)`
   - Always verify with `read_ui` after swiping.
4. **Keyevents**: `key(keycode="BACK")` for dismiss/back, `key(keycode="HOME")` for home, `key(keycode="ENTER")` for enter.
5. **Open App**: `open_app(package="...")` brings the app to the foreground.
