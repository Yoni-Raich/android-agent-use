# Changelog

## Unreleased

## 0.6.2 — 2026-09-10

- `open_intent` now takes the prefilled message body as `text` and encodes it
  into the deep link, instead of expecting a hand-built `?text=` that an
  unencoded space or `&` would truncate or make unparseable. Attaching a body
  still needs the user's approval, and that approval no longer looks like a
  hung tool call: the app is raised so the card can be answered, the floating
  card says "Approve in Android Agent", and a denial, an unanswered approval
  and a stopped run are now three distinct typed errors instead of one "denied
  or expired". A cancelled approval no longer strands its card and block every
  later approval.
- Device control is no longer reported as one global ADB-dependent switch. Each
  turn now carries a snapshot naming the tools that can be called right now and
  the tools whose backend is down, so a disconnected Wireless ADB no longer
  blocks `read_ui`, `tap`, `type_text`, `open_app` or `open_intent` — all of
  which the accessibility service serves with no ADB at all. The legacy "Do not
  call device tools" instruction and the ADB-only framing in the on-device
  `AGENTS.md` are gone. Fixes #44.
- `read_ui` can now be asked a focused question instead of returning the whole
  screen and silently dropping the tail. It takes `text`, `resourceId`, `class`,
  `package`, `rootNodeId`, `clickableOnly` and `scrollableOnly` filters plus
  `offset`, `maxNodes` and `maxChars`, and every reply reports `totalNodes`,
  `returnedNodes`, `matchedNodes` and a `nextOffset` cursor when it left
  something out. Filters change only what is listed, so node ids stay valid for
  `tap_node`, `set_text` and `scroll_node`. Fixes #45.

- Added public Developer Preview documentation, privacy notes, contribution
  guidance, and release evidence rules.
- Added the current screen-awake behavior for active typed and voice runs.
  Physical voice and broader end-to-end checks remain open.

## 0.6.1 — 2026-09-09

- Added the `repo-structure-guard` skill for agents that develop this
  repository. It documents module ownership, safe paths, generated files,
  worktree rules, evidence boundaries, and release checks.
- Added the setup hub, quota meter, run indicator, pairing scan, and bounded
  ADB setup flow from the merged `main` changes.
- Published a dev-flavor test APK with v3 debug signing for local testing.

## 0.6.0 — 2026-09-08

- Added workflows, intent handling, knowledge storage, accessibility capture,
  and proxy diagnostics.
- Published a dev-flavor test APK with v3 debug signing.

## 0.5.0 — 2026-09-08

- Added the in-process Accessibility device backend and shared observation
  routing.
- Added redaction and password-node handling to semantic observations.

## 0.4.0 — 2026-09-08

- Improved chat presentation, session queue behavior, overlay controls, voice
  stop handling, and audio-route policy.

## 0.3.x — 2026-09-07

- Added the staged `read_ui` path, on-device skill catalog behavior, richer chat
  rendering, and the WebRTC voice path.

## 0.2.x — 2026-09-06

- Added experimental realtime voice contracts and the app-private feature gate.

## 0.1.x — 2026-09-06

- Established the Android Agent app, on-phone Codex runtime boundary, session
  workspace, Wireless ADB path, device gateway, and floating control UI.

All entries describe repository changes, not a promise that every feature is
verified on every Android device. See [Testing](docs/TESTING.md) and
[Known issues](docs/KNOWN_ISSUES.md).
