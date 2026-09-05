# Android AI Agent — MVP Brief

## Concept

Build an Android app with a **chat interface** where **Codex** acts as the agent and has access to the device through **Wireless ADB**.

The agent should not only return text responses — it should also be able to operate the device itself:

- Open apps
- Tap, type, and swipe
- Read the current UI state
- Run ADB commands
- Store files and information during a session

## Example Use Case

The user could ask:

> Open WhatsApp, go to my conversation with Danny, read his latest message, and reply that we are meeting tomorrow at 6:00 PM.

The agent should be able to navigate the device, inspect the screen, understand the current context, and perform the requested action.

## Agent Control Mode

Whenever the agent takes control of the device, it should be immediately obvious that it is active.

- Show a **small floating chat window**, similar to Gemini's floating overlay.
- Apply a **glowing visual tint / overlay across the screen** so the user clearly sees that the agent is currently controlling the device.
- From the floating chat window, the user should be able to:
  - Send additional prompts while the agent is working
  - Refine or change the instruction in real time
  - See a short status of what the agent is currently doing
  - **Stop the agent immediately**

The intended experience is similar to **Computer Use**, but built directly into Android.

## Useful References

- **AnyClaw / openclaw-android-assistant**  
  https://github.com/l7-Holy/openclaw-android-assistant

- **OpenAI Codex**  
  https://github.com/openai/codex

- **Codex app-server**  
  https://github.com/openai/codex/tree/main/codex-rs/app-server

- **libadb-android**  
  https://github.com/MuntashirAkon/libadb-android

- **codex-termux**  
  https://github.com/androidly/codex-termux
