# Contributing

Thanks for helping with Hey Mike. It is a Developer Preview, so clear
evidence and small changes matter more than broad claims.

## Before changing code

- Read [AGENTS.md](AGENTS.md), [PROGRESS.md](PROGRESS.md), and
  [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- Keep changes inside the requested scope and preserve unrelated work.
- Do not commit credentials, tokens, pairing codes, keystores, raw private
  screen captures, or generated build output.

## Repository work by agents

Before an agent changes, reviews, builds, releases, cleans, or adds a skill,
read [repo-structure-guard](.agents/skills/repo-structure-guard/SKILL.md).
It is a development-repository skill and is not shipped in the APK. On-device
skills belong under `app/src/main/assets/agent_stack/skills/`.

## Pull requests

- Use a focused branch and a conventional commit message.
- Explain the change, checks run, and remaining gaps.
- For UI or device work, say whether the result was tested on a real phone or
  only in a fixture/emulator.
- Keep production, release, and end-to-end claims tied to direct evidence.

Useful checks are listed in [Testing](docs/TESTING.md). Use the repository pull
request template when opening a PR.

## Issues

Use the [GitHub issue tracker](https://github.com/Yoni-Raich/hey-mike/issues)
for reproducible bugs and feature requests. Do not include secrets or private
screen content. Use [Security](SECURITY.md) for vulnerability reports.
