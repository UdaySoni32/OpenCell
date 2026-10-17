# OpenCell AI assistant implementation plan

## Goal

Make the OpenCell assistant a trustworthy personal assistant instead of a collection of SMS and voice automations. The assistant must remember user-approved information, retrieve the right context, explain planned actions, and route every external action through the existing approval and audit system.

## Delivery rules

Work proceeds phase by phase. Each phase must have:

1. A usable user-visible slice.
2. Unit tests for the new behavior.
3. An Android build/test attempt.
4. An emulator smoke test when the local Android SDK and `adb` are available.
5. A conventional git commit before the next phase starts.

The current container does not expose `adb` or an Android SDK, and the Gradle 9.6 distribution is not cached completely. Those are setup blockers for emulator validation; they must remain visible rather than being treated as passing checks.

## Phase 1 — Persistent assistant chat

### User result

The user can open an Assistant tab, send a message, receive an AI response, and reopen the conversation later. Provider failures are shown as errors and are never presented as successful actions.

### Work

- Add Room entities and DAOs for assistant threads and messages.
- Add a Room migration from database version 3.
- Add an Assistant tab and Compose chat screen.
- Add a chat ViewModel that uses the configured LLM and stores both sides of the conversation.
- Keep action execution behind `ActionExecutor`; chat does not send SMS or place calls directly yet.

### Checks

- DAO/model tests where practical.
- Prompt and provider failure tests.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- Emulator: open Assistant, send a message with a test provider, kill/reopen the app, verify history persists.

## Phase 2 — Structured memory and retrieval

### User result

The user can say “remember …”, review what was saved, confirm or reject inferred facts, edit facts, and delete them. Future assistant prompts use relevant memories rather than dumping every recent fact.

### Work

- Extend memory records with category, confidence, source, confirmation state, timestamps, and optional expiration.
- Add explicit versus inferred memory flows.
- Add a deterministic `MemoryRetriever` ranking contact scope, confirmation, confidence, relevance, and recency.
- Add duplicate and conflict handling.
- Replace the simple memory list with a reviewable memory screen.
- Include retrieved memory context in chat, SMS, and voice prompts.

### Checks

- Retrieval ranking tests.
- Sensitive-memory filtering tests.
- Duplicate/conflict tests.
- Room migration test if the project gains a migration test harness.
- Emulator: save a preference, reopen memory, ask a related question, edit/delete it, verify it no longer appears in later context.

## Phase 3 — Central assistant orchestrator and structured intents

### User result

Chat, incoming SMS, call screening, gateway requests, and voice flows share the same reasoning and context rules. The model returns validated structured intents instead of untrusted free-form action text.

### Work

- Add `AssistantOrchestrator` as the single assistant entry point.
- Add typed intents: answer, remember, forget, send SMS, place call, task, reminder, search, summarize, and clarification.
- Add strict JSON parsing and validation with safe fallback responses.
- Route SMS and call flows through the orchestrator.
- Show action previews in chat.
- Route sends/calls through `ActionExecutor` and record audit details.

### Checks

- Structured intent parsing and malformed-output tests.
- Fake-LLM orchestration tests.
- Tests proving denied actions never reach Android handlers.
- Emulator: request an SMS draft, see a preview, approve it, verify the audit entry and SMS provider state.

## Phase 4 — Tasks, reminders, summaries, and follow-ups

### User result

The assistant can track commitments and reminders, summarize important conversations, and show controlled follow-up suggestions.

### Work

- Add Room entities for tasks, reminders, contact summaries, and commitments.
- Add local notification scheduling.
- Add “create task”, “complete task”, “list overdue”, and “remind me” intents.
- Add contact summaries with open questions and commitments.
- Add confirmation before creating reminders or commitments.
- Add quiet hours and proactive-suggestion settings; keep suggestions off by default.

### Checks

- Date parsing and timezone tests.
- Task/reminder state transition tests.
- Summary update and privacy tests.
- Emulator: create a reminder, verify its notification, complete it, and confirm it is not shown as overdue.

## Phase 5 — Gateway, MCP, and voice integration

### User result

The assistant is available consistently from the app, CLI/MCP, SMS, and voice calls, while preserving the same memory, policy, audit, and privacy behavior.

### Work

- Add gateway/MCP methods for asking the assistant, searching memory, and managing tasks.
- Reuse structured intents and policy checks for remote actions.
- Feed contact summaries, commitments, and relevant memories into voice calls.
- Store voice summaries and extract reviewable memory candidates.
- Keep call transcript learning opt-in and sensitive facts confirmation-gated.

### Checks

- Python client/MCP tests against a fake gateway.
- Kotlin gateway auth, policy, and event tests.
- Voice context tests using fake STT/TTS/LLM.
- Emulator: use the CLI/MCP path to ask a question and create an approval-backed action.

## Final acceptance pass

The project is complete only when the following are true:

- Assistant history survives process death.
- Explicit memory is stored and editable.
- Inferred memory is reviewable before becoming trusted context.
- Deleted memory is excluded from prompts.
- Relevant contact and user context is retrieved deterministically.
- SMS/call actions require the existing policy and approval flow.
- Tasks and reminders survive restart.
- Gateway, MCP, SMS, and voice use the same orchestrator.
- Unit tests and Android build pass.
- Emulator smoke tests pass, or the remaining hardware/tooling blocker is explicitly recorded.
