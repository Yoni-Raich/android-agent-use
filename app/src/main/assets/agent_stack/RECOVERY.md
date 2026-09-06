# Fast Recovery Guide

If you encounter an unexpected UI situation or error:

1. **Keyboard Blocking View**:
   - Call `key(keycode="BACK")` to hide the keyboard.
   - Call `read_ui` to re-inspect full screen bounds.

2. **Tap Had No Effect**:
   - Check if the element was a leaf node with `clickable="false"`. If so, find its clickable parent `<node clickable="true">` and tap the parent center.
   - Or swipe slightly (`swipe`) if the element is partially off-screen.

3. **Unexpected Dialog / Pop-up**:
   - Inspect dialog text via `read_ui`.
   - If related to user intent (e.g. required app permission), select "Allow" / "While using the app".
   - If unrelated promo or ANR dialog, dismiss with `key(keycode="BACK")` or tap "Wait" / "Cancel".

4. **App Crashed or Closed**:
   - Call `open_app(package="<target-package>")` to relaunch.

5. **Stuck / Looping**:
   - If after 2 attempts the state remains unchanged, pause and ask the user for guidance or clarification.
