# OpenCell v2 — Implementation Plan

Spec: `docs/superpowers/specs/2026-10-11-opencell-v2-design.md`

Toolchain on the dev machine: JDK 25 (Temurin, `~/.gradle/jdks/jdk25`), Gradle 9.6, AGP 9.4 (built-in Kotlin),
Kotlin 2.2.21, KSP 2.3.5, compileSdk 36, minSdk 30. Test device: emulator `Medium_Phone` (supports
`adb emu gsm call` / `adb emu sms send`). Python 3.14 + uv for the `opencell` package. Ollama installed locally.

Every phase ends with a **test gate**: unit tests green, `assembleDebug` green, emulator smoke test, commit.

---

## Phase 1 — Android core (dialer, SMS, unified call list)

1. **Skeleton**: Gradle wrapper 9.6, version catalog, `app` module (Compose, Material 3, Room, DataStore),
   `OpenCellApp` + `AppContainer`, `.gitignore`, `README.md`, `LICENSE` (GPLv3).
2. **Onboarding**: request `ROLE_DIALER`, `ROLE_SMS`, runtime permissions (phone, call log, contacts, SMS,
   notifications), notification-listener access. Screen shows status of each.
3. **Telecom**: `OpenCellInCallService` (+ `INCLUDE_SELF_MANAGED_CALLS` meta), `CallManager` (StateFlow of
   active calls), `InCallActivity` (answer / decline / hold / mute / speaker / keypad / hang up), incoming-call
   full-screen notification, dialer screen (`ACTION_DIAL`/`tel:` intent filters), `ACTION_CALL` placing.
4. **Contacts**: read-only `ContactsRepository` over `ContactsContract` (search, lookup by number).
5. **SMS**: `SmsDeliverReceiver`, `MmsWapPushReceiver` (stub), `RespondViaMessageService`, `SmsRepository`
   over `Telephony.Sms` (threads, messages, send with sent/delivered PendingIntents, write to provider),
   threads screen + conversation screen + compose.
6. **Unified call list**:
   - `CallEntry` Room entity (source app, kind incoming/outgoing/missed, peer name/number, time, duration).
   - `SimCallLogSource` over `CallLog.Calls`.
   - `CallNotificationListener` + pure-Kotlin parsers per package (WhatsApp, WA Business, Telegram, Signal,
     Discord, Messenger, Instagram, Teams, Meet) → Room; `contentIntent` cache in memory.
   - `UnifiedCallLog` merges both flows sorted by time; filter chips (All / Missed / per app).
   - `CallbackLauncher`: SIM → `ACTION_CALL`; WhatsApp → contacts data row voip mimetype; others → cached
     `contentIntent` → launch package.
7. **Navigation**: bottom bar Calls / Messages / Contacts / Settings; dial FAB.

**Test gate**: JVM tests for parsers, merge, callback resolution; `assembleDebug`; emulator: set default roles,
`adb emu gsm call` → in-call UI answers, appears in list; `adb emu sms send` → thread appears; send SMS.

## Phase 2 — Gateway + ActionPolicy + `opencell` Python package

1. **ActionPolicy**: modes Ask / AllowList / FullAuto (+ daily limit), kill switch, `PendingApproval` store,
   approval notification (Approve / Deny), `AuditLog` Room table.
2. **Gateway**: foreground `GatewayService` running Ktor CIO on 8787; bearer tokens with scopes
   (`read`, `sms:send`, `call:place`, `call:control`, `auto-approve`) stored hashed; pairing screen with QR
   (`opencell://pair?host=..&port=..&token=..`) and paired-device list with revoke.
   Routes: `GET /v1/Health`, `GET/POST /v1/Messages`, `GET/POST /v1/Calls`, `POST /v1/Calls/{sid}` (answer,
   hangup), `GET /v1/Approvals`, `POST /v1/Approvals/{id}` (approve/deny, needs `auto-approve` scope),
   `WS /v1/Events`. Twilio-shaped JSON.
3. **Python package `opencell/`** (uv, src layout): `OpenCellClient` (httpx + websockets), CLI (typer:
   `pair`, `status`, `sms send|list`, `call place|list|answer|hangup`, `events`, `approvals`), MCP server
   (`opencell mcp`) exposing the same tools; config in `~/.config/opencell/config.toml`.

**Test gate**: Ktor `testApplication` route + auth tests, ActionPolicy unit tests, pytest against fake gateway,
end-to-end: `adb forward tcp:8787 tcp:8787` → `opencell sms send` → approval notification on emulator →
approve → SMS sent; `opencell events` shows incoming `adb emu sms send`.

**Phase 2 as built:** pairing uses a 6-digit one-time code shown on the phone (a QR is useless to a CLI);
CLI config is `~/.config/opencell/config.json`; MCP uses the mcp 2.x `MCPServer` API.

## Phase 3 — AI core (text first)

1. **AI providers**: `LlmProvider` interface; `OpenAiCompatibleLlm` (covers Ollama, OpenAI, Groq, OpenRouter,
   Gemini OpenAI endpoint), `AnthropicLlm`; settings screen (provider, base URL, model, key in
   EncryptedSharedPreferences); fallback chain.
2. **Memory**: `Instructions` (DataStore text), `MemoryFact` Room table (global / per contact), memory screen
   (view / edit / delete), `PromptBuilder` (instructions + facts + recent thread).
3. **SMS agent**: on incoming SMS (if enabled for that contact / globally) → draft reply → ActionPolicy →
   send; optional fact extraction from threads (opt-in).
4. **Text-mode call screening**: setting; on incoming call from unknown/screened number → decline → SMS
   "assistant" conversation via SMS agent → summary notification.

**Test gate**: unit tests with fake LLM (prompt building, fact parsing, policy routing); emulator e2e with
local Ollama at `10.0.2.2:11434`: `adb emu sms send` → AI draft approval → reply sent.

## Phase 4 — Voice agent

1. **Call audio modes**: `CallAudioMode` detection (Root via `su` check, Shizuku via `dev.rikka.shizuku:api`
   binder + permission, Speaker always, Text fallback); settings show detected modes.
2. **Voice pipeline**: `SttEngine` (Android `SpeechRecognizer`, server Whisper), `TtsEngine` (Android
   `TextToSpeech`, server Piper), `VoiceAgent` turn loop with barge-in; speaker mode wiring
   (answer → speakerphone → listen/speak).
3. **Controls**: "Let AI handle" button in-call; AI pickup of unanswered calls (setting, delay); AI-placed calls
   via ActionPolicy with goal text; transcript saved + opt-in fact extraction.
4. **`opencell brain`** (Python extra): FastAPI `/stt` (faster-whisper) and `/tts` (piper) endpoints the app
   can use as the "server" provider.

**Test gate**: unit tests for turn loop with fake STT/TTS; mode detection tests; emulator: speaker-mode loop
smoke test; pytest for brain endpoints with stubbed engines.

## Pending on user hardware (cannot run on emulator)
- Phase 0 spike on the real Jio phone: Shizuku call-audio capture, speakerphone injection, real notification
  fixtures for WhatsApp / Discord / Telegram.
