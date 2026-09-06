# Project Plan

OpenCell: An open-source Android application functioning as a normal Phone dialer/app, SMS/MMS messaging app, and an optional developer-controlled telephony gateway (REST, WebSocket, MCP, Webhooks) allowing local/remote developer control over cellular capabilities. Focus on Phase-by-Phase development starting from Phase 0 (Project Audit, foundation, Call Engine, Message Engine, Local Developer API, Auth, Events, WebSocket, Webhooks, Remote Access, MCP, CLI, etc.) with clean Jetpack Compose UI, Room, DataStore, Telecom/Telephony APIs.

## Project Brief

# Project Brief: OpenCell

## Features
1. **Phone Dialer & Call Engine**: Complete telephony call management (dialing, receiving, and handling active call states) utilizing the Android Telecom framework with a clean Jetpack Compose interface.
2. **SMS & MMS Messaging**: Threaded messaging engine for sending, receiving, and displaying SMS and MMS conversations with real-time delivery status updates.
3. **Local Telephony Gateway API**: On-device REST and WebSocket server allowing developers to programmatically initiate calls, dispatch SMS messages, and listen to cellular events.
4. **Developer Auth & Event Webhooks**: Configurable authentication key management and outgoing webhook triggers to forward telephony events to remote developer endpoints.

## High-Level Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Navigation & Adaptive Strategy**:
  - **Jetpack Navigation 3** (state-driven navigation)
  - **Compose Material Adaptive** library (adaptive multi-pane and responsive layouts)
- **Asynchronous Processing**: Kotlin Coroutines & Flow
- **Telephony Integration**: Android Telecom Framework (`InCallService`) & Telephony APIs
- **Gateway Engine**: Ktor Embedded Server (for REST, WebSocket, and Webhooks)
- **Persistence**: Room Database (message threads and call logs) & DataStore (gateway settings and developer keys)

## Implementation Steps
**Total Duration:** 10h 52m 41s

### Task_1_ProjectAuditAndDocs: Conduct Phase 0 Project Audit (inspect repo, Gradle, SDK, dependencies, manifest) and create initial project documentation (ARCHITECTURE.md, ROADMAP.md, TESTING.md, ANDROID_COMPATIBILITY.md).
- **Status:** COMPLETED
- **Updates:** Phase 0 Audit completed successfully. Repository and environment audited, baseline debug build and unit tests pass, and initial documentation created in docs/ (ARCHITECTURE.md, ROADMAP.md, TESTING.md, ANDROID_COMPATIBILITY.md, docs/phases/phase-00.md).
- **Acceptance Criteria:**
  - Repository and environment audited
  - Baseline project build pass
  - Initial documentation created
- **Duration:** 10h 52m 41s

### Task_2_AppFoundationAndUI: Set up Phase 1 Application Foundation: Compose app shell, Navigation 3, Material 3 theme, Room DB, DataStore, and basic UI screens (Phone, Messages, Contacts, Recents, Settings, Developer).
- **Status:** COMPLETED
- **Updates:** Phase 1 App Foundation & UI completed. Jetpack Navigation 3 state-driven navigation implemented. Material 3 theme and Adaptive Navigation Shell created (NavigationRail for wide screens / NavigationBar for compact screens). Room Database (AppDatabase v1) and DataStore preferences initialized with repositories. 6 core UI screens implemented (Phone, Messages, Contacts, Recents, Settings, Developer) and verified with 21 passing unit tests and debug build. Phase documentation updated in docs/phases/phase-01.md.
- **Acceptance Criteria:**
  - Compose shell and Navigation 3 working
  - Room DB and DataStore initialized
  - Core UI screens rendering correctly
  - build pass

### Task_3_TelephonyAndMessagingEngines: Implement Telecom Call Engine (InCallService, dialer, call management) and SMS/MMS Messaging Engine (threaded messaging, delivery status updates).
- **Status:** COMPLETED
- **Updates:** Phase 2 & 3 completed. Implemented Telecom Call Engine with OpenCellInCallService, TelecomAdapter, CallEngine state machine (NEW, DIALING, RINGING, ACTIVE, HELD, ENDING, ENDED, MISSED, REJECTED, FAILED), and active call UI controls (answer/reject, hangup, mute, speaker, DTMF). Implemented SMS/MMS Messaging Engine with MessageEngine, SmsAdapter, SmsReceiver, SmsSentReceiver, SmsDeliveredReceiver, and conversation thread persistence in Room DB. Connected Phone & Messages ViewModels to core engines. Verified with 25 passing unit tests and clean debug assemble build. Created docs/phases/phase-02.md and docs/phases/phase-03.md.
- **Acceptance Criteria:**
  - Telecom InCallService integrated for dialing and call handling
  - SMS/MMS send/receive integrated with Room
  - Phone and Messages UI connected to core engines
  - build pass

### Task_4_LocalGatewayAndWebhooks: Implement Local Telephony Gateway API with Ktor embedded server for REST, WebSocket server, API_KEY developer authentication management in DataStore, and outgoing webhooks.
- **Status:** COMPLETED
- **Updates:** Phase 4 Local Gateway API & Webhooks completed successfully. Embedded Ktor HTTP/WebSocket server running via GatewayServerService foreground service on 127.0.0.1:8080. Implemented REST endpoints (/v1/health, /v1/device, /v1/capabilities, /v1/calls, /v1/messages, /v1/contacts, /v1/sim, /v1/network, /v1/webhooks) and WebSocket (/v1/events). Implemented API Key auth (oc_live_ / oc_test_) in DataStore with scope checks. Implemented EventEngine and WebhookEngine with HMAC SHA-256 signatures. Connected Developer UI with server toggle, key manager, webhook manager, and server logs. Verified with 32 passing unit tests and clean debug build. Documented in docs/phases/phase-04.md.
- **Acceptance Criteria:**
  - Ktor server listening on local REST and WebSocket endpoints
  - API_KEY authentication key management implemented
  - Outgoing webhooks triggered on telephony events
  - build pass

### Task_5_RunAndVerify: Run and verify application stability on emulator, ensure alignment with user requirements, and verify no crashes. Instruct critic_agent to audit UI fidelity and application performance.
- **Status:** COMPLETED
- **Updates:** Phase 5 Verification completed successfully. critic_agent deployed app on emulator-5554, verified launch, tested all 6 screens (Phone, Messages, Contacts, Recents, Settings, Developer), verified zero crashes/ANRs/logcat exceptions, and confirmed Material 3 UI design compliance and feature functionality. All unit tests pass.
- **Acceptance Criteria:**
  - build pass
  - app does not crash
  - make sure all existing tests pass
  - critic_agent verifies application stability and reports critical UI issues

### Task_6_CustomInCallUIAndServiceBinding: Implement Default Dialer Role request, full-screen notification for OpenCellInCallService, and custom full-screen Jetpack Compose InCallActivity / InCallScreen with caller details, call states, answer/decline, mute, speakerphone, hold, DTMF dialpad, and hangup controls bound to CallEngine.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Default Dialer role request manager implemented
  - InCall notification with full-screen intent configured for OpenCellInCallService
  - Custom Jetpack Compose InCallActivity and InCallScreen built with caller details, timer, mute, speaker, hold, DTMF, answer/decline, and hangup
  - OpenCellInCallService bound to CallEngine and InCallActivity without OEM dialer handoff
  - build pass
- **StartTime:** 2026-09-05 16:15:39 IST

### Task_7_RunAndVerify_RemoteGatewayAndInCallTesting: Configure ADB port forwarding (adb reverse tcp:8080 tcp:8080) for connected Moto phone (ZD222XRP72), execute remote REST call (POST http://127.0.0.1:8080/v1/calls), verify custom In-Call UI launches directly, and instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, and report critical UI issues.
- **Status:** PENDING
- **Acceptance Criteria:**
  - ADB port reverse configured for port 8080 on Moto phone ZD222XRP72
  - API_KEY authentication integration verified for REST call
  - Remote REST API call POST /v1/calls successfully triggers outgoing call
  - Custom InCallActivity UI renders on device during call execution
  - build pass
  - app does not crash
  - make sure all existing tests pass
  - critic_agent verifies application stability and reports critical UI issues

