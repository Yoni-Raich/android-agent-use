# Releases

Android Agent releases are Developer Preview artifacts until production signing
and complete phone E2E are established.

## Current public artifact

The latest recorded artifact is v0.6.0:

- package: `dev.androidagent.app.dev`;
- versionCode: `16`;
- APK: `android-agent-0.6.0.apk`;
- SHA-256: `870E40E2A617FD3DF2484D60F83FFD8361AD4D15BB658C7D668359D53D274BD3`;
- signing: APK Signature Scheme v3 with the local Android debug key.

This is test-only signing. It is installable for local testing, not a production
release. The public release page is
[v0.6.0 on GitHub](https://github.com/Yoni-Raich/android-agent-use/releases/tag/v0.6.0).

## 0.6.1 release candidate

The v0.6.1 candidate was built on 2026-09-09 from the latest validated `main`
history plus the repository structure guard documentation:

- package: `dev.androidagent.app.dev`;
- versionCode: `17`;
- APK: `android-agent-0.6.1.apk`;
- SHA-256: `DAA863A1BC0D9379526F8404423F684AB3A1368DE27A2119B40E90D71B832732`;
- signing: APK Signature Scheme v3 with the local Android debug key;
- validation: full Gradle gate, runtime staging tests, metadata, zip alignment,
  and v3 signature verification passed;
- device status: not installed or tested on a physical phone in this release
  step;
- publication status: candidate only; it is not a public GitHub release yet.

The signed local artifact is kept under the ignored `artifacts/` directory.

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
