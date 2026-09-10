# Testing and evidence

Tests in this repository cover different boundaries. Keep those boundaries
explicit when reporting a result.

## Useful commands

Run these from the repository root in PowerShell:

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat :app:assembleDevDebug --no-daemon
.\gradlew.bat :device-tools:test :core:test --no-daemon
python -m unittest tools.test_prepare_runtime
git diff --check
```

The recorded v0.6.0 full validation also used:

```powershell
.\gradlew.bat test assembleDevRelease assembleDevDebugAndroidTest :voice:lintDebug --no-daemon
```

That full gate passed on 2026-09-08. The focused screen-awake change passed
`:core:test`, `:app:testDevDebugUnitTest`, `:overlay:testDebugUnitTest`, and
`:app:assembleDevDebug` on 2026-09-09. These are recorded results, not a claim
that every later environment will pass.

## Evidence levels

- **PASS** means the named command or observation completed successfully.
- **NOT TESTED** means no evidence was collected for that behavior.
- **BLOCKED** means a known environment or product limit prevents the check.

Builds prove compilation and packaging. Unit tests prove the covered logic.
Compose fixture tests prove fixture UI behavior. APK installation proves only
that Android accepted the APK. None of these alone proves real account sign-in,
streamed chat, visual quality, device control, or voice success.

## Current gaps

The full signed-in phone chat and complete device-control flow are not proven.
Physical checks are still needed for the Accessibility service, realtime voice,
overlay visuals, recovery, Wireless ADB pairing/reconnect, and several device
tools. The emulator runtime is also not a substitute for an ARM64 phone.

CI runs `:core:test`, `:app:assembleDevDebug`, and `:app:lintDevDebug`, and
all three are expected to pass. A green CI run is not the full gate.

## Protecting a configured phone

Do not run `connectedDevDebugAndroidTest` against a phone whose app data matters.
The Android Gradle test flow installs the test APK and then uninstalls the app
and test APK. That removes app-private sign-in, sessions, and staged runtime
files, and installing the test APK can disable the Accessibility service.

For a stateful phone, install both APKs with `adb -s <serial> install -r`, enable
Accessibility by hand if needed, and run only a selected instrumentation class
with `adb -s <serial> shell am instrument ...`. Even then, instrumentation
force-stops the package, so it cannot observe a live Accessibility service in
the same process. See [Known issues](KNOWN_ISSUES.md).
