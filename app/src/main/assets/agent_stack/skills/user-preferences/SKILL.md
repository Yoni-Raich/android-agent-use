---
name: user-preferences
description: Manage persistent user preferences, default applications, and frequent addresses to minimize redundant user questioning.
---

# User Preferences System

1. Check `preferences.json` in workspace before asking user for preferred app or address.
2. If preference exists, use it silently.
3. If preference is learned, update `preferences.json` in workspace.
