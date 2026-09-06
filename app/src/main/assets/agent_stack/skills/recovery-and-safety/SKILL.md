---
name: recovery-and-safety
description: Safety guardrails, intent preservation rules, and recovery procedures for stuck screens, system dialogs, keyboard obstruction, and app crashes.
---

# Android Agent Safety & Recovery Guide

1. **Confirmation Gates**: Ask user confirmation before financial actions, deletions, or messaging ambiguous contacts.
2. **Intent Preservation**: Never rewrite user's message text or search query.
3. **Keyboard Clearance**: Send `key(keycode="BACK")` to hide soft keyboard hiding target views.
4. **Stuck Loop**: After 2 unchanged UI screens, pause and report or ask user for steering.
