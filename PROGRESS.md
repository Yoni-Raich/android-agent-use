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
- 2026-09-06: Wireless ADB now remembers only the last successful connect port
  in app-private preferences. The foreground service retries that port, then
  falls back to the locally advertised `_adb-tls-connect._tcp` service with
  bounded backoff; pairing endpoints are never used for reconnect. Empty NSD
  results expose an explicit Wireless Debugging off/on-waiting status. ADB
  policy tests pass. Reconnect on Q8 remains NOT TESTED because the device is
  not connected.
- 2026-09-06: Release v0.1.1 published (versionCode 2, versionName 0.1.1).
  Added reasoning effort selection in the UI composer and turn start params.
  Replaced dumpsys parsing with direct package-scoped INPUT_PROBE broadcast
  for Unicode IME readiness. Polished floating control overlay lifecycle and
  in-app hide/restore behavior.
  Validation:
  - Unit tests: all modules passed (:adb, :core, :device-tools, :engine-codex, :overlay, :runtime, :workspace).
  - Runtime staging tests: test_prepare_runtime passed with 2 tests.
  - Android test compilation: assembleDevDebugAndroidTest passed.
  - APK: artifacts/android-agent-0.1.1.apk (SHA-256: D8940A057866E3DBC4B7AC09EA7441F37BFB4974A4ADFEBC41F21E9D506C5154).
- 2026-09-06: Release v0.1.2 published (versionCode 3, versionName 0.1.2).
  - Added in-app automatic update checking and installation.
  - Implemented `AppUpdateManager` querying GitHub Releases API (`repos/Yoni-Raich/android-agent-use/releases/latest`).
  - Added semver comparison with pre-release support and APK asset discovery.
  - Implemented secure download with HTTPS enforcement, strict host allowlist (`github.com`, `githubusercontent.com`), relative redirect resolution, 200ms progress throttling, and path traversal protection.
  - Added FileProvider integration (`cache-path` for `updates/`) with `REQUEST_INSTALL_PACKAGES` permission and package installer intent.
  - Added `UpdateBanner` in chat and manual "Check for updates" in Settings sheet, with dismissed tag persistence in SharedPreferences.
  - Independent peer reviews completed and approved:
    - Claude Opus 4.6 (OpenRouter): Reviewed architecture, redirect handling, and throttling.
    - Muse (OpenCode `opencode/muse-spark-1.3-contributor-free`): Approved with DECISION: APPROVE after verifying strict host allowlist and filename sanitization.
  - Validation:
    - Unit tests: all modules passed (`./gradlew.bat test`), including `AppUpdateManagerTest`.
    - APK: artifacts/android-agent-0.1.2.apk (SHA-256: 89FFB574F8E290B07F9B62B4CF7E9216DE7F693F6E7AB3D6D37FFD9D3DE86B96).
- 2026-09-06: Implemented experimental Codex realtime voice behind separate
  engine and Android-audio contracts.
  - Added a blue voice button based on the supplied ChatGPT composer reference,
    microphone permission flow, foreground microphone state, live transcripts,
    typed text while voice is active, and local start/stop handling.
  - Added V2 `thread/realtime/start`, `appendAudio`, `appendText`,
    `appendSpeech`, and `stop` support against pinned Codex 0.153.4. App-server
    owns the upstream WebSocket. Audio is PCM16 24 kHz mono in bounded 20 ms
    chunks; raw microphone audio is not stored.
  - Realtime turns may call device tools through `AgentCoordinator`. Voice stop
    revokes the gateway before interrupting active work and stopping capture.
  - PASS: `./gradlew.bat test assembleDevDebugAndroidTest assembleDevDebug :voice:lintDebug`
    (466 tasks, 42 executed),
    `python -m unittest tools.test_prepare_runtime`, and
    `git diff --check` (line-ending warnings only).
  - Project-wide `:app:lintDevDebug` remains blocked by the existing unchanged
    suspicious indentation in `AgentInputMethodService.kt:39` (1 error). This
    voice change did not edit that file; the remaining lint output has 18 warnings.
  - NOT TESTED: real sign-in, microphone capture, speaker playback, interruption,
    latency, device tools during voice, and transcript accuracy on physical Q8.
    Q8G64TD6ZTB6H6ZL was not connected; only the prohibited other emulator was
    visible, so no device test, install, or visual check was run.
  - At the time of this implementation entry, changes were still local and no
    release had been published yet. The release result is recorded below after
    the validated main-branch build.
- 2026-09-06: Release v0.2.0 prepared from merged `main`.
  - `versionCode=4`, `versionName=0.2.0`.
  - Full validation passed: `./gradlew.bat test assembleDevRelease
    assembleDevDebugAndroidTest :voice:lintDebug` (624 tasks, 221 executed).
  - APK metadata: `dev.androidagent.app.dev`, versionCode 4, versionName 0.2.0.
  - Asset: `artifacts/android-agent-0.2.0.apk`.
  - The matching SHA-256 is recorded in the GitHub Release body.
  - APK alignment passed and APK Signature Scheme v3 verification passed with
    the local Android debug keystore, matching the prior private test release.
    This is installable for local testing, not production signing.
  - Physical Q8 install and Voice E2E remain NOT TESTED.
- 2026-09-06: Fixed the realtime feature gate for existing and fresh installs.
  - `AndroidRuntimeHost` now merges `[features] realtime_conversation = true`
    into app-private `CODEX_HOME/config.toml` and repairs the comment-only file
    created by v0.2.0.
  - Existing settings and inline comments are preserved; the migration uses an
    atomic sibling replacement and never reads or changes `auth.json`.
  - Added JVM coverage for fresh, partial, existing, and idempotent configs.
  - Bumped `versionCode=5`, `versionName=0.2.1`.
  - PASS: `./gradlew :runtime:testDebugUnitTest --no-daemon`.
  - Full validation passed: `./gradlew.bat test assembleDevRelease
    assembleDevDebugAndroidTest :voice:lintDebug --no-daemon` (624 tasks,
    74 executed), plus `python -m unittest tools.test_prepare_runtime`.
  - APK: `artifacts/android-agent-0.2.1.apk`, metadata
    `dev.androidagent.app.dev`, versionCode 5, versionName 0.2.1.
  - SHA-256: `51E7BA0D5B85E4A34B985D240B040E47F1722574B44D2A69D4D25529F6322638`.
    Zip alignment and APK Signature Scheme v3 verification passed with the
    same local Android debug key used for v0.2.0; this is not production
    signing.
  - NOT TESTED: physical Q8 voice E2E, microphone capture, speaker playback,
    and signed-in realtime upstream connectivity.
- 2026-09-06: Release v0.2.2 published (versionCode 6, versionName 0.2.2).
  - Resolved chat lockout caused by `[UNKNOWN] no rollout found for thread id`:
    - Root cause: In upstream Codex app-server, zero-turn threads (threads on
      which no turn completed, e.g. when voice failed immediately upon startup)
      are not written to `.jsonl` rollout files on disk. When reopening the
      session, `openSession` attempted `thread/resume` on the non-existent
      rollout, throwing RPC error -32600 and permanently locking the session.
    - Fix: `CodexEngine.openSession` now catches recoverable `thread/resume`
      failures, logs a redacted warning, and falls back to `thread/start`.
      `CancellationException` (including `withTimeout` cancellations) is
      strictly preserved and never swallowed.
    - Bounded `CodexEngine.rpcErrorMessage` stderr snapshot to recent lines
      (2-5 lines) so historic startup logs no longer pollute unrelated RPC errors.
  - Resolved `Codex could not find bubblewrap on PATH` warning:
    - Added `codex-path/bwrap -> libcodex_bwrap.so` runtime alias in
      `AndroidRuntimeHost.PACKAGE_LINKS` so `bwrap` is found in the directory
      exported on `PATH`.
  - Improved `AgentViewModel.startVoice` resilience:
    - Preserves the fresh valid thread returned by `openSession` instead of
      restoring a stale dead thread on voice start failures.
    - Rethrows `Throwable` cleanly to avoid swallowing cancellations.
  - Peer Code Review:
    - Muse (OpenCode `opencode/muse-spark-1.3-contributor-free`):
      - Pass 1: Requested changes on silent catch, unverified launcher flags,
        `PACKAGE_LINKS` vs `LIB_MAPPING` symmetry, and `AgentViewModel` rollback.
      - Pass 2: Verified all 5 items addressed. 11/11 tests in `CodexEngineTest`
        passed. Approved with `DECISION: APPROVE`.
  - Validation:
    - Unit tests: all modules passed (`./gradlew.bat test --no-daemon`).
    - Staging test: `python -m unittest tools.test_prepare_runtime` passed.
    - Full build: `./gradlew.bat test assembleDevRelease assembleDevDebugAndroidTest :voice:lintDebug --no-daemon`
      passed (624 tasks, 84 executed).
    - APK: `artifacts/android-agent-0.2.2.apk`, metadata
      `dev.androidagent.app.dev`, versionCode 6, versionName 0.2.2.
    - SHA-256: `AB1B472D7FDF5BEE76D96BAD4E8331A12BBFE4562D712365ED9DFAEB60C82A55`.
    - Zip alignment verified (4-byte alignment passed).
    - APK Signature Scheme v3 verified with local Android debug key.
  - NOT TESTED: physical Q8 voice E2E, microphone capture, speaker playback,
    and signed-in realtime upstream connectivity (Q8 not connected).

## On-device agent instruction & skill stack - 2026-09-06
- Synthesized industry frameworks (DroidRun Mobile Harness, Android Automation Agent, Mobile-Agent v3.5, ADB Agent Bridge) for the on-device Codex agent:
  - **Prompt Hierarchy**:
    - Upgraded `CodexEngine.AGENT_INSTRUCTIONS` into an inviolable system prompt establishing identity, prompt injection boundaries (screen text as untrusted data), the 5-step loop (**Observe → Evaluate → Plan → Act → Verify**), and Tier-1 Semantic targeting preference.
  - **3-Tier Addressing Strategy**:
    - Tier 1 (Semantic First): Always inspect `read_ui` XML hierarchy and calculate element bounds center `((x1+x2)/2, (y1+y2)/2)`. Targets clickable containers when child text views are unclickable. Eliminates blind pixel guessing.
    - Tier 2 (Vision Fallback): `screenshot` reserved for canvas, games, or visual verification.
    - Tier 3 (Hardware/Navigation Keys): `key` events (`BACK` for keyboard/modal dismissal, `HOME`, `ENTER`) and directional swipes.
  - **Modular Skills & App Cards**:
    - `app/src/main/assets/agent_stack/AGENTS.md` (root execution harness)
    - `.agents/skills/device-automation/SKILL.md` (deep ADB and UI manipulation)
    - `.agents/skills/recovery-and-safety/SKILL.md` (intent fidelity, confirmation gates for financial/delete actions, stuck state recovery)
    - `.agents/skills/user-preferences/SKILL.md` (durable preferences management)
    - `.agents/skills/app-cards/SKILL.md` + `cards/` (`whatsapp.md`, `chrome.md`, `maps.md`, `settings.md`, `youtube.md`)
    - `preferences.json` (durable user defaults)
    - `RECOVERY.md` (fast recovery guide)
  - **Automated Workspace Seeding**:
    - Implemented `WorkspaceSeeder` in `workspace` module.
    - Integrated into `LocalSessionStore.workspace(sessionId)`: seeds `AGENTS.md`, skills, and cards into the active session workspace offline while preserving existing `preferences.json`.
  - **Validation**:
    - New unit test `WorkspaceSeederTest` passed (`:workspace:test`).
    - Engine tests passed (`:engine-codex:test`).
    - Core tests passed (`:core:test`).
    - Device tools tests passed (`:device-tools:test`).
  - **Release Validation v0.3.0**:
    - Bumped `versionCode=7`, `versionName=0.3.0` in `version.properties`.
    - Build: `./gradlew.bat assembleDevRelease` passed.
    - APK: `artifacts/android-agent-0.3.0.apk`, metadata `dev.androidagent.app.dev`, versionCode 7, versionName 0.3.0.
    - SHA-256: `CE5B6ECD8ABBE5164EC31A7BE72993D445A34AB829131B62AB3E7E846F530DDC`.
    - Zip alignment verified (4-byte alignment passed).
    - APK Signature Scheme v3 verified with local Android debug key.
    - Git tag: `v0.3.0`.

## Slash command ('/') skills menu & on-device skill discovery fix - 2026-09-07
- Addressed skill discovery root cause on device:
  - Fixed `WorkspaceSeeder`: embedded harness, skills, and app cards are now always seeded regardless of asset directory extraction.
  - Added multi-root seeding: seeds both `.agents/skills/` and `.codex/skills/` into the session workspace.
  - Added `WorkspaceSeeder.seedToCodexHome`: seeds on-device skills directly into `$CODEX_HOME/skills/` (`device-automation`, `recovery-and-safety`, `user-preferences`, `app-cards`) so Codex app-server discovers them during indexing.
  - Updated `CodexEngine.AGENT_INSTRUCTIONS` to explicitly declare the 4 on-device skills, prohibit listing cloud developer skills (`imagegen`, `openai-docs`, `plugin-creator`, etc.), and mandate reporting on-device skills and device tools.
  - Staged non-dot asset directory `app/src/main/assets/agent_stack/skills/` so AAPT does not strip skill files during APK build.
- Interactive Composer Slash ('/') Menu:
  - Added real-time suggestion menu above the text composer in `AndroidAgentScreen.kt` when typing `/`.
  - Displays all 10 available skills and app cards:
    - `/skills` (Core skill listing & capabilities)
    - `/device-automation` (UI automation, bounds calculation, tap, type_text, swipe, key)
    - `/recovery-and-safety` (Safety gates, sensitive confirmations, stuck recovery)
    - `/app-cards` (App cards directory)
    - `/user-preferences` (Durable user defaults)
    - `/whatsapp`, `/chrome`, `/maps`, `/settings`, `/youtube` (Individual app workflows)
  - Real-time filtering by keystrokes after `/`.
  - Tapping an item autocompletes the draft (`/skills` or `/app `).
  - Expanded `/skills` and app-card commands in `AgentViewModel.send()` to ensure crystal clear prompt resolution.
- Validation:
  - `WorkspaceSeederTest` updated and passed with assertions for `.codex/skills/` and `seedToCodexHome`.
  - All unit tests passed (`.\gradlew.bat testDevDebugUnitTest`).
- Release Validation v0.3.1:
  - Bumped `versionCode=8`, `versionName=0.3.1` in `version.properties`.
  - Build: `./gradlew.bat assembleDevRelease` passed.
  - APK: `artifacts/android-agent-0.3.1.apk`, metadata `dev.androidagent.app.dev`, versionCode 8, versionName 0.3.1.
  - SHA-256: `8B33FA42A138C3D055720D5EE7658E3AEEC3786ED1B5E33EDC127C3A6BEC12EB`.
  - Zip alignment verified (4-byte alignment passed).
  - APK Signature Scheme v3 verified with local Android debug key.
  - Git tag: `v0.3.1`.

## WebRTC realtime voice - 2026-09-07
- Added the default native WebRTC voice transport for the pinned Codex
  app-server. Android now creates a peer connection with a local microphone
  track and `oai-events` data channel, sends a V3
  `thread/realtime/start` request with the local SDP offer, and applies the
  `thread/realtime/sdp` answer notification.
- The existing app-server WebSocket PCM path remains available only through
  explicit `RealtimeTransport.WEBSOCKET` selection. It stays on protocol V2
  and keeps its API-key limitation; the app default is now WebRTC.
- WebRTC uses the bundled native audio device module for microphone and
  speaker media. It waits for the started event, SDP answer, and ICE
  connection before enabling audio. Stop and startup failure paths mute and
  close the media session and restore audio focus state.
- Added protocol tests for WebRTC request shape, SDP notification mapping,
  missing SDP rejection, and WebSocket/SDP mismatch rejection.
- PASS: `./gradlew.bat test assembleDevDebugAndroidTest assembleDevDebug
  :voice:lintDebug --no-daemon` (472 actionable tasks, 121 executed).
- PASS: the built debug APK contains `libjingle_peerconnection_so.so` for
  `arm64-v8a` and `x86_64`.
- `:voice:lintDebug` reports only two existing dependency-update warnings.
  Project-wide app lint was not run in this pass.
- NOT TESTED: signed-in WebRTC negotiation, physical Q8 microphone/speaker
  capture, latency, interruption, transcript accuracy, or voice device-tool
  E2E. No install, visual check, commit, push, or release was made.

## WebRTC AVAS header repair - 2026-09-07
- Device testing exposed the pinned app-server error
  `invalid_quicksilver_alpha_header`: WebRTC V1 selected
  `OpenAI-Alpha: quicksilver=v1`, but AVAS now requires
  `OpenAI-Alpha: quicksilver=v2`.
- Official Codex source comparison confirmed that WebRTC V3 selects the
  required `quicksilver=v2` header while V2 remains unsupported for WebRTC.
  Updated only the WebRTC request from V1 to V3; the explicit WebSocket V2
  fallback is unchanged. No binary patch or API key is needed.
- Focused request-shape tests now assert WebRTC V3 and still assert WebSocket
  V2. Fresh build passed with `test assembleDevDebug :voice:lintDebug
  --no-daemon`. APK: `app/build/outputs/apk/dev/debug/app-dev-debug.apk`,
  SHA-256 `08C15ADE7ACCF6BAA6C33D0F8EB78187350CD807B26173F9768C7F32A486FEBA`.
  The APK contains WebRTC native libraries for `arm64-v8a` and `x86_64`.
  It was not installed or physically retested in this task.
