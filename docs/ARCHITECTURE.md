# Architecture

One Android project, with replaceable modules and small core contracts.

| Module | Responsibility |
|---|---|
| app | Compose chat, setup, foreground lifecycle, dependency wiring |
| core | Neutral contracts, coordinator, run state, local cancellation |
| engine-codex | Bidirectional Codex app-server protocol and event mapping |
| runtime | On-phone executable provisioning and process supervision |
| workspace | Durable sessions, messages, artifacts and session directories |
| adb | Persistent pairing identity, discovery, localhost transport |
| device-tools | Sole agent-facing device gateway, reads/control/shell/files |
| overlay | Floating chat, glow, status and direct local stop |

A model change is configuration. An engine change replaces the engine adapter. Runtime packaging must not affect chat or ADB APIs. The UI observes app events, never raw Codex JSON.

The MVP permits one active agent run per phone. A session has its own working directory; this is organization, not a claim of OS-level isolation. Stop revokes dispatch first and interrupts active work next. Unknown raw shell requests are visible device control. Known read-only tools may run without the glow. The overlay must be visible before control starts.

Codex app-server is preferred over parsing terminal UI output. The model remains a cloud service; the agent process and workspace live on the phone. The APK packages the official ARM64 and x86_64 Linux-musl app-server variants, and Android selects the matching native library directory. The x86_64 emulator now avoids ARM translation, but its app-process launch currently exits with `SIGSYS` (exit code 159), so emulator runtime support remains unproven.

The Android APK stages the code-mode helper as `codex-code-mode.so`, because
Android extracts `.so` entries into `nativeLibraryDir` but does not preserve a
no-extension executable there. The staging script patches the helper-name
lookup in the pinned app-server copy to this exact filename and fails closed
if the upstream binary layout changes. The downloaded official archive stays
unchanged, and the manifest records the staged file hashes.

## Android network compatibility

The app-server packages are the official `aarch64-unknown-linux-musl` and
`x86_64-unknown-linux-musl` builds. On
Android, the app UID does not have `/etc/resolv.conf`, so the musl resolver can
fail even when Android/Bionic and the browser can reach OpenAI. Runtime starts
one app-owned HTTP `CONNECT` proxy on `127.0.0.1` before spawning Codex. The
proxy resolves and opens only allowlisted OpenAI HTTPS destinations, then
blindly tunnels TLS; it never terminates TLS or records request data.

The APK contains a hash-pinned Mozilla-derived PEM bundle. Runtime copies and
validates it under app-private files and passes both `SSL_CERT_FILE` and
`CODEX_CA_CERTIFICATE`. `HTTPS_PROXY` and `HTTP_PROXY` are set in both cases,
`NO_PROXY` keeps stdio/local traffic direct, and `CODEX_SANDBOX` is removed.
The engine retains a short redacted stderr tail and redacted RPC error data so
DNS, TLS and connection failures remain diagnosable without exposing tokens or
device codes. Proxy lifecycle follows the supervised app-server and closes on
stop or failed startup.

The CONNECT allowlist includes `chatgpt.com:443`: in pinned Codex 0.153.4,
ChatGPT account sessions use `https://chatgpt.com/backend-api/codex` for
models and responses. Allowing only auth.openai.com and api.openai.com lets
device-code login succeed while blocking signed-in chat. The runtime sets
NO_COLOR and strips terminal formatting from redacted diagnostics.

## Chat presentation

The app owns presentation only: a black conversation canvas, neutral user bubbles,
selectable assistant text, and expandable diagnostic/activity rows. The composer
uses the existing send, steer, stop, model, and attachment action contracts.
Stop remains reachable while a steering draft exists; STOPPING blocks dispatch
and preserves that draft. Terminal formatting is removed before display.
Compose fixture tests exercise UI callbacks without starting or authenticating
Codex. They do not establish real runtime, device-control, or network success.
