# Changelog

All notable changes to OpenCell are documented here.

## [Unreleased] — 2026-08-30

### Fixed

#### answerCall() State Restriction
- **`CallEngine.answerCall()`** now accepts transitions from both `RINGING` (incoming) and `DIALING` (outbound that connected) states. Previously it only accepted `RINGING`, which caused API-initiated calls to fail when `InCallService` reported `STATE_ACTIVE`.

#### Default Calling App Setup (Critical)
- **`RoleManager`** now wraps `createRequestRoleIntent()` in try/catch and logs errors. Added `openDefaultAppsSettings()` fallback that opens `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`.
- **`MainActivity`** role setup screen now shows:
  - Snackbar feedback when role request succeeds or fails
  - "Open Default Apps Settings" button as fallback when the `Set` button doesn't work
  - Helper text explaining the fallback
- **`RoleManager.getDialerRoleStatus()`/`getSmsRoleStatus()`** now use `isRoleAvailable()` to detect OEM restrictions, returning `UNAVAILABLE` instead of always `AVAILABLE`.

#### Calling Flow (Critical)
- **`CallEngine.makeCall()`** now actually places calls through the Android telephony system by launching `ACTION_CALL` (or `ACTION_DIAL` as fallback). Previously it only created a database record and emitted events without ever initiating a real call.
- **`DialerScreen`** call button now invokes `viewModel.dial()` directly. Previously it navigated to a non-existent `call-outgoing/{number}` route, which would crash the app.
- **`Navigation.kt`** removed the broken `call-outgoing/{number}` route. Contacts screen now launches `ACTION_CALL` intent directly via `LocalContext`.

#### Default App Role Setup (Critical)
- **`MainActivity`** role-setup screen now properly refreshes after the user grants (or denies) the default dialer/SMS role. The `showRoleSetup` state is now backed by a `mutableStateOf` that is updated in the `roleRequestLauncher` callback and re-checked on every `onStart()`.
- Previously `checkAndRequestRoles()` was an empty method, so the UI never updated after role grant.

#### SMS Receiving (Critical)
- **`SmsReceiver`** now directly calls `MessagingEngine.onIncomingSms()` via Hilt `PlatformEntryPoint` instead of forwarding via a broadcast (`ACTION_SMS_PROCESSED`) that had no registered receiver. Incoming SMS were being silently dropped.

#### Build Errors
- **`ui/build.gradle.kts`** added missing `kotlin.serialization` plugin and `kotlinx-serialization-json` dependency. This fixed compilation errors in `TestingDashboardViewModel.kt` (`Unresolved reference 'serialization'`, `Unresolved reference 'JsonPrimitive'`).
- **`ApiServer.kt`** removed duplicate `ApiRoutes` class definition (the canonical one lives in `ApiRoutes.kt` with `@Singleton` for Hilt DI). This fixed `kspDebugKotlin` compilation failure.

---

## [0.1.0-mvp] — Previous

### Added
- Initial MVP with embedded Ktor API server
- Call management (make, answer, reject, hangup, hold, resume)
- SMS send/receive with delivery tracking
- Contact management
- Device info, SIM info, network info
- Capability detection engine
- Role manager for default dialer/SMS app
- WebSocket event streaming
- API key authentication with rate limiting
- Testing dashboard with event emitter
