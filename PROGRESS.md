# Progress

## Goal
A fully usable Android app with Codex chat, on-phone execution, per-session files, wireless ADB to the same phone, full ADB tools, floating control UI, live steering, and local stop.

## Current state
- 2026-09-05: Requirements and eight module boundaries agreed.
- Existing APK Manager source reviewed. Reuse candidate: Kadb pairing, persistent identity, localhost connection, discovery.
- Test device: Q8G64TD6ZTB6H6ZL, Android 13, ARM64. USB works; wireless debugging enabled. A shell-UID TCP connection to the current localhost ADB port succeeds. App-UID authenticated self-ADB remains unverified.
- LUNA/MAX runtime feasibility work started. Native runtime support is a release gate.
- 2026-09-06: Added the Android/Bionic localhost CONNECT proxy experiment and
  pinned CA bundle. Unit tests pass; the dev APK builds and installs on Q8.
  On Q8, the app-server process stays running and a real UI login request
  produced two `CONNECT auth.openai.com:443` events and returned the browser
  login state with a one-time code. No code or credential was stored in the
  repository or logs. Full browser completion and signed-in chat are still
  untested.

## Delivery gates
- [ ] Private GitHub repo and repeatable build
- [ ] Native chat and durable sessions/files
- [ ] Real Codex sign-in and streamed conversation on the phone
- [ ] Same-phone wireless ADB pair/connect/reconnect
- [ ] Screenshots, UI reads, taps, typing, swipes, shell and files
- [ ] Floating chat and glow during device control
- [ ] Live steering and local stop with no queued actions after stop
- [ ] Recovery after app/process/network interruption
- [ ] Unit, build, lint, real-device and visual checks
- [ ] Signed usable APK, source commits and setup documentation

## Rules for evidence
Record actual commands and results. Mark untested features explicitly. Do not reduce the goal to a prototype or remote-computer demo.
