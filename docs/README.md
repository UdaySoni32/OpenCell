# OpenCell

> Phone & Messaging Gateway — An Android app that exposes telephony capabilities (calls, SMS, contacts, SIM info, network state) via a local REST API, enabling remote control and automation.

---

## Architecture Overview

OpenCell is a multi-module Android project built with **Kotlin**, **Jetpack Compose**, and **Hilt DI**.

### Module Structure

```
OpenCell/
├── app/          # Application entry point, MainActivity, role/permission setup
├── core/         # Database (Room), crypto utils, data models, boot receiver
├── platform/     # Android platform integration (telephony, SMS, contacts, events, roles)
├── server/       # Embedded Ktor HTTP/WebSocket server (REST API)
└── ui/           # Jetpack Compose UI screens (dialer, messages, contacts, settings)
```

| Module | Responsibility |
|--------|---------------|
| `app` | Application class, MainActivity, RoleRequestActivity, notification channels |
| `core` | Room database DAOs/entities, `CryptoUtils`, data model classes, `BootReceiver` |
| `platform` | `CallEngine`, `MessagingEngine`, `DeviceEngine`, `EventEngine`, `ContactEngine`, `CapabilityEngine`, `RoleManager`, `SmsReceiver`, `SmsDeliveryReceiver`, `ConnectionService`, `InCallService` |
| `server` | Ktor `ApiServer`, route definitions, `AuthenticationService`, API key management |
| `ui` | Compose screens, ViewModels, navigation graph |

### Key Flows

#### Outgoing Call Flow
1. User dials number on `DialerScreen` → `DialerViewModel.dial()`
2. `CallEngine.makeCall()` creates a `Call` record in Room DB
3. `CallEngine` launches `ACTION_CALL` intent → Android Telecom framework
4. If OpenCell is the default dialer, `OpenCellConnectionService.onCreateOutgoingConnection()` creates a `Connection`
5. `OpenCellInCallService` is bound by the framework for in-call UI
6. State transitions are tracked via `CallEngine.updateCallState()` → Room + events

#### Incoming Call Flow
1. Telecom framework calls `OpenCellConnectionService.onCreateIncomingConnection()`
2. `ConnectionService` creates a `Connection` and calls `CallEngine.onIncomingCall()`
3. `InCallService.onCallAdded()` picks up the `Call` object and registers a state callback
4. State changes propagate through `CallEngine` → Room DB → events → UI

#### Outgoing SMS Flow
1. User composes message on `ComposeScreen` → `ComposeViewModel.send()`
2. `MessagingEngine.sendSms()` creates a `Message` record, sends via `SmsManager`
3. `SmsDeliveryReceiver` handles sent/delivered status callbacks
4. State transitions tracked: CREATED → QUEUED → SENDING → SENT → DELIVERED

#### Incoming SMS Flow
1. `SmsReceiver` (manifest-registered, priority 999) receives `SMS_RECEIVED_ACTION`
2. Extracts sender, body, timestamp from `SmsMessage` objects
3. Calls `MessagingEngine.onIncomingSms()` via Hilt `PlatformEntryPoint`
4. Message stored in Room DB, events emitted, audit log entry created

#### Role/Default App Flow
1. `MainActivity.onCreate()` checks `RoleManager.isFullyDefault()`
2. If not default, shows `RoleSetupScreen` with buttons for Phone and SMS roles
3. Tapping "Set" calls `roleManager.createRequestDialerRoleIntent()` / `createRequestSmsRoleIntent()`
4. Launched via `ActivityResultContracts.StartActivityForResult()`
5. On result, `showRoleSetup` state is updated → UI recomposes
6. `onStart()` re-checks on every resume (user may change defaults in system settings)

---

## Dependencies

### Core Libraries
- **Room** — Local database for calls, messages, events, devices, API keys
- **Hilt** — Dependency injection across all modules
- **Kotlin Serialization** — JSON serialization for models and API
- **Kotlin Coroutines / Flow** — Async operations and reactive state

### Server
- **Ktor** (Netty engine) — Embedded HTTP/WebSocket server
- **Ktor Content Negotiation** — JSON serialization for API responses

### UI
- **Jetpack Compose** (Material 3) — Modern declarative UI
- **Navigation Compose** — Screen navigation
- **Hilt Navigation Compose** — ViewModel injection

### Android Platform
- **Telecom Framework** (`ConnectionService`, `InCallService`) — Call management
- **SmsManager** — SMS send/receive
- **RoleManager** (API 29+) — Default dialer/SMS app roles
- **SubscriptionManager** — Multi-SIM support

---

## API Server

The app runs an embedded Ktor server on port **8900** (all interfaces) as a foreground service.

### Authentication
- API key-based authentication via `Authorization: Bearer <key>` header
- Keys are hashed (SHA-256) before storage; raw key shown only at creation
- Rate limiting: configurable per key (default 60 req/min)

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Server health check |
| GET | `/v1/devices` | List devices |
| GET | `/v1/devices/{id}` | Get device details |
| POST | `/v1/calls` | Initiate outbound call |
| GET | `/v1/calls` | List active/recent calls |
| POST | `/v1/calls/{id}/answer` | Answer incoming call |
| POST | `/v1/calls/{id}/reject` | Reject incoming call |
| POST | `/v1/calls/{id}/hangup` | Hang up call |
| POST | `/v1/calls/{id}/hold` | Hold call |
| POST | `/v1/calls/{id}/resume` | Resume held call |
| GET | `/v1/messages` | List messages |
| POST | `/v1/messages` | Send SMS |
| GET | `/v1/messages/{id}` | Get message details |
| GET | `/v1/conversations` | List conversations |
| GET | `/v1/conversations/{id}/messages` | Get thread messages |
| GET | `/v1/contacts` | List/search contacts |
| GET | `/v1/contacts/lookup` | Lookup contact by phone number |
| GET | `/v1/devices/{id}/network` | Network info |
| GET | `/v1/devices/{id}/sim` | SIM info |
| GET | `/v1/devices/{id}/capabilities` | Device capabilities |
| WS | `/v1/events/stream` | Real-time event WebSocket |

### Error Response Format
```json
{
  "error": {
    "code": "AUTHENTICATION_ERROR",
    "message": "Missing or invalid Authorization header.",
    "request_id": "req_..."
  }
}
```

---

## Permissions

### Telephony
- `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `READ_CALL_LOG`, `WRITE_CALL_LOG`
- `CALL_PHONE`, `ANSWER_PHONE_CALLS`, `PROCESS_OUTGOING_CALLS`
- `MODIFY_PHONE_STATE`, `READ_PRECISE_PHONE_STATE`, `MANAGE_OWN_CALLS`

### SMS/MMS
- `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`
- `BROADCAST_SMS`, `SEND_RESPOND_VIA_MESSAGE`

### Contacts
- `READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS`

### Other
- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`
- `POST_NOTIFICATIONS`, `WAKE_LOCK`
- `RECORD_AUDIO`

---

## Known Issues & Limitations

### Call ID Mismatch Between CallEngine and ConnectionService
When a call is initiated via `CallEngine.makeCall()`, a call record is created with one ID. When the Telecom framework routes through `ConnectionService`, it generates a separate ID. These are not linked. This means:
- The Connection's lifecycle callbacks in `ConnectionService` reference a different callId
- API-initiated calls and UI-initiated calls may have disjoint tracking

**Fix needed:** `ConnectionService` should look up the existing `Call` record (by phone number + recency) instead of generating a new ID.

### InCallService State Sync
`InCallService.handleStateChange()` calls `answerCall()` on `STATE_ACTIVE`, but `answerCall()` requires the call to be in `RINGING` state. If the state transition happens too fast, this check may fail.

### MMS Support
MMS send/receive is marked as `EXPERIMENTAL`. The `SmsReceiver` handles `WAP_PUSH_RECEIVED_ACTION` but does not parse MMS content.

### Default App Persistence
If the user revokes the default dialer/SMS role in system settings, the app will show the role setup screen again on next `onStart()`. This is by design but may be unexpected.

---

## Development

### Building
```bash
./gradlew assembleDebug
```

### Running Tests
```bash
./gradlew test
```

### Key Commands
```bash
# Build specific module
./gradlew :server:compileDebugKotlin

# Check for compilation errors
./gradlew :ui:compileDebugKotlin

# Run all tests
./gradlew test
```

---

## Recent Fixes

### 2026-08-30 — Calling & SMS Fixes

| File | Fix |
|------|-----|
| `CallEngine.kt` | `makeCall()` now launches `ACTION_CALL` / `ACTION_DIAL` intent to actually place calls through the telephony system |
| `DialerScreen.kt` | Call button now calls `viewModel.dial()` directly instead of navigating to non-existent route |
| `Navigation.kt` | Removed broken `call-outgoing` route; ContactsScreen now launches `ACTION_CALL` intent directly |
| `MainActivity.kt` | Role setup UI now properly refreshes after granting default dialer/SMS role via `mutableStateOf` |
| `SmsReceiver.kt` | Now directly calls `MessagingEngine.onIncomingSms()` via Hilt EntryPoint instead of sending a broadcast nobody listens for |
| `TestingDashboardViewModel.kt` | Added missing `kotlinx-serialization-json` dependency to `ui/build.gradle.kts` |
| `ApiServer.kt` | Removed duplicate `ApiRoutes` class definition (was also in `ApiRoutes.kt`) |
| `ui/build.gradle.kts` | Added `kotlin.serialization` plugin and `kotlinx-serialization-json` dependency |
