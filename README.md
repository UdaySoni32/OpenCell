# OpenCell

An open-source replacement for Android's Phone and SMS apps, with:

- **One call list** for SIM calls *and* WhatsApp, Discord, Telegram, Signal, Messenger, Teams… calls (read from their call notifications), with call-back from the list.
- **A Twilio-style local API + CLI + MCP server** so your own projects (AI agents, scripts) can make and take calls and SMS through your phone — from any computer, anywhere (via Tailscale).
- **A bring-your-own-keys AI assistant** that learns from your instructions and answers/makes calls and SMS — only with your permission.

No root. No VoIP number. No OpenCell cloud.

Design: [`docs/superpowers/specs/2026-10-11-opencell-v2-design.md`](docs/superpowers/specs/2026-10-11-opencell-v2-design.md) ·
Build plan: [`docs/superpowers/plans/2026-10-11-opencell-v2-implementation.md`](docs/superpowers/plans/2026-10-11-opencell-v2-implementation.md)

## Status

| Phase | What | State |
|---|---|---|
| 1 | Dialer, in-call UI, SMS, contacts, unified call list | ✅ done |
| 2 | Gateway API, permissions, `opencell` CLI/MCP ([cli/](cli/README.md)) | ✅ done |
| 3 | AI (text): SMS agent, call screening, memory | ✅ done |
| 4 | Voice agent (Shizuku / speaker modes, root via Sui) | ✅ done (needs real-device tuning) |

### Voice calls

Android does not let apps touch SIM-call audio, so the AI uses one of these, picked automatically:

| Mode | Setup | How it works |
|---|---|---|
| **Shizuku** (best) | Install [Shizuku](https://shizuku.rikka.app), start it via Wireless debugging, tap *Allow Shizuku* in OpenCell | Hears the caller directly (`VOICE_DOWNLINK`), speaks through the speakerphone |
| **Speaker** | Allow the microphone | Speakerphone + mic, half-duplex (lower quality, audible in the room) |
| **Text** | nothing | Declines and continues by SMS (Phase 3 call screening) |

Root users: install Sui (Magisk) — it speaks the Shizuku API. Use it from the in-call screen (**Let AI handle**),
let it answer calls you miss, or have your code place AI calls:

```bash
opencell call place +919876543210 --goal "Ask if the clinic has a slot on Saturday morning"
```

Speech-to-text / text-to-speech run on the phone, on your own server (`opencell brain`: Whisper + Piper, see
[cli/README.md](cli/README.md)) or a cloud API (OpenAI-compatible).

**Check a phone quickly:** during a call, run
`adb shell CLASSPATH=$(adb shell pm path com.opencell | cut -d: -f2) app_process /system/bin com.opencell.voice.shizuku.CallAudioProbe 3`
— it prints whether shell-level call capture works on that device.

Recording and AI-handled calls are regulated differently around the world (in India, the DPDP Act 2023 applies);
"Learn from calls I answer" is off by default.

### AI assistant (Settings → AI assistant)

Bring your own model: your own server (Ollama / any OpenAI-compatible URL, e.g. over Tailscale), OpenAI, Anthropic,
Gemini or Groq, plus an optional fallback. Keys are encrypted with the Android Keystore. The assistant can reply to
SMS, screen unknown callers by SMS, and learn facts from conversations (you can view and delete every fact). It never
answers banks, short codes or one-time codes, and everything it sends goes through **Permissions & activity**
(default: you approve each message).

## Build

Requirements: JDK 17+ (built with 25), Android SDK 36.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the app → **Settings** → allow each item (default Phone app, default SMS app, permissions, notification access).

### Emulator testing

```bash
adb emu gsm call 5551112222          # incoming SIM call
adb emu sms send 5559998888 "hello"  # incoming SMS
```

`testapps/fakecaller` is an emulator-only fixture that pretends to be WhatsApp (`com.whatsapp`) and posts call
notifications, so the unified call list can be tested without real accounts. **Never install it on a real phone.**

```bash
./gradlew :testapps:fakecaller:assembleDebug
adb install -r testapps/fakecaller/build/outputs/apk/debug/fakecaller-debug.apk
adb shell am start -n com.whatsapp/com.opencell.fakecaller.PostActivity --es type missed --es name Riya
```

## Distribution

GitHub Releases and F-Droid. Google Play restricts the call-log/SMS permissions a default dialer needs.

## License

Apache-2.0
