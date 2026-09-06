# Google Maps Card

- **Package**: `com.google.android.apps.maps`
- **Main Launcher Activity**: `com.google.android.maps.MapsActivity`

---

## Key Selectors & Resource IDs

| Element | Selectors / Indicators |
|---|---|
| **Search Bar** | `text="Search here"` or `content-desc="Search"` or `resource-id="com.google.android.apps.maps:id/search_omnibox_text_box"` |
| **Directions Button** | `content-desc="Directions"` or `text="Directions"` |
| **Start Navigation Button** | `content-desc="Start"` or `text="Start"` |

---

## Standard Flow: Navigate to Destination

1. **Launch**: `open_app(package="com.google.android.apps.maps")`
2. **Search Location**:
   - Tap `text="Search here"`.
   - Call `type_text(text="<destination address>", submit=true)`.
3. **Select & Route**:
   - In search results, tap the target location.
   - Tap "Directions" -> tap "Start" for navigation.
