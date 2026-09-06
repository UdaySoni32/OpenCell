# OpenCell Android Compatibility & Permissions Matrix

This document defines the Android SDK targets, compatibility considerations, and runtime permission requirements for OpenCell.

---

## SDK Version Specifications

| Property | Target Value | Description |
| :--- | :--- | :--- |
| **`compileSdk`** | **37** | Android 16 / Baklava release |
| **`targetSdk`** | **37** | Android 16 / Baklava compatibility standards |
| **`minSdk`** | **24** | Android 7.0 Nougat minimum deployment level |
| **Java Compatibility** | **Java 11** | Source & Target compatibility set to JDK 11 |

---

## Runtime Permissions Requirements

OpenCell interacts directly with Android telephony hardware, location services, camera, and notifications. The required permissions are listed below:

| Permission | Purpose | Min API | Exemption / Special handling |
| :--- | :--- | :--- | :--- |
| `android.permission.INTERNET` | Cell tower database lookups & network sync | 24 | Normal permission |
| `android.permission.ACCESS_FINE_LOCATION` | Pinpointing GPS coordinates for cell tower tagging | 24 | Dangerous permission (Runtime check required) |
| `android.permission.ACCESS_COARSE_LOCATION` | Coarse location fallback | 24 | Dangerous permission |
| `android.permission.ACCESS_BACKGROUND_LOCATION` | Continuous background cell logging | 29 | Requires separate user approval flow |
| `android.permission.READ_PHONE_STATE` | Accessing cell tower IDs (MCC, MNC, TAC, CID) | 24 | Dangerous permission (Runtime check required) |
| `android.permission.CAMERA` | CameraX field AR view for tower direction overlay | 24 | Dangerous permission (Runtime check required) |
| `android.permission.POST_NOTIFICATIONS` | Foreground scanning service status notification | 33 | Runtime check required on API 33+ |

---

## Hardware & Telephony API Differences Across Android Versions

### TelephonyManager API Evolution
- **API 24 - 28 (Android 7.0 - 9.0)**:
  - Uses `TelephonyManager.getAllCellInfo()` and `PhoneStateListener`.
  - Signal metrics rely on `CellSignalStrengthLte`, `CellSignalStrengthGsm`, `CellSignalStrengthCdma`.
- **API 29 - 30 (Android 10 - 11)**:
  - Introduction of `CellInfoNr` for 5G Non-Standalone (NSA) and Standalone (SA) measurements.
  - Requires `ACCESS_FINE_LOCATION` to access `getAllCellInfo()`.
- **API 31+ (Android 12+)**:
  - Deprecation of `PhoneStateListener` in favor of `TelephonyCallback` (`TelephonyCallback.CellInfoListener`, `TelephonyCallback.SignalStrengthsListener`).
  - Strict location privacy enforcement for telephony scans.

### Location Services
- Google Play Services `FusedLocationProviderClient` is used for location updates across all supported versions.
- On API 31+, exact location vs approximate location user choice is respected.

### Display & Edge-to-Edge
- `enableEdgeToEdge()` is used in `MainActivity`.
- Insets management via `WindowInsets` padding ensures top status bar and bottom gesture bar legibility across API 24 - 37.
