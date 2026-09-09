---
name: user-preferences
description: The user's durable defaults - preferred apps, saved addresses, frequent contacts. Read before asking for one; update it the moment the user states one.
---

# User Preferences

Preferences are global. Every chat on this phone reads and writes the same
document, at `~/memory/preferences.json`, and the app never reseeds it. A
preference written into the session workspace is lost when the chat ends.

---

## 1. Shape

```json
{
  "apps": {
    "messaging": "WhatsApp",
    "browser": "Chrome",
    "maps": "Google Maps",
    "music": "YouTube"
  },
  "addresses": { "home": "", "work": "" },
  "contacts": { "mom": "", "partner": "" },
  "defaults": { "confirm_destructive": true }
}
```

Sections are open — add a key when the user gives you something that belongs
here and has no home yet.

---

## 2. Rules

1. **Read first.** `cat ~/memory/preferences.json`. When the user says "send a
   message" or "navigate", use the preferred app or saved destination you find
   there instead of asking.
2. **Write the moment it is stated.** "I always use Chrome", "home is 12 Herzl
   St" — record it in the same turn, not at the end of the task. A run that is
   stopped halfway still keeps what it learned.
3. **Read the file, change the key, write it whole back.** Never reconstruct it
   from memory: a key you did not happen to recall is a preference the user has
   to state again.
4. **This file is for short defaults only.** A list that grows — contacts with
   numbers, saved places, anything with structure — belongs in its own skill
   with its own data file. See `personal-skills`.

```sh
cat ~/memory/preferences.json
# then write the full document back
cat > ~/memory/preferences.json <<'EOF'
{ ...the whole document, with your one change... }
EOF
```
