# Releases

Android Agent releases are Developer Preview artifacts until production signing
and complete phone E2E are established.

## Current public artifact

The latest public artifact is v0.7.0:

- package: `dev.androidagent.app.dev`;
- versionCode: `19`;
- APK: `android-agent-0.7.0.apk`;
- SHA-256: `B5CB6196FFDDC50C8AD33FB2650F60E98EEE9B547DF800C00762A5E5C6089625`;
- signing: APK Signature Scheme v3 with the local Android debug key.

This is test-only signing. It is installable for local testing, not a production
release. The public release page is
[v0.7.0 on GitHub](https://github.com/Yoni-Raich/android-agent-use/releases/tag/v0.7.0).

## Version rules

Before a release, update both `versionCode` and `versionName` in
`version.properties`. The tag, APK asset name, and embedded APK version must
match. Never replace a published asset with a different file.

Release candidates should be built from the validated `main` history. Keep
`dev` builds for integration and testing. A flavor named `prod` does not by
itself provide production signing or production readiness.

## Validation recipe

```powershell
.\gradlew.bat test assembleDevRelease assembleDevDebugAndroidTest :voice:lintDebug --no-daemon
python -m unittest tools.test_prepare_runtime
git diff --check
```

For a candidate APK, also verify its package and version with Android build
tools, check zip alignment, and verify APK Signature Scheme v3. Record the
exact artifact hash and every untested hardware or account path. A release
record must say clearly when a physical device was not tested.

## Signing and distribution

The current public APK uses a local debug key. Users should treat it as a
testing artifact and install it only when they accept that trust boundary.
Production distribution needs a separately managed release key, a documented
key-protection process, a production package choice, and fresh phone E2E proof.
Do not commit keystores, credentials, pairing codes, or tokens.
