# Architecture

One Android project, with replaceable modules and small core contracts.

| Module | Responsibility |
|---|---|
| app | Compose chat, setup, foreground lifecycle, dependency wiring |
| core | Neutral contracts, coordinator, run state, local cancellation |
| engine-codex | Bidirectional Codex app-server protocol and event mapping |
| voice | Android microphone, speaker, and realtime audio lifecycle |
| runtime | On-phone executable provisioning and process supervision |
| workspace | Durable sessions, messages, artifacts and session directories |
| adb | Persistent pairing identity, discovery, localhost transport |
| device-tools | Sole agent-facing device gateway, reads/control/shell/files |
| overlay | Floating chat, glow, status and direct local stop |

A model change is configuration. An engine change replaces the engine adapter. Runtime packaging must not affect chat or ADB APIs. The UI observes app events, never raw Codex JSON.

The MVP permits one active agent run per phone. A session has its own working directory; this is organization, not a claim of OS-level isolation. Stop revokes dispatch first and interrupts active work next. Unknown raw shell requests are visible device control. The overlay tracks Starting, Thinking, Running, Controlling, Stopping, Done, and Error states for the entire run. MainActivity visibility hides its window inside the app and restores it outside the app until the run ends, including between tool calls. Its translucent pill provides local Stop and steering; it releases input focus before device actions and returns to MainActivity after a terminal state.

Wireless ADB stores only the last successful local connect port in app-private
preferences. The foreground service runs a bounded reconnect loop: it tries
that port first, then uses Android NSD's `_adb-tls-connect._tcp` result and
ignores pairing services. No arbitrary LAN scan is used. A missing service is
reported as Wireless Debugging off/on-waiting when Android exposes that state;
the loop stays idle until the app has a stored pairing identity, and pairing
codes are never requested by reconnect.

Codex app-server is preferred over parsing terminal UI output. The model remains a cloud service; the agent process and workspace live on the phone. The APK packages the official ARM64 and x86_64 Linux-musl app-server variants, and Android selects the matching native library directory. The x86_64 emulator now avoids ARM translation, but its app-process launch currently exits with `SIGSYS` (exit code 159), so emulator runtime support remains unproven.

The Android APK stages the code-mode helper as `codex-code-mode-x.so`, because
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

## Realtime voice (experimental)

Voice is isolated behind `RealtimeVoiceEngine` and the separate `voice` module.
The pinned Codex 0.153.4 app-server remains the single JSON-RPC stdio process.
The app starts `thread/realtime/start` with protocol V2 and lets app-server own
its upstream WebSocket; Android does not connect directly to the OpenAI Realtime
endpoint. WebRTC can be added later as another transport without changing chat,
the coordinator, or Android audio capture.

Realtime is enabled in the app-private `CODEX_HOME/config.toml` through a small
startup migration. It adds only `[features] realtime_conversation = true`,
preserves existing user settings, and replaces the file atomically. The
migration also repairs the comment-only config created by older builds. The
app-server is restarted before a new voice thread is created; existing threads
created while the feature was disabled are not retrofitted.

Android records and plays signed PCM16, 24 kHz, mono audio. Capture uses bounded
20 ms chunks and drops old queued audio under backpressure instead of growing
memory. Recording starts only after `thread/realtime/started`. Echo cancellation,
noise suppression, gain control, audio focus, and foreground microphone service
state are used when Android supports them. Raw microphone audio is never saved;
only finalized user and assistant transcripts enter the session store.

Realtime turns still pass through `AgentCoordinator`. A matching realtime turn
may use the same device-tool gateway as typed chat. Local stop first revokes new
tool calls, then interrupts an active delegated turn, stops microphone capture,
and asks app-server to stop the realtime conversation. Completed side effects
cannot be undone.

## Unicode input

Device tools temporarily select the bundled IME and probe its actual editor
connection through a package-scoped broadcast. Android enforces DUMP permission
on the sender via receiver registration. The outgoing broadcast must not set
receiver-permission DUMP, because the receiving app does not hold it. A hidden
sender UID on Android 14+ is accepted only behind that platform permission gate;
known non-shell UIDs are rejected. No text payloads are logged.

The gateway stops immediately after an acknowledged commit. Only explicit
no-delivery/no-editor responses can retry. Missing or ambiguous acknowledgements
fail without sending Enter. Cleanup restores the user's previous IME. Runtime
probe responses replace device-specific dumpsys parsing.

## Release versioning

Every distributed APK increments versionCode and updates versionName in
version.properties before building. Release tag, asset filename and embedded
APK version must match. Previously published assets remain available. 0.1.1 is
versionCode 2; the older 0.1.0 fix releases all used versionCode 1.
