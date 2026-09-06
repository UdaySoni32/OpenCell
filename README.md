# OpenCell 📞

**Open-source Android dialer + SMS app with a built-in developer gateway.**

OpenCell replaces your phone app with a custom, Material 3 calling experience — and exposes your
device's telephony as a local REST + WebSocket API so you can script calls, messages and events
from your computer. Think of it as a dialer with an API.

> ⚠️ **Alpha software.** Tested on Android 14–16 devices and emulators. Call handling requires
> OpenCell to be set as the default phone app.

## Features

- **Recents-first home** — fresh launches open into call history; the dialer is a Dial FAB
  with a bottom-sheet dialpad, not a separate tab (5-item nav bar, no clutter)
- **One-tap redial** — the call button on any history entry dials immediately
- **Custom in-call UI** — mute, speaker, hold, DTMF keypad; no handoff to the OEM/Google dialer
- **No handoff dialing** — qualifies for the Android `DIALER` role and binds its own
  `InCallService` (`android.telecom.IN_CALL_SERVICE_UI`)
- **Messages, Contacts, Settings, Dev tools** — full Material 3, light/dark/dynamic color
- **Local REST gateway** — Ktor server on `127.0.0.1:8080`, API-key auth + scopes
- **WebSocket events** — live call/message events on `/v1/events`
- **Dev screen** — key management, webhooks, live request logs, mock telephony toggle
- **Adaptive layout** — bottom bar on phones, navigation rail on tablets/foldables

## Architecture

```
app/src/main/java/com/example/opencell/
├── MainActivity.kt          # Single activity + tel:/DIAL intent handling
├── OpenCellApplication.kt   # App singleton wiring
├── telecom/                 # CallEngine, TelecomAdapter, OpenCellInCallService,
│                            # DefaultDialerManager
├── messaging/               # MessageEngine (SMS)
├── gateway/                 # Embedded Ktor gateway (REST + WebSocket)
│   ├── server/GatewayServer.kt
│   ├── event/EventEngine.kt # Event bus feeding /v1/events + webhooks
│   ├── webhook/             # Webhook dispatcher
│   └── logging/             # Request/server log repository
├── data/                    # Room DB, DataStore preferences, repositories
└── ui/                      # Compose screens, theme, adaptive navigation shell
```

- **UI**: Jetpack Compose + Material 3 + Navigation 3, MVVM ViewModels
- **Data**: Room (calls/messages), DataStore (settings, API keys, webhooks)
- **Telecom**: `CallEngine` wraps `TelecomAdapter` → platform `ConnectionService`/
  `InCallService`
- **Gateway**: embedded Ktor CIO server, never exposed to the network — reach it via
  `adb forward` (see below)

## Building

```bash
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # JVM unit tests
```

Requirements: JDK 17+, Android SDK with API 37. Open in Android Studio for the best experience.

## Running / Testing

1. Install the debug APK and grant phone/SMS/contacts permissions (in-app prompts).
2. Open **Settings → Default Phone App Role → Set Default** — required for the custom
   in-call UI; otherwise calls hand off to the system dialer.
3. Open the **Dev** tab and enable the gateway server.
4. From your computer:

```bash
adb forward tcp:28080 tcp:8080      # host port → device gateway
export K="X-API-Key: oc_live_opencell_default_key_999"

curl -H "$K" http://127.0.0.1:28080/v1/health
curl -H "$K" -X POST -H "Content-Type: application/json" \
     -d '{"phoneNumber":"15551234567","contactName":"Demo"}' \
     http://127.0.0.1:28080/v1/calls
```

Key endpoints: `GET /v1/health`, `/v1/device`, `/v1/capabilities`, `/v1/sim`, `/v1/network`,
`/v1/calls`, `POST /v1/calls`, `POST /v1/calls/{id}/hangup|answer|reject`, `GET/POST /v1/messages`,
`GET /v1/contacts`, `GET/POST/DELETE /v1/webhooks`, WS `GET /v1/events`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). UI/UX guidelines and a design audit live in
[docs/UI_UX_AUDIT.md](docs/UI_UX_AUDIT.md).

## License

[Apache License 2.0](LICENSE)

## Security note

The gateway binds to loopback only. Nothing is exposed over Wi-Fi/LAN, and API keys live in the
app's private storage. Do not add remote binding without adding transport security first.
