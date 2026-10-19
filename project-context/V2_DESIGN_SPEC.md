# OpenCell v2: Design and Phased Build Plan

## Context

The old OpenCell repo was wiped (commit `2e328c6`) to start over. OpenCell v2 is an **open-source** Android app that anyone can install, add their own AI keys to, and use. It:

1. Replaces the default Phone and SMS apps (no root required).
2. Shows **one call list** for SIM calls *and* WhatsApp, Discord, Telegram, Signal and other calls, with call-back from the list.
3. Has an **AI assistant** that takes the user's written instructions and **learns about the user over time**. It answers and makes calls and SMS **from the same phone and number, only with the user's permission**.
4. Exposes a **Twilio-style API** and is controllable from **any computer** (Pi, WSL, Linux, macOS, Windows) through one package (`pipx install opencell`: CLI + Python library + MCP server), **from anywhere** via Tailscale.
5. Does processing **in the app**. Each AI stage (STT, LLM, TTS) can run on-device, on the user's own server (for example Ollama on a Pi/PC over Tailscale), or through a cloud API with the user's own keys.

**Hard requirements from the user:** no VoIP number, no extra service to sign up for, no root, works on the go.

## Core problem: call audio without root or VoIP

Android blocks normal apps from SIM-call audio. Without root or VoIP, the app offers **call audio modes**. It detects at runtime which modes the phone supports and picks the best one:

| Mode | Hear the call | AI speaks into the call | Needs | Status |
|---|---|---|---|---|
| **Shizuku mode** | Yes, both sides (`VOICE_CALL` / `VOICE_UPLINK` / `VOICE_DOWNLINK` via shell permissions) | Speakerphone playback. True injection to be researched. | Shizuku app plus Wireless debugging, re-enabled after each reboot | Capture proven by the FOSS ShizuCallRecorder. Works on some phones only. |
| **Speaker mode** | Mic picks up the speakerphone | App plays TTS on the speaker | Nothing extra | Works on most phones, lower quality, audible in the room |
| **Root mode** (optional) | Yes | Yes | Rooted phone | For users who already have root |
| **Text mode** (always available) | No | No | Nothing | AI declines the call, then talks to the caller by SMS and reports back |

SMS AI works fully on every phone, since the default SMS app can read and send. Call *control* (answer, decline, dial, hang up, screen) also works fully on every phone, since the default dialer has it.

**Non-goals for v1:** VoIP numbers or SIP providers (could become an optional plugin later). Any hosted OpenCell cloud service.

## Permission model ("only with the user's permission")

Every AI or API action that calls, answers, or sends SMS goes through one `ActionPolicy`:
- **Ask every time** (default). A notification shows Approve / Edit / Deny with the draft SMS or the call purpose.
- **Auto for allowed contacts or categories.** For example, auto-reply to OTP-free family texts.
- **Full auto**, with daily limits.

Also:
- A global kill switch.
- An audit log of every action: who asked (AI, which paired computer, or the user), what ran, and the result.
- Remote API tokens get scopes (`read`, `sms:send`, `call:place`, `auto-approve`). Without `auto-approve`, the phone asks the user.
- Listening to or recording calls the user answers is a separate opt-in. Default OFF. Optional spoken notice "this call is assisted by AI".

## Instructions and learning
- **About me / instructions:** free text the user writes, for example "I'm a student, decline sales calls, reply in Hinglish to friends."
- **Memory:** facts the AI extracts from SMS threads and call transcripts (each source opt-in) into a local Room table. Facts are global or per contact.
- The user can view, edit and delete every memory. Everything stays on the device unless a cloud AI provider is chosen, in which case prompts go to that provider.
- At runtime, the agent prompt combines instructions, relevant memories and the contact's history.

## Architecture

```
Phone: OpenCell app (Kotlin, Compose)                    Any computer: pip package "opencell"
 ├─ telecom/   default dialer: InCallService, in-call UI   ├─ CLI (opencell call/sms/events/approve)
 ├─ sms/       default SMS: receivers, threads             ├─ Python library (async client)
 ├─ calllog/   unified list: CallLog + other-app           ├─ MCP server (tools for AI agents)
 │             notifications + self-managed calls          └─ opencell brain (optional server assist:
 ├─ callaudio/ modes: Shizuku | Speaker | Root | Text          Whisper STT + Piper TTS; LLM via Ollama)
 ├─ agent/     pipeline: VAD, barge-in, turn-taking;    <── Tailscale / LAN / USB (adb forward) ──>
 │             SMS agent; ActionPolicy; audit log
 ├─ memory/    instructions + learned facts (Room)
 ├─ ai/        per-stage providers STT | LLM | TTS,
 │             each = on-device | server URL | cloud key
 └─ gateway/   embedded Ktor: REST + WebSocket events, scoped tokens via QR pairing
```

### Decisions (my calls unless marked as the user's)
- **User's:**
  - open source, bring your own keys
  - no VoIP number
  - Tailscale for remote access
  - one package on every OS
  - AI per stage on-device, server or cloud
  - permission before any action
  - learning from calls the user answers is optional
- Android: Kotlin, Jetpack Compose, Material 3, Room, DataStore, coroutines and Flow. Manual DI through an `AppContainer` in `Application`. Single `app` module with feature packages. minSdk 30, target latest stable. Shizuku via `dev.rikka.shizuku:api`.
- AI providers:
  - **STT:** Android `SpeechRecognizer` / whisper.cpp (on-device), Whisper server, or Deepgram/OpenAI (cloud).
  - **LLM:** MediaPipe/LiteRT Gemma (on-device), any OpenAI-compatible URL such as Ollama (server), or Claude/OpenAI/Gemini (cloud).
  - **TTS:** Android `TextToSpeech`, Piper server, or a cloud provider.
  - Keys are stored in EncryptedSharedPreferences.
  - **Fallback chain:** cloud or server unreachable, so use on-device.
- Gateway: Ktor (CIO) on port 8787. Twilio-*shaped* JSON (`sid`, `to`, `from`, `status`, `direction`) at `/v1/Calls`, `/v1/Messages`, `/v1/Approvals`, and `/v1/Events` (WebSocket).
- Python package: typer (CLI), httpx and websockets (client), the official `mcp` SDK, and an optional extra `opencell[brain]` (faster-whisper, piper).
- Distribution: GitHub Releases APK plus F-Droid. The Play Store restricts call-log and SMS permissions and accessibility use, so it is not a target.

### Unified call list
- **SIM:** `CallLog.Calls` plus live `InCallService` events.
- **Other apps:**
  - A `NotificationListenerService` with one parser per package (WhatsApp, WhatsApp Business, Telegram, Signal, Discord, Messenger, Instagram, Teams, Meet). It also stores each notification's `contentIntent` while that notification is alive.
  - Self-managed calls come in through `InCallService` with the `android.telecom.INCLUDE_SELF_MANAGED_CALLS` meta-data.
- **Call back:**
  - SIM: `ACTION_CALL tel:`.
  - WhatsApp: a `ContactsContract.Data` row with mimetype `vnd.android.cursor.item/vnd.com.whatsapp.voip.call`.
  - Others: fire the stored `contentIntent`, or else launch the app.

## Phases (each gets its own detailed plan before build)

**Phase 0: Spike on the user's phone** (throwaway)
- Shizuku capture of `VOICE_CALL`, `VOICE_UPLINK` and `VOICE_DOWNLINK` during a real Jio call.
- Speakerphone TTS playback: does the far end hear it, or does echo cancellation kill it?
- Research true uplink injection through shell permissions.
- Capture real WhatsApp, Discord and Telegram call notifications (`adb shell dumpsys notification --noredact`) as parser fixtures.
- Output: a note listing which call audio modes work on this phone.

**Phase 1: Android core**
- Skeleton, role onboarding (`ROLE_DIALER`, `ROLE_SMS`).
- Dialer, in-call UI, contacts.
- SMS: threads, send, delivery status.
- Unified call list with call-back.

**Phase 2: Gateway + `opencell` package**
- Foreground-service Ktor gateway with QR pairing and scoped tokens.
- REST and WebSocket events.
- `ActionPolicy` approvals reachable from the CLI.
- CLI, library and MCP server. Tailscale setup doc.

**Phase 3: AI core (text first)**
- AI provider settings with key entry and fallback.
- Instructions and memory screens.
- SMS agent: draft or auto reply under `ActionPolicy`.
- Text-mode call screening: decline, then an SMS conversation, then a summary to the user.

**Phase 4: Voice agent**
- Call audio mode detection: Shizuku, Speaker, Root.
- Streaming pipeline: VAD, barge-in, STT, LLM, TTS.
- "Let AI handle" during a call, and AI pickup of unanswered calls.
- AI-placed calls (after approval).
- Opt-in learning from transcripts of calls the user answered.
- The `opencell brain` server.

## Files to create first (Phase 1)
- `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`: dialer and SMS intent filters, `InCallService`, `NotificationListenerService`, SMS receivers (`SMS_DELIVER`, `WAP_PUSH_DELIVER`), `RESPOND_VIA_MESSAGE` service
- `app/src/main/java/com/opencell/`: `OpenCellApp.kt` (`AppContainer`), `telecom/`, `sms/`, `calllog/`, `ui/`
- `README.md`, `LICENSE` (GPLv3 suggested, so forks stay open), `.gitignore`
- On approval, also commit this design as `docs/superpowers/specs/2026-10-11-opencell-v2-design.md`.

## Verification
- JVM unit tests:
  - notification parsers, against Phase 0 fixtures
  - call-log merge
  - `ActionPolicy` decisions
  - gateway routes, with Ktor `testApplication`
  - provider fallback
  - memory extraction prompt builder
- Python: pytest for the client, CLI and MCP tools, against a fake gateway.
- On device (user's Jio phone):
  - set as default Phone and SMS
  - SIM call and SMS in and out
  - miss a WhatsApp and a Discord call, check they appear, and call back
  - from WSL over Tailscale on mobile data: `opencell sms send` triggers an approval notification on the phone; after approval, the SMS is delivered
- Phase 3/4:
  - AI SMS reply with Ask mode
  - text-mode call screening
  - voice agent in each available audio mode
  - kill switch stops everything

## Key risks
- Voice AI quality depends on the phone. Shizuku capture is per device, and speaker mode is lo-fi. Text mode guarantees a working baseline everywhere.
- Shizuku must be restarted after each reboot (Wireless debugging). The app guides the user through it.
- Notification parsers break when other apps update. Parsers are isolated per app and tested against fixtures.
- Android kills background work. The gateway runs as a foreground service with a battery-optimization exemption.
- Laws on recording and AI calls vary. India's DPDP Act 2023 applies here. Recording and learning are opt-in with an announcement option, and data stays local.
