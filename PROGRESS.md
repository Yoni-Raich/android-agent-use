# Progress

## Goal
A fully usable Android app with Codex chat, on-phone execution, per-session files, wireless ADB to the same phone, full ADB tools, floating control UI, live steering, and local stop.

## Current state
- 2026-09-05: Requirements and eight module boundaries agreed.
- Existing APK Manager source reviewed. Reuse candidate: Kadb pairing, persistent identity, localhost connection, discovery.
- Test device: Q8G64TD6ZTB6H6ZL, Android 13, ARM64. USB works; wireless debugging enabled. A shell-UID TCP connection to the current localhost ADB port succeeds. App-UID authenticated self-ADB remains unverified.
- LUNA/MAX runtime feasibility work started. Native runtime support is a release gate.

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
