# OpenCell Development Roadmap

This document outlines the phased development roadmap for the OpenCell Android application.

---

## Phase Overview

```
+-----------------------------------------------------------------+
| Phase 0: Project Audit, Setup & Documentation                     | [COMPLETED]
+-----------------------------------------------------------------+
                                |
                                v
+-----------------------------------------------------------------+
| Phase 1: Core Data Models, Room DB, Local Storage & Permissions | [NEXT]
+-----------------------------------------------------------------+
                                |
                                v
+-----------------------------------------------------------------+
| Phase 2: Telephony Engine, Cell Scanning & Location Tracking   |
+-----------------------------------------------------------------+
                                |
                                v
+-----------------------------------------------------------------+
| Phase 3: Remote Network API & Cell Tower Geolocation Services   |
+-----------------------------------------------------------------+
                                |
                                v
+-----------------------------------------------------------------+
| Phase 4: Jetpack Compose UI, Navigation 3 & Adaptive Layouts   |
+-----------------------------------------------------------------+
                                |
                                v
+-----------------------------------------------------------------+
| Phase 5: CameraX & Field AR Overlay                             |
+-----------------------------------------------------------------+
                                |
                                v
+-----------------------------------------------------------------+
| Phase 6: Testing, Performance Optimization & Release Readiness  |
+-----------------------------------------------------------------+
```

---

## Detailed Phase Breakdown

### Phase 0: Project Audit, Setup & Baseline Configuration [COMPLETED]
- [x] Repository audit and Gradle dependency inspection.
- [x] Baseline compilation check (`:app:assembleDebug`, `:app:testDebugUnitTest`).
- [x] Device environment query (ADB device status).
- [x] Architecture, Roadmap, Testing, Compatibility, and Phase 0 documentation creation.

### Phase 1: Core Data Models, Room Database & Preferences
- [ ] Define cell tower & signal telemetry data entities (`CellInfoEntity`, `SignalMeasurementEntity`, `CellTowerLocationEntity`).
- [ ] Implement Room Database, DAOs, and Type Converters.
- [ ] Implement DataStore preferences repository for user settings (scan interval, auto-log, unit systems).
- [ ] Setup runtime permission handling framework (Location, Telephony state, Camera, Notifications).

### Phase 2: Telephony Scanning & Location Engine
- [ ] Build `TelephonyScanner` wrapping `TelephonyManager` for LTE, 5G (NR), GSM, and WCDMA monitoring.
- [ ] Capture key parameters: MCC, MNC, LAC/TAC, CID/NCI, RSRP, RSRQ, SINR, CQI, RSSI, ARFCN/EARFCN.
- [ ] Integrate `FusedLocationProviderClient` for GPS location tagging on each scan.
- [ ] Implement background scan service with configurable polling intervals and battery guardrails.

### Phase 3: Remote Network API & Cell Geolocation Integration
- [ ] Implement Retrofit/Moshi API client for cell tower database lookup (OpenCelliD / MLS).
- [ ] Implement offline cell tower caching and lookup strategy.
- [ ] Add network error handling, rate limiting, and request retry mechanisms.

### Phase 4: UI Development (Jetpack Compose & Navigation 3)
- [ ] Build Navigation 3 structure with `@Serializable` route keys.
- [ ] Build Dashboard Screen: Active cell info, primary signal meter, network type badge, current location.
- [ ] Build Cell Towers List & Detail Screen using `ListDetailPaneScaffold` for adaptive layouts.
- [ ] Build Signal Analytics Screen: Real-time signal strength charts/graphs.
- [ ] Build Settings Screen: Configurable scanning parameters, API key management, database clear options.

### Phase 5: CameraX & Field AR View
- [ ] Implement CameraX preview integration.
- [ ] Develop bearing/orientation calculation between user's current GPS and cell tower coordinates.
- [ ] Render AR overlay tags on camera view showing estimated tower direction and distance.

### Phase 6: Quality Assurance, Performance & Release Readiness
- [ ] Unit test coverage for Telephony parsers, Room DAOs, Repositories, and ViewModels.
- [ ] Compose UI instrumentation tests.
- [ ] Battery and memory profiling using Android Profiler and Perfetto.
- [ ] R8 optimization, rule verification, and release APK/AAB build check.
