# WhatsApp Card

- **Package**: `com.whatsapp`
- **Main Launcher Activity**: `com.whatsapp.HomeActivity`

---

## Key Selectors & Resource IDs

| Element | Selectors / Indicators |
|---|---|
| **Top Search Icon** | `resource-id="com.whatsapp:id/search_icon"` or `content-desc="Search"` |
| **Search Input Field** | `resource-id="com.whatsapp:id/search_input"` or `com.whatsapp:id/search_src_text"` |
| **Chat Contact Name** | `resource-id="com.whatsapp:id/conversations_row_contact_name"` or `text="<Name>"` |
| **Message Input Field** | `resource-id="com.whatsapp:id/entry"` or `text="Message"` |
| **Send Button** | `resource-id="com.whatsapp:id/send"` or `content-desc="Send"` |
| **Back Button** | `resource-id="com.whatsapp:id/back"` or `content-desc="Navigate up"` |

---

## Standard Flow: Send Message

1. **Launch**: `open_app(package="com.whatsapp")`
2. **Locate Contact**:
   - Tap `content-desc="Search"`.
   - Call `type_text(text="Danny", submit=true)`.
   - Call `read_ui` and tap the conversation row matching `"Danny"`.
3. **Draft & Send**:
   - Tap the message entry field (`resource-id="com.whatsapp:id/entry"`).
   - Call `type_text(text="We are meeting tomorrow at 6:00 PM.", submit=false)`.
   - Call `read_ui` to verify text was entered. The voice note icon now transforms into the **Send** button (`content-desc="Send"`).
   - Tap the Send button center.
4. **Verify**: Call `read_ui` to confirm the sent message bubble is present in the conversation history.
