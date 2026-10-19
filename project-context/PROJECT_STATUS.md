# OpenCell project status

## Snapshot and scope

- Prepared: 2026-10-19.
- Repository: `https://github.com/UdaySoni32/OpenCell`.
- Application revision: `84300e4`; context branch: `docs/project-context`.
- This change adds documentation only. It does not change application behavior, merge into `main`, publish a release, or deploy Pages.
- No project files were modified before this context work; the temporary emulator screenshot had already been removed.

## Product

OpenCell is an open-source Android replacement for the Phone and SMS apps. It combines SIM and app-call history, SMS and call controls, a local gateway with CLI/Python/MCP access, and a bring-your-own-provider AI assistant. External actions are routed through permission and audit controls.

## Implemented assistant work

- Persistent assistant threads and messages are stored with Room.
- Structured memories support categories, sources, confidence, confirmation, expiration, usage, and retraction metadata.
- Deterministic memory retrieval filters unconfirmed, expired, retracted, and sensitive facts from normal context.
- Explicit `remember` commands save confirmed user memories without requiring an AI provider.
- The assistant orchestrator validates structured intents and rejects invalid or unsafe model output.
- SMS drafts use the existing action-policy and audit path instead of sending directly from chat.
- Tasks and reminders are persisted in Room.
- Natural-language task and reminder commands are supported.
- Android reminder alarms and notifications are registered through the reminder receiver.
- `POST /v1/Assistant`, `OpenCellClient.ask_assistant`, `opencell assistant ask`, and the MCP `ask_assistant` tool expose assistant responses and action drafts. This endpoint does not execute drafts or persist task/memory changes.
- Voice prompts include open tasks. SMS/call screening and voice still have their own assistant pipelines; they are not all routed through `AssistantOrchestrator`.
- A dependency-free GitHub Pages website and deployment workflow exist. Publication has not been verified; pushing this feature branch alone does not trigger the workflow's `main` push rule.

## Database and validation

- Current Room database schema version: 6.
- Android unit tests were rerun and debug APK assembly passed during this documentation task. See `VERIFICATION.md` for the exact commands and test totals.
- Prior emulator smoke testing at the same application revision verified app launch, Assistant navigation, explicit memory persistence, task creation, reminder scheduling, and reminder firing. No fatal exception, ANR, SQLite, or Room error was observed in the checked logcat window.
- The assistant test emulator did not have notification permission enabled, so reminder database state was verified even though the notification was not visibly displayed. No emulator checks were rerun for this documentation-only change.
- The current offline Python test attempt is blocked before test collection because `httpx==0.28.1` is not cached. A previous attempt lacked `starlette==1.7.0`; do not assume downloading only one package completes the cache.

## Known gaps and next implementation work

- General AI responses require a configured provider. The emulator had no provider configured; real-provider chat, action previews, and remote assistant end-to-end behavior remain unverified.
- The remote adapter calls `decide(message, emptyList())`, while `decide` sends the history list to the LLM. Check that the user's message reaches the model before considering remote questions complete.
- The broad assistant plan is not fully delivered: unified SMS/voice orchestration, contact summaries, commitments, proactive suggestions/quiet hours, dedicated task/memory management APIs, and the complete acceptance pass remain follow-up scope.
- Alarm scheduling uses `setAndAllowWhileIdle`, an inexact alarm API. The receiver marks reminders fired even when notification permission is absent. Exact delivery, permission UX, reboot restoration, task completion, and process-restart behavior need dedicated validation.
- The gateway currently binds to `127.0.0.1:8787`. USB forwarding is supported; the original LAN/Tailscale setup claims do not match the current bind address. Do not widen the bind as part of a documentation change.
- Physical USB-phone testing requires the user's connected, authorized device. Real SIM audio, Shizuku support, remote audio quality, and third-party call-notification fixtures require hardware validation.
- No Dockerfile, single `make test` entry point, or dedicated runtime-version pin files are present. Gradle/dependency versions and `cli/uv.lock` exist, but clean-machine/offline reproducibility is not yet fully established.
- Branch publication needs GitHub authentication in the pushing environment; the push dry-run could not obtain an HTTPS username. No credentials are stored in this context folder.

## Recent implementation history

| Commit | Change |
|---|---|
| `796e48e` | Assistant implementation plan |
| `0fdc88b` | Persistent assistant chat; schema 4 |
| `21bb1c2` | Structured memory and retrieval; schema 5 |
| `4c25e4e` | Structured assistant orchestration and SMS previews |
| `9270e98` | Tasks and reminders; schema 6 |
| `f41eadb` | Assistant gateway/CLI/MCP access and voice task context |
| `84300e4` | Static project website and Pages workflow |

These seven local commits were ahead of `origin/main` when the context branch was created and are included in its history. Existing commits are not rewritten by this task.

## Current documentation snapshot

The detailed product, setup, architecture, and phased delivery context is available in the other Markdown files in this directory. The live source documents are linked in `project-context/README.md`.

## Development rules

- Work toward reproducibility: pin runtimes, retain lockfiles, add a Dockerfile and offline test entry point in a dedicated follow-up, and avoid machine-specific absolute paths.
- Do not commit secrets or private device data.
- Use conventional commits. For new commits in this task, both author and committer must be `Uday Soni <udaysoni.hbd@gmail.com>`; do not add a co-author or another email. Use per-command identity rather than changing global Git settings.
- Run the smallest meaningful project check after each change.

## Next concrete steps

1. Authenticate GitHub for the pushing terminal, then run `git push -u origin docs/project-context`. Do not force-push or push `main`.
2. To resume product validation, connect the phone with USB debugging, run `adb devices -l`, and follow the physical-device checklist in `VERIFICATION.md`.
