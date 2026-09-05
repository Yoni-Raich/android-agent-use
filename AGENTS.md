# Working rules

- Write short, clear English.
- Goal: a fully usable on-device Android Codex agent, including chat, session files, wireless self-ADB, visible device control, live steering, and local stop.
- Preserve android_ai_agent_mvp_brief.md. Record decisions in docs/ARCHITECTURE.md and progress in PROGRESS.md.
- Work only in assigned paths. Never edit another project. Read-only tasks must never write files.
- Use LUNA with max reasoning for bounded reading and implementation tasks. The main agent owns integration and final checks. Muse via OpenCode may review code.
- Keep engine, runtime, workspace, ADB transport, device tools, coordinator, chat, and overlay behind core contracts.
- All device operations pass through the tool gateway. Arbitrary ADB shell may control the screen and must enter visible-control mode.
- Stop revokes new tool calls locally before interrupting the engine. Do not claim that already completed side effects can be undone.
- Device tests use adb -s Q8G64TD6ZTB6H6ZL. Never use the other device on the LAN.
- Never commit credentials, device pairing codes, private keys, local.properties, or raw personal screen captures.
- Update PROGRESS.md with real evidence and remaining gaps. Build/install alone do not prove a working app.
- Use conventional commits. dev is the integration branch; main holds validated releases. No public distribution repo is needed for this private MVP.
