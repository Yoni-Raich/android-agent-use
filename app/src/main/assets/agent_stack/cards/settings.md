# Android Settings Card

- **Package**: `com.android.settings`
- **Main Launcher Activity**: `com.android.settings.Settings`

---

## Key Selectors & Resource IDs

| Element | Selectors / Indicators |
|---|---|
| **Search Settings** | `text="Search settings"` or `resource-id="com.android.settings:id/search_action_bar"` |
| **Network & Internet** | `text="Network & internet"` or `text="Connections"` |
| **Apps** | `text="Apps"` or `text="Applications"` |
| **Display** | `text="Display"` |
| **Developer Options** | `text="Developer options"` |

---

## Standard Flow: Modify Setting

1. **Launch**: `open_app(package="com.android.settings")`
2. **Search directly**:
   - Instead of deep scrolling, tap the "Search settings" bar.
   - Call `type_text(text="Wireless debugging", submit=true)`.
   - Tap the matching preference item.
3. **Toggle / Check**:
   - Inspect the toggle switch (`class="android.widget.Switch"`).
   - Check `checked="true"` or `checked="false"`. If state needs changing, tap the switch bounds.
