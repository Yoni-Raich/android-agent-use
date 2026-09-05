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

Codex app-server is preferred over parsing terminal UI output. The model remains a cloud service; the agent process and workspace live on the phone. The exact Android runtime is under device feasibility testing.
