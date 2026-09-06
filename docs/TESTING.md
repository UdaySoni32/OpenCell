# OpenCell Testing Strategy & Guidelines

This document outlines the testing strategy, test architecture, and execution procedures for OpenCell.

---

## Testing Pyramid

```
       / \
      /   \     Instrumented / UI Tests (Compose Test, Navigation)
     /  15% \
    /---------\
   /   25%     \   Integration Tests (Room DB, Network Mocks, Service Fakes)
  /-------------\
 /      60%      \   Unit Tests (ViewModels, Parsers, Repositories, Domain Models)
/-----------------\
```

---

## 1. Unit Testing
- **Framework**: JUnit4, Kotlinx Coroutines Test (`1.10.2`).
- **Scope**:
  - **Telephony Parsers**: Conversion of raw Android `CellInfo` objects into domain entities (`CellInfoLte`, `CellInfo5g`).
  - **Signal Converters**: Calculation of signal quality levels (RSRP to signal bar rating, dBm mappings).
  - **ViewModels**: StateFlow emission testing using `StandardTestDispatcher` / `UnconfinedTestDispatcher`.
  - **Repositories**: Business logic verification with fake/mock sources.
- **Execution Command**:
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

---

## 2. Integration Testing
- **Scope**:
  - **Room Database**: Verification of DAO queries, migrations, and entity constraints using in-memory Room database instances (`Room.inMemoryDatabaseBuilder`).
  - **DataStore**: Preference read/write operations.
  - **Network Layer**: Retrofit service interfaces with `MockWebServer` / static JSON responses.
- **Mocking Telephony & Location**:
  - Fakes are provided for `TelephonyManager` and `FusedLocationProviderClient` to produce reproducible simulated cell measurements without physical hardware.

---

## 3. UI & Instrumentation Testing
- **Framework**: Compose UI Test (`androidx.compose.ui:ui-test-junit4`), Espresso core, AndroidJUnitRunner.
- **Scope**:
  - Screen rendering and state transitions in Jetpack Compose.
  - Navigation 3 flow verification (`NavDisplay`).
  - Edge-to-edge layout inset assertions.
- **Execution Command**:
  ```bash
  ./gradlew :app:connectedDebugAndroidTest
  ```

---

## Test Verification Standards
1. All pull requests or major task implementations must run unit tests and pass cleanly.
2. Coroutine tests must use explicit test dispatchers (`runTest`) to prevent asynchronous flakiness.
3. Database tests must ensure complete cleanup after each test execution.
