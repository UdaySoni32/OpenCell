# OpenCell architecture and code map

This describes the source at application revision `84300e4`, not every proposed feature in the original plans. Paths below are relative to the repository root.

## Stack and entry points

- Android: Kotlin, Jetpack Compose/Material 3, coroutines/Flow, Room, and DataStore. `app/build.gradle.kts` sets namespace/application ID `com.opencell`, minimum API 30, compile/target API 36, version `0.1.0`, and JVM target 17.
- Build: Gradle wrapper 9.6.0, AGP 9.4.0, Kotlin plugin catalog version 2.2.10, KSP 2.3.5, Room 2.7.0, and Ktor 2.3.12. Treat `gradle/libs.versions.toml` and the wrapper properties as authoritative; the older plans list different versions.
- Python: `cli/pyproject.toml` declares Python 3.10+, Typer, HTTPX, websockets, and MCP 2.x. `cli/uv.lock` locks the dependency resolution. The optional `brain` extra supplies speech-server dependencies.
- Dependency injection: `app/src/main/java/com/opencell/OpenCellApp.kt` creates one `AppContainer` with repositories, settings, policy execution, pairing, the orchestrator, and voice/SMS controllers.
- Main UI: `app/src/main/java/com/opencell/ui/MainActivity.kt`; feature screens are under `ui/`. The bottom navigation includes Calls, Messages, Contacts, Assistant, and Settings.

## Android packages

All entries in this table are under `app/src/main/java/com/opencell/`.

| Path | Responsibility |
|---|---|
| `telecom/` | Default-dialer integration, in-call service, call state/control, call placement, incoming-call notifications |
| `sms/` | Default-SMS receivers, provider-backed messages, sending and status notifications |
| `contacts/` | Read-only contact lookup/search |
| `calllog/` | SIM and app-call history, notification parsers/cache, merged call list and callback selection |
| `policy/` | Actor/scopes, ask/allowlist/full-auto decisions, approvals, audit log and action execution |
| `gateway/` | Ktor service, route validation/authentication, pairing, Android backend and DTOs |
| `ai/` | Provider configuration, OpenAI-compatible/Anthropic integrations, encrypted key storage and speech settings |
| `agent/` | Existing SMS reply and text-mode call-screening pipeline |
| `assistant/` | Persistent chat models/DAO, structured intents/orchestrator, task/reminder models/parser and scheduler |
| `memory/` | Memory persistence, instructions/context building and deterministic retrieval |
| `voice/` | Voice session controller, speech engines, turn loop, audio modes and Shizuku integration |
| `ui/assistant/` | Chat ViewModel/UI, local commands and SMS draft review |
| `ui/settings/` | Setup, provider settings, memory review, tasks, gateway and permissions/activity screens |

## Assistant request paths

### In-app chat

`AssistantChatScreen.kt` persists a user message, then checks deterministic commands first: explicit remember, reminder, create task, and complete task. These paths do not require an LLM provider.

Other messages go through `AssistantOrchestrator.decide`, combining provider settings, user instructions, retrieved confirmed memories, and recent history. `AssistantIntentParser` accepts only supported structured intents: answer, clarify, remember, forget, send SMS, create/complete task, and create reminder. Unsupported/malformed output is rejected.

The UI stores responses and local task/reminder changes. Inferred memories require review. Forget responses direct the user to the memory screen. SMS intents become a preview; pressing Send explicitly submits an `Actor.User` request through `ActionExecutor`.

### Remote assistant

`GatewayModule.kt` authorizes `POST /v1/Assistant` with the `read` scope and validates a 1–4000-character message. `AndroidGatewayBackend.askAssistant` returns an answer/error or an action/task/reminder/memory draft; it does not apply those draft mutations. The adapter currently passes an empty history to `decide`; model message delivery needs an end-to-end regression test.

`cli/src/opencell/client.py` exposes `ask_assistant`; `cli.py` exposes `assistant ask`; `mcp_server.py` exposes the `ask_assistant` tool. They call this same endpoint. These are not independent task-management APIs.

### SMS and voice

`AppContainer` invokes the existing `agent.Assistant` for incoming SMS and call screening. `VoiceController` manages voice calls and adds up to eight open task titles to voice prompt context. These paths share repositories, settings, and action execution, but are not consolidated into the structured chat orchestrator.

## Persistence

`data/OpenCellDatabase.kt` declares schema version 6, exported schemas, and automatic migrations for each step from 1 through 6. Schema JSON files live in `app/schemas/com.opencell.data.OpenCellDatabase/`.

| Table | Purpose |
|---|---|
| `app_calls` | Third-party app-call records |
| `audit_log`, `approvals` | Execution history and approval state |
| `paired_devices` | Paired-device authentication/scopes |
| `memory_facts` | Global/per-contact facts with category, source, confidence, confirmation, expiry, usage and retraction fields |
| `assistant_threads`, `assistant_messages` | Persistent assistant conversations |
| `assistant_tasks` | Task titles/details, due times, source and OPEN/DONE/CANCELLED status |
| `assistant_reminders` | Reminder title, trigger, optional task and PENDING/FIRED/CANCELLED status |

Room column names such as `sourceType`, `needsConfirmation`, and `triggerAtMillis` are camelCase. Inspect the exported schema before writing diagnostic SQL. The schema version is available via `PRAGMA user_version`; `room_master_table` stores the schema identity hash, not a `version` column.

## Security and execution boundaries

- `ActionPolicy` trusts explicit `Actor.User` actions. AI/device actions are subject to the kill switch and, for devices, required scopes. Outbound SMS/calls use ask/allowlist/full-auto and daily-limit rules. Existing-call controls have different rules; do not assume every action always asks.
- `ActionExecutor` records outcomes and performs approved actions through Android handlers. A model response is not evidence that a call or SMS executed.
- `PairingManager` uses a short-lived pairing code and hashed device tokens. Keys belong in encrypted phone/local configuration, not this folder or Git.
- `GatewayService` binds to loopback `127.0.0.1:8787`. `adb forward` provides a practical USB route. Plain LAN/Tailscale access described in historical docs needs an explicit secure networking design; do not expose the gateway by silently changing its bind address.
- Learning from SMS/calls is opt-in. Normal memory retrieval excludes unconfirmed, retracted, expired, and sensitive-category facts. A selected cloud provider receives the prompt context sent to it; local storage does not mean all processing is local.
- Real call audio access depends on the device. Shizuku is shell-assisted capture, speaker mode is audible/half-duplex, and no universal true uplink injection is established.

## Tests, fixtures, and publishing

- Kotlin unit tests: `app/src/test/java/com/opencell/`; JUnit plus Ktor test-host/coroutine helpers. These are JVM tests, not hardware or instrumentation tests.
- Python tests: `cli/tests/`; pytest configured in `cli/pyproject.toml`.
- Emulator-only notification fixture: `testapps/fakecaller` uses package `com.whatsapp`. Never install it on a real phone or a device with WhatsApp data.
- Website: `website/index.html`; `.github/workflows/pages.yml` deploys the `website` directory for relevant pushes to `main` or manual dispatch. GitHub Pages must be enabled separately.
- APK signing: release secrets come from ignored `keystore.properties`. Without signing configuration release builds are unsigned. Do not copy a keystore or its properties into the context folder.
- License: the current repository's `LICENSE` and package metadata declare Apache-2.0. GPL suggestions in the design document are historical proposals, not the current license.
