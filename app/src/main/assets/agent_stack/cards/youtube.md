# YouTube Card

- **Package**: `com.google.android.youtube`
- **Main Launcher Activity**: `com.google.android.apps.youtube.app.WatchWhileActivity`

---

## Key Selectors & Resource IDs

| Element | Selectors / Indicators |
|---|---|
| **Search Icon** | `content-desc="Search"` or `resource-id="com.google.android.youtube:id/menu_item_1"` |
| **Search Input Box** | `resource-id="com.google.android.youtube:id/search_edit_text"` |
| **Video Thumbnail / Title** | `resource-id="com.google.android.youtube:id/title"` |
| **Play / Pause Button** | `content-desc="Play"` or `content-desc="Pause"` |

---

## Standard Flow: Play Video

1. **Launch**: `open_app(package="com.google.android.youtube")`
2. **Search**:
   - Tap `content-desc="Search"`.
   - Call `type_text(text="<search query>", submit=true)`.
3. **Select & Play**:
   - From results, tap the first relevant video thumbnail or title.
   - Video begins playback automatically.
