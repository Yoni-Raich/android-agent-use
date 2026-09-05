# Android Codex runtime feasibility (read-only spike)

Status: artifact/package inspection only. No device execution, real sign-in, or app-server turn was run here.

## Decision
Use the official **app-server JSON-RPC over stdio** as the replaceable engine boundary. Pin Codex `rust-v0.153.4`'s ARM64 Linux musl app-server package:

- [app-server package](https://github.com/openai/codex/releases/download/rust-v0.153.4/codex-app-server-package-aarch64-unknown-linux-musl.tar.gz)
- SHA-256: `5673c5a8935ff2f85ca67b489e560fdd5e08fb0f0e2f7426f048ec7449aa4fdc`
- [single app-server archive](https://github.com/openai/codex/releases/download/rust-v0.153.4/codex-app-server-aarch64-unknown-linux-musl.tar.gz), SHA-256 `d2a3d0882f6eb4ddb84dfe1c90c5113dfbe32301f718706da0acd276770d3c75`
- [Apache-2.0 license](https://raw.githubusercontent.com/openai/codex/rust-v0.153.4/LICENSE)

The package was downloaded and hash-checked locally. Its manifest says `aarch64-unknown-linux-musl`, entrypoint `bin/codex-app-server`, resources `codex-resources`, and path tools `codex-path`. It also ships `codex-code-mode-host`, `bwrap`, patched `zsh`, and `rg`. The ELF files are AArch64 static/PIE Linux binaries. This proves packaging, not Android execution.

The official [app-server README](https://raw.githubusercontent.com/openai/codex/rust-v0.153.4/codex-rs/app-server/README.md) documents JSON-RPC 2.0, stdio, `initialize`, `thread/start`/`thread/resume`/`thread/fork`, `turn/start`, streaming item events, `turn/completed`, and interruption. Keep the Android engine behind an interface that can later swap to another provider. Use per-session writable `CODEX_HOME` and `cwd`; capture stdout as protocol and stderr as logs; stop with process destroy/forcible destroy.

## Android packaging boundary
Android's [API 29 behavior change](https://developer.android.com/about/versions/10/behavior-changes-10) forbids executing files extracted in an app-writable home directory for apps targeting API 29+. Therefore do not extract the Codex ELF to `filesDir` and execute it while claiming targetSdk35. Package native executables in the APK's `jniLibs/arm64-v8a` and resolve `applicationInfo.nativeLibraryDir`; keep sessions, config, workspace, and logs in `filesDir`. This is the targetSdk35 implementation hypothesis and still needs Android 13 proof.

There is a layout risk: Codex's [install-context](https://raw.githubusercontent.com/openai/codex/rust-v0.153.4/codex-rs/install-context/src/lib.rs) discovers the package when the executable is under a directory named `bin` or `codex-resources` beside `codex-package.json`. Android's native-library extraction normally flattens/renames files, so a naive `libcodex_app_server.so` may lose automatic discovery of `rg`, `zsh`, `bwrap`, and code-mode host. Preserve the package layout through an Android-specific launcher/path configuration, or build an Android target; do not assume the stock binary is drop-in.

## Community evidence and limits
[AnyClaw](https://github.com/l7-Holy/openclaw-android-assistant) bundles a private Termux userland, Node, Codex, and a localhost CONNECT proxy. Its source uses writable app storage and explicitly sets `targetSdk 28`; it pins old Codex `0.104.0`, downloads bootstrap without a hash, and enables full access. It is a useful design reference for DNS/provisioning only, not a targetSdk35 recipe. [codex-termux](https://github.com/androidly/codex-termux) reports Android ARM64 smoke tests for patched Codex `0.131/0.133` on Android 16 (version/help, ephemeral exec, file I/O, curl), but no real OAuth plus app-server thread/turn test. Neither proves stock `0.153.4` on Android 13.

## Device probe gate
After APK packaging, run on the specified device: `--version`, `--help`, then JSON-RPC `initialize`; only after that test `thread/start` and one real signed-in `turn/start`. Also verify DNS/TLS, a writable session cwd, `rg`/shell/code-mode availability, stop latency, and resume. If static musl DNS fails because Android has no usable `/etc/resolv.conf`, add a pinned Android/Bionic resolver/proxy path; AnyClaw's localhost proxy is evidence of this risk, not proof of correctness. `bwrap` namespaces and all full-access tools remain unverified on Android 13.
