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
- 2026-09-06: Added the official x86_64 Codex app-server package alongside the
  ARM64 package so the local emulator can use a native binary. Before this,
  the x86_64 emulator's ARM translation runner aborted the ARM64 app-server
  with `SIGABRT`. The multi-ABI APK installs on `emulator-5554`, but the
  native x86_64 app-process launch exits with `SIGSYS` (code 159), so runtime
  login and chat remain blocked on this emulator. The ARM64 Q8 path remains
  the valid device target.

## Delivery gates
- 2026-09-06: Phone screenshot showed model refresh and chat network failures.
  Confirmed the proxy rejected chatgpt.com although pinned upstream
  model-provider-info/src/lib.rs selects that host for ChatGPT accounts.
  Added the exact TLS host, tests against lookalike hosts, NO_COLOR and ANSI
  cleanup tests. Core/runtime tests and assembleDevDebug passed. No device
  was connected at this check; successful phone chat is still NOT TESTED.
  The bubblewrap/package-layout warning is a separate remaining issue.

- [x] Private GitHub repo and repeatable build
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

## Chat UI refresh - 2026-09-06
- Replaced the purple card-heavy chat with a black canvas, neutral user bubbles,
  open assistant text, content-based Hebrew direction, and a rounded composer.
- Added compact expandable activity/error details. Stop stays visible alongside
  steering; STOPPING disables dispatch and keeps the draft. Scrolling only follows
  updates while the user remains at the end of the conversation.
- Removed the separate overlay permission strip above the app; permission setup
  remains in Settings. Phone system-bar and permission discovery checks remain open.
- PASS: assembleDevDebug and assembleDevDebugAndroidTest. Four Compose device tests
  passed on emulator-5556: Hebrew send/clear, steering plus stop callbacks, preserving
  the draft during STOPPING, and expanding/collapsing diagnostics.
- Visual inspection: Compose captures show readable Hebrew, the new composer, and
  collapsed errors. These are fixture messages, not a real Codex conversation.
- Emulator uses the installed API 36 TV image with phone size/density overrides.
  Actual phone keyboard, gesture/three-button insets, streaming scroll behavior,
  overlay control, and signed-in end-to-end chat still require physical-device checks.
- No physical device connected during this UI verification. Changes are local;
  no new commit or push was made for this UI pass.
- 2026-09-06: Assistant messages now render headings, bullets, emphasis,
  inline code, and fenced code blocks, with a small per-message copy action.
  Five Compose UI tests pass on emulator-5556, including the Markdown fixture.
- 2026-09-06: Reproduced the phone error where Codex looked for
  `nativeLibraryDir/codex-code-mode-host` although the APK only contained a
  `.so` helper. The runtime staging step now patches the pinned app-server's
  helper lookup to `codex-code-mode-x.so`, which Android extracts and the
  emulator can execute directly. `prepare_runtime.py`, APK assembly,
  installation, and direct helper `--help` execution passed. The full
  x86_64 app-server still exits with `SIGSYS` on the TV emulator, and the
  ARM64 phone path remains unverified until Q8 is connected.
- 2026-09-06: Unicode IME input now waits for Android to select the bundled
  input method and expose an editor connection through `dumpsys input_method`.
  The broadcast is retried only a bounded number of times and succeeds only
  on result code 1; missing focus/IME state returns setup guidance and never
  reports text as sent. Device-tools unit tests pass. Hebrew input on the
  physical Q8 device remains NOT TESTED because it is not connected.
- 2026-09-06: Floating control now starts at run startup and reports explicit
  Starting, Thinking, Running, Controlling, Stopping, Done, and Error states.
  The card keeps Stop and steering available, changes its status dot by phase,
  shows a short terminal state, then removes itself and opens Android Agent.
  Core coordinator and overlay presentation tests pass. Overlay permission and
  visual behavior on the physical Q8 device remain NOT TESTED.
