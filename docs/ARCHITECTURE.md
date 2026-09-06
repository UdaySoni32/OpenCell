# OpenCell Architecture Documentation

## Overview
OpenCell is a modern Android application designed for cellular network monitoring, signal measurement, cell tower mapping, and network telemetry analysis. Built with modern Android development practices, the app leverages **Jetpack Compose**, **Navigation 3**, **Room**, **Retrofit**, and native Android **Telephony & Location APIs**.

---

## Architectural Pattern: Unidirectional Data Flow (UDF) & Clean MVVM

The project follows standard Android Clean Architecture with Model-View-ViewModel (MVVM) and Unidirectional Data Flow (UDF):

```
+-------------------------------------------------------------+
|                      UI / Compose Layer                     |
|  - Jetpack Compose Screens & Expressive M3 Components        |
|  - Nav3 NavDisplay & Adaptive ListDetailPaneScaffold        |
+------------------------------^------------------------------+
                               | UI State (StateFlow)
                               | User Events / Intents
+------------------------------v------------------------------+
|                    Presentation / ViewModel                 |
|  - ViewModels (Scoped to Nav3 BackStack / Lifecycle)        |
|  - UI State formatting & Business Flow Orchestration        |
+------------------------------^------------------------------+
                               | Use Cases / Repositories
                               |
+------------------------------v------------------------------+
|                         Domain Layer                        |
|  - Data Models (CellTower, SignalMetric, LocationData)     |
|  - Repository Interfaces & Business Rules                   |
+------------------------------^------------------------------+
                               | Data Mapping
                               |
+------------------------------v------------------------------+
|                          Data Layer                         |
|  - Local Storage: Room Database & DataStore Preferences     |
|  - Telephony & Location: TelephonyManager, FusedLocation    |
|  - Remote API: OpenCelliD / MLS Client (Retrofit + Moshi)   |
+-------------------------------------------------------------+
```

---

## Core Components & Technologies

### 1. Presentation Layer
- **UI Framework**: Jetpack Compose with Material Design 3 (M3 Expressive design token principles).
- **Navigation**: Jetpack Navigation 3 (`androidx.navigation3`) with type-safe route keys and backstack management.
- **Adaptive UI**: `androidx.compose.material3.adaptive` (`ListDetailPaneScaffold`) for phone, tablet, and foldable form-factor adaptation.
- **State Management**: `StateFlow` and `SharedFlow` exposed by ViewModels, collected in Compose via `collectAsStateWithLifecycle()`.

### 2. Domain & Business Logic
- **Telephony Scanner Engine**: Wraps `TelephonyManager`, listening to cell info updates (`CellInfoLte`, `CellInfo5g`, `CellInfoGsm`, `CellInfoWcdma`) and signal strength reports.
- **Location Engine**: Wraps Google Play Services `FusedLocationProviderClient` for precise cell scanning positioning.
- **Cell Mapping & Analytics**: Calculates signal quality metrics (RSRP, RSRQ, SINR, RSSI, CQI) and distance to towers.

### 3. Data Layer
- **Local Persistence**:
  - **Room DB (`room-runtime`, `room-ktx`, `room-compiler` via KSP)**: Caches scanned cell towers, signal logs, and offline tower databases.
  - **DataStore (`datastore-preferences`)**: Stores user settings, preferred network types, scan intervals, and API tokens.
- **Remote Network Layer**:
  - **Retrofit 2.12.0 + Moshi 1.15.2 + OkHttp 4.10.0**: Fetches cell tower location databases and queries online geolocation providers.
- **CameraX (1.5.0)**:
  - Enables AR/field viewing for overlaying cell tower directions onto camera feeds.

---

## Design Principles
1. **Edge-to-Edge by Default**: All screens call `enableEdgeToEdge()` and apply proper `WindowInsets` padding.
2. **Offline First**: Scanned network telemetry is stored in Room first and synchronized when connectivity is available.
3. **Adaptive & Responsive**: Multi-pane support via Compose Material Adaptive for varied screen sizes.
4. **Lifecycle & Battery Conscious**: Hardware scans (GPS, Telephony) are bound to active lifecycles and configurable sampling rates to minimize power drain.
