# Google Chrome Card

- **Package**: `com.android.chrome`
- **Main Launcher Activity**: `com.google.android.apps.chrome.Main`

---

## Key Selectors & Resource IDs

| Element | Selectors / Indicators |
|---|---|
| **URL / Search Bar** | `resource-id="com.android.chrome:id/url_bar"` or `text="Search or type URL"` |
| **Tab Switcher Button** | `resource-id="com.android.chrome:id/tab_switcher_button"` |
| **Menu / More Options** | `content-desc="More options"` |
| **Home Button** | `content-desc="Home"` or `resource-id="com.android.chrome:id/home_button"` |

---

## Standard Flow: Navigate / Search

1. **Launch**: `open_app(package="com.android.chrome")`
2. **Focus Address Bar**:
   - Call `read_ui` and tap the URL bar center (`resource-id="com.android.chrome:id/url_bar"`).
3. **Enter Query / URL**:
   - Call `type_text(text="https://example.com" or "weather today", submit=true)`.
4. **Verify**:
   - Call `read_ui` (or `screenshot` if checking web layout) after page loads.
