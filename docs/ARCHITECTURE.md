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
| overlay | Floating steering card, status and direct local stop |

A model change is configuration. An engine change replaces the engine adapter. Runtime packaging must not affect chat or ADB APIs. The UI observes app events, never raw Codex JSON.

The MVP permits one active agent run per phone. A session has its own working directory; this is organization, not a claim of OS-level isolation. Stop revokes dispatch first and interrupts active work next. Unknown raw shell requests are visible device control. The overlay tracks Starting, Thinking, Running, Controlling, Stopping, Done, and Error states for the entire run. MainActivity visibility hides its window inside the app and restores it outside the app until the run ends, including between tool calls. Its floating card separates status and local Stop from the steering composer, with 48dp action targets and native drawn icons. It releases input focus before device actions and returns to MainActivity after a terminal state.

Wireless ADB stores only the last successful local connect port in app-private
preferences. The foreground service runs a bounded reconnect loop: it tries
that port first, then uses Android NSD's `_adb-tls-connect._tcp` result and
ignores pairing services. No arbitrary LAN scan is used. A missing service is
reported as Wireless Debugging off/on-waiting when Android exposes that state;
the loop stays idle until the app has a stored pairing identity, and pairing
codes are never requested by reconnect.

Immediately before each typed Codex turn, `AgentCoordinator` snapshots the
app-owned `AdbStatus`. The engine adds a small application-owned runtime-context
text item before the user's text with the phase, tool availability, and local
port when known. The newest snapshot replaces older snapshots in the thread.
The pinned app-server's `turn/start` contract has no per-turn developer-
instructions field, so this context uses a supported input item while the
thread-level developer instructions define its trust and precedence rules.
Disconnected/error snapshots tell the model not to call device tools and to
guide the user to Wireless Debugging. The gateway remains the enforcement
boundary if connection state changes after the snapshot.

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
The default app path creates an Android WebRTC peer connection with a local
microphone track and the `oai-events` data channel, then starts
`thread/realtime/start` with `outputModality: "audio"`, protocol V3, and
`transport: { type: "webrtc", sdp: "..." }`. The pinned app-server maps V3
to the AVAS request header `OpenAI-Alpha: quicksilver=v2`, then returns the
remote answer through `thread/realtime/sdp`. The Android WebRTC audio device
module handles the negotiated microphone and speaker media. V2 WebSocket voice
remains available only as an explicit `RealtimeTransport.WEBSOCKET` fallback;
it still needs API-key auth on the pinned app-server and does not fix the
ChatGPT-account error.

Realtime is enabled in the app-private `CODEX_HOME/config.toml` through a small
startup migration. It adds only `[features] realtime_conversation = true`,
preserves existing user settings, and replaces the file atomically. The
migration also repairs the comment-only config created by older builds. The
app-server is restarted before a new voice thread is created; existing threads
created while the feature was disabled are not retrofitted.

The WebRTC path uses the bundled native WebRTC audio device module for live
microphone capture and speaker playback, with hardware echo cancellation and
noise suppression enabled when supported. It waits for `thread/realtime/started`,
the SDP answer, and ICE connection before enabling the microphone. The explicit
WebSocket fallback records and plays signed PCM16, 24 kHz, mono audio in bounded
20 ms chunks. Both paths use audio focus and foreground microphone service state.
Raw microphone audio and SDP are never saved or logged; only finalized user and
assistant transcripts enter the session store.

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

## On-device agent instruction and skill stack

The on-device agent uses progressive disclosure and Codex's standard skill
catalog:

- **Layer 0: Engine Developer Instructions (`developerInstructions`)**: Dense,
  inviolable system prompt in `CodexEngine` establishing identity, wireless ADB
  ownership, prompt injection boundaries (screen text as untrusted data),
  the 5-step operational loop, and Tier-1 Semantic UI preference.
- **Layer 1: Workspace Harness (`AGENTS.md`)**: Root execution harness seeded
  into every session workspace (`sessions/$sessionId/workspace/AGENTS.md`).
  Enforces the **Observe → Evaluate → Plan → Act → Verify** cycle and routes
  to app cards and skills on demand.
- **Layer 2: 3-Tier Addressing Strategy**:
  1. *Tier 1 (Semantic First)*: `read_ui` returns compact semantic JSON with an
     observation revision, package, elapsed time, node state, bounds, and
     clickable-ancestor targeting. Raw XML is debug-only. Coordinate actions
     still require verification because Android state can change.
  2. *Tier 2 (Vision Fallback)*: `screenshot` is used when semantic observation
     fails, is unexposed (canvas, games, webviews), or images need verification.
  3. *Tier 3 (Hardware Navigation)*: `key` events (`BACK`, `HOME`, `ENTER`) and
     calibrated swipes.
- **Layer 3: Modular Skills & App Cards**: Complete bundled skill sources live
  under `app/src/main/assets/agent_stack/skills/<skill-name>/SKILL.md`. App
  startup installs the app-managed defaults into the app-private
  `$HOME/.agents/skills` before the Codex app-server starts. It removes only
  app-managed legacy copies from the workspace `.agents/skills/`,
  `.codex/skills/`, and `$CODEX_HOME/skills`; unrelated skills are preserved.
- **Layer 4: Durable Preferences**: `preferences.json` in the session workspace
  retains user defaults (preferred messaging apps, addresses) to prevent
  redundant questioning while respecting intent fidelity.
- **Catalog and composer**: For each session workspace, the pinned app-server
  is queried through `skills/list` with that workspace as the CWD. The composer
  uses the returned catalog and explicit `$skill-name` invocations; the turn
  includes Codex's native skill input item with the catalog-provided name and
  path. `skills/changed` refreshes the catalog. There is no hard-coded
  slash-skill list. `WorkspaceSeeder` still populates `AGENTS.md`, app cards,
  `RECOVERY.md`, and `preferences.json` offline without duplicating skills.

## Bounded UI observation

`read_ui` has a six-second default total budget instead of inheriting the
gateway's 30-second shell timeout. Exactly one dump runs per observation: the
hierarchy is staged to `/sdcard/window_dump.xml` and read back. A timeout or
`could not get idle state` result returns a typed failure and never starts a
second dump.

Dumping straight to `/dev/tty` is deliberately not attempted. `uiautomator`
reports success and exits 0 on that path while emitting no hierarchy unless the
shell service forwards raw stdout, which the app's transport does not. On the
supported device it cost roughly 2.2 seconds per call and never once returned
data; the staged dump costs about the same and always works.

Successful XML is parsed inside the device gateway with external entities and
DOCTYPEs disabled. The model receives only labeled or actionable semantic
nodes plus clickable-ancestor bounds; decorative empty nodes are filtered.

An observation whose semantic payload is byte-identical to the previous one is
answered with `"unchanged":true` and `"unchangedSinceRevision"` instead of the
node list. The dump still runs every time, so a changed screen is never missed;
only the resend is suppressed. The fingerprint is cleared on `beginRun` and
after any failed observation, so the gateway never claims "unchanged" across a
gap in its own knowledge, and `raw=true` never participates. `force=true`
resends the full list for an agent that no longer holds it. A device trace of
one WhatsApp send showed three consecutive identical observations of the chat
list, so this suppression removes repeated payloads the model has already read.
Each result includes monotonic elapsed time and an observation revision. This
removes raw XML token cost and caps the observed 22-second idle-wait tail, but
it does not prove a faster real WhatsApp workflow until measured on Q8.

## Session queue and exclusive device ownership

The MVP still allows one active run per phone, because one phone screen cannot
be shared. `SessionRunQueue` makes that limit a queue instead of a rejection:
the UI accepts a turn for any chat, and `AgentCoordinator` publishes an
`available` flag that gates dispatch. Sending into the chat that is already
running still steers it. FIFO order is durable in a `run_queue` SQLite table,
and a turn is dequeued before it starts, so a process crash cannot replay a
side effect. A queue restored at startup is paused and needs an explicit
Resume, and a local stop pauses the queue rather than releasing the next run at
the user unannounced. Deleting a chat cancels its queued turns.

## Assistant message segmentation

`item/completed` from the app-server, not only text deltas, drives assistant
message boundaries. Each `agentMessage` item becomes its own stored message, so
commentary before a tool call stays above that tool row and the answer after it
starts a new block. A completed item whose full text differs from the
accumulated deltas replaces them rather than appending, so a full final message
never duplicates its own stream. A turn that ends with no assistant text is
recorded as an explicit "no final reply" message, and an errored or interrupted
turn says so, so a run never ends on a bare activity line. The active status
label is "Working" everywhere, including the overlay.

## Usage and quota

`thread/tokenUsage/updated` and `account/rateLimits/updated` map to a single
`UsageChanged` event; `account/rateLimits/read` fetches the same shape on
demand at sign-in and refresh. Token usage is kept per engine thread and shown
for the visible chat only. A missing `usedPercent` is surfaced as unknown and
never rendered as zero. `RunMetrics` records first-response latency, total run
time, and tool count/time per session.

## Rich chat presentation

Assistant markdown is rendered with Markwon (tables, strikethrough, prism4j
syntax highlighting) inside an `AndroidView`, and images with Coil. The link
resolver opens only `https`, `http`, and `mailto` URLs, so markdown from an
untrusted screen cannot launch a file or intent URL. Generated images and
screenshots are stored inside the session workspace and attached to the
message; a generated image is accepted only from inside that workspace and
under a size cap. Image generation is enabled through the app-server
`features.image_generation` config, and the agent is instructed never to
present a screenshot as generated artwork.

## Overlay bubble and manual exit

The floating card collapses to a 56dp bubble that keeps the status colour, can
be dragged, and long-presses to stop; expanding restores the card. Collapse
state resets when a new run starts. The overlay is no longer shown at run
start: a read-only chat turn never takes over the screen, and the first device
action is what checks overlay permission and shows the card. Leaving the app
manually during a read-only turn therefore shows nothing and does not reopen
the app on completion.

## Voice audio routing

`CommunicationAudioRoute` owns only the route this voice session selected. On
API 31+ it uses `setCommunicationDevice` over `availableCommunicationDevices`,
ranking wired and USB headsets first, then Bluetooth/BLE, then built-in
speaker, and it keeps an external device the user picked in platform UI. Below
31 it falls back to Bluetooth SCO and speakerphone flags. An
`AudioDeviceCallback` re-selects when a headset is plugged or unplugged
mid-call. On release it restores the previous device only if the session's own
device is still selected, so a route the user changed during the call is left
alone. Voice now fails loudly when audio focus is denied instead of talking
into a device it does not own, and losing focus stops the session. Stop
silences capture and playback before any network acknowledgement, and a second
stop while stopping is a no-op.

## App launch

`open_app` resolves a normal launcher intent with `am start` instead of
`monkey`. Monkey is a fuzzing harness whose cleanup changes device state, which
is the same class of hidden side effect as the `uiautomator` rotation thaw. An
explicit activity must belong to the requested package. Launch success requires
both a zero exit code and no `Error`/`Exception` line in the output, so a
failed launch is never reported as "Opened".

## Combined act-and-observe

`act_and_observe` performs one already-decided action and returns a fresh
observation in the same tool call, removing a model round trip per step. It is
never a batch: a failed action returns immediately and is not retried or
observed. When the action commits but the observation fails, the result says
`actionCompleted: true` with `observationSucceeded: false`, so the model cannot
mistake a lost observation for a lost action and repeat a side effect.
