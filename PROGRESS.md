# Progress

## Status — 2026-09-10

Android Agent is a Developer Preview. It is useful for local testing, but it is
not production-ready.

### Verified

- The public v0.6.2 dev APK was built from merged `main` at tag `v0.6.2` on
  2026-09-10. The full Gradle gate, runtime staging tests, APK metadata check,
  zip alignment check, and APK Signature Scheme v3 verification passed. The
  artifact uses the local debug key and was not installed on a physical phone.
- The public v0.6.1 dev APK was built from merged `main` at tag `v0.6.1` on
  2026-09-09. The full Gradle gate, runtime staging tests, APK metadata check,
  zip alignment check, and APK Signature Scheme v3 verification passed. The
  artifact uses the local debug key and was not installed on a physical phone.
- The v0.6.0 dev APK builds with the Codex runtime staging step.
- The recorded v0.6.0 full gate passed on 2026-09-08:
  `test`, `assembleDevRelease`, `assembleDevDebugAndroidTest`, and
  `:voice:lintDebug`, plus the runtime staging tests.
- The current screen-awake change passed focused JVM tests and a dev debug
  build on 2026-09-09. A designated Android 13 test phone kept its screen on
  during a typed run and released the wake lock when the run ended.
- Focused `read_ui` queries and paging (issue #45) passed JVM tests and a dev
  debug build on 2026-09-10: `./gradlew.bat test :app:assembleDevDebug`, with
  31 tests in `UiObservationSerializerTest`, 21 in `NodeTraversalTest` and 33 in
  `AndroidDeviceToolsTest`, all passing. One test pages a synthetic 3 000-node
  screen through `nextOffset` and asserts every node comes back exactly once and
  in order, which is the behavior the issue reported as unreachable. This proves
  the serializer, both parsers and the ADB gateway wiring only; it was **not**
  run against WhatsApp or any physical phone, so the original reproduction is
  still unverified on hardware.
- The `open_intent` prefilled-text path passed JVM tests and a dev debug build
  on 2026-09-10. New tests cover percent-encoding into the uri, that attaching a
  body never downgrades a decision below `NeedsConfirmation`, the ambiguity and
  length refusals, that a waiting approval raises the app and labels the
  floating card, that an unanswered approval expires with a different error than
  a denial, and that a stopped approval clears its card. Not proven on a phone:
  the app-raise itself is an Android `startActivity` from the application
  context and has no unit coverage, and the reported WhatsApp deep link with a
  message body has not been re-run on hardware.
- Per-operation device capability reporting (issue #44) passed JVM tests and a
  dev debug build on 2026-09-10. New tests cover the composite union over live
  backends, the ready/blocked split, an ADB gateway that reports nothing while
  disconnected, and a runtime snapshot that lists the accessibility tools by
  name and no longer emits "Do not call device tools". This proves the snapshot
  text and the gateway plumbing only; the reported scenario — accessibility on,
  Wireless ADB off, `open_intent` on a WhatsApp deep link — has **not** been
  re-run on a phone. The accessibility gateway's own `readyTools()` is not unit
  tested, because `A11yServiceHandle` needs a bound service.
- The app has Compose chat, per-session workspace storage, Codex app-server
  integration, Wireless ADB support, an accessibility device backend, visible
  control state, local Stop, skills, workflows, and experimental realtime voice
  paths.
- The setup hub, quota meter and run indicator were built and run on an
  attached Android 13 phone on 2026-09-09. The hub read every readiness signal
  correctly, including the overlay permission the app never reported before;
  the quota ring and its breakdown matched the account's real windows; and
  `:core:test`, `:adb:test`, `:a11y:testDebugUnitTest` plus
  `:app:compileProdDebugKotlin` and `:app:compileProdDebugAndroidTestKotlin`
  passed. Two bugs were found by running it rather than by reading it: system
  back closed the whole settings sheet from a detail page, and
  `WIRELESS_DEBUGGING_SETTINGS` does not resolve at all on HyperOS, so every
  tap on it was an unguarded `startActivity`. Both are fixed and the back fix
  was re-verified on the phone.

### Not proven yet

- A complete signed-in Codex chat and device-control flow on a supported phone.
- Reliable same-phone Wireless ADB pairing, reconnect, and app-UID self-ADB.
- Reading the pairing code off the system dialog end to end. The parser has
  unit tests, but AGP disables the accessibility service on every reinstall and
  re-pairing was out of scope, so the live capture has not run once.
- That each readiness dot flips after a trip to a system settings screen, and
  that `ChatUiTest` still passes after the composer and settings changes.
- Full physical checks for accessibility, voice, overlay visuals, recovery,
  and all device tools.
- Production signing, production packaging, and production readiness.

The x86_64 emulator app-server currently exits with `SIGSYS` (exit code 159),
so emulator runtime success must not be inferred from APK installation or
Compose fixture tests. The project-wide `:app:lintDevDebug` task also has a
known pre-existing failure in `AgentInputMethodService.kt`; see
[Known issues](docs/KNOWN_ISSUES.md).

Build output and test results are evidence for those exact checks only. They do
not prove real phone, visual, account, network, or end-to-end success.

Detailed historical evidence is preserved in
[docs/history/PROGRESS-2026-09-09.md](docs/history/PROGRESS-2026-09-09.md).
See the [testing guide](docs/TESTING.md) for the current evidence rules.
