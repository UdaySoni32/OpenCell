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
| 3 | AI (text): SMS agent, call screening, memory | in progress |
| 4 | Voice agent (Shizuku / speaker / root modes) | planned |

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
