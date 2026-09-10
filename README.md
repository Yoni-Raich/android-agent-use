# Android Agent

Android Agent is an on-device Codex chat app. The app-server and session
workspace run on the phone. The agent can use controlled screen, input, file,
shell, and Wireless ADB tools on that same phone.

## Status

This repository is a **Developer Preview**. It is for development and local
testing, not production use. The current public APK uses the `dev` package and
test/debug signing. It is not a production-signed release.

The latest recorded version is v0.6.0 (`versionCode` 16). Read
[PROGRESS.md](PROGRESS.md) for the current evidence and open gaps.

## What is here

- Codex app-server JSON-RPC over a supervised on-phone process.
- Chat sessions with local session files and artifacts.
- Wireless ADB pairing, discovery, reconnect, shell, file, and device tools.
- Optional Android Accessibility control when Wireless Debugging is off.
- Visible control state, steering, and local Stop.
- Unicode input through a temporary bundled input method.
- Optional floating overlay and experimental realtime voice.
- On-device skills, app cards, preferences, and saved workflows.

These features are not all proven end to end on a phone. The test boundary is
important: a build, APK install, unit test, or Compose fixture does not prove a
real signed-in conversation or a successful device action.

## Requirements

- Windows development machine with PowerShell, Git, JDK 17, Python 3, and an
  Android SDK with platform tools.
- Android 11 or newer (`minSdk 30`).
- A supported ARM64 Android phone is the practical runtime target today.
- A Codex account for sign-in. No API key is shipped in the APK.

The APK also contains an x86_64 runtime for emulator packaging. The current
x86_64 app-server path exits with `SIGSYS` (159), so emulator installation is
not proof of runtime support.

## Build and install

```powershell
git clone https://github.com/Yoni-Raich/android-agent-use.git
cd android-agent-use
.\gradlew.bat :app:assembleDevDebug --no-daemon
adb devices -l
adb -s <serial> install -r app\build\outputs\apk\dev\debug\app-dev-debug.apk
adb -s <serial> shell am start -n dev.androidagent.app.dev/.MainActivity
```

Select a serial deliberately when more than one device is attached. Do not
use an `unauthorized` or `offline` device. The build may download the pinned
Codex runtime and CA bundle when they are not already cached; the staging script
hash-checks them.

For setup details, see [Getting started](docs/GETTING_STARTED.md). For the
test-safe install and test commands, see [Testing](docs/TESTING.md).

## Permissions and privacy

The app asks for sensitive Android capabilities because it can control the
phone. Accessibility, overlay, microphone, notifications, and package discovery
are optional paths with different uses. Screen text and screenshots can be
sent to the Codex service while a task is running. Raw microphone audio and SDP
are not saved by the app; finalized transcripts may be stored in the session.

Read [Permissions and privacy](docs/PERMISSIONS_AND_PRIVACY.md) before enabling
device control.

## Architecture

The main modules are `app`, `core`, `engine-codex`, `runtime`, `workspace`,
`adb`, `device-tools`, `a11y`, `overlay`, and `voice`. The UI observes app
events instead of raw Codex JSON. Device calls go through the device gateway,
and one phone has at most one active agent run.

See [Architecture](docs/ARCHITECTURE.md) for the boundaries and safety rules.

## Known limits

- Developer Preview only; production signing and full release readiness are
  not established.
- Full signed-in phone chat, voice, recovery, and device-control E2E are still
  incomplete.
- Stop prevents new work and interrupts active work, but it cannot undo a side
  effect that already completed.

See [Known issues](docs/KNOWN_ISSUES.md) for the full list.

## Contributing and security

Start with [Contributing](CONTRIBUTING.md), [Security](SECURITY.md), and the
[Code of Conduct](CODE_OF_CONDUCT.md). Please do not post credentials, pairing
codes, tokens, or private screen captures in issues.

## License

Repository-owned code is provided under the [Apache License 2.0](LICENSE),
subject to the copyright owner's authority. Bundled or fetched third-party
components keep their own licenses; see [NOTICE](NOTICE) and
[third-party license notes](third_party/licenses/openai-codex-app-server-0.153.4-Apache-2.0.txt).

See the [changelog](CHANGELOG.md) for release notes and the
[documentation index](docs/INDEX.md) for all project docs.
