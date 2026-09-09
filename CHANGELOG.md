# Changelog

## Unreleased

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
- Published a dev-flavor test APK with v3 debug signing.

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
