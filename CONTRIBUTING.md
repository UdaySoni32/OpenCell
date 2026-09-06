# Contributing to OpenCell

Thanks for your interest in contributing! This document covers the basics.

## Getting started

1. Fork the repo and clone your fork.
2. Open in **Android Studio** (JDK 17+, Android SDK with API 37).
3. Build and run: `./gradlew assembleDebug` — APK lands in `app/build/outputs/apk/debug/`.
4. Run the JVM unit tests: `./gradlew testDebugUnitTest`.

## Pull requests

- Keep PRs focused: one feature or fix per PR.
- Run the unit tests before pushing. New ViewModels/engine logic should come with tests.
- Describe **why** the change is needed, not just what it does.

## Code style

- Kotlin official style; 4-space indent; no wildcard imports.
- Compose: state flows down (`StateFlow` in ViewModels), events up.
- No hard-coded colors in screens — use `MaterialTheme.colorScheme` or the semantic tokens in
  `ui/theme/Color.kt` (e.g. `successColor()` for call/confirm green). This keeps dark mode and
  dynamic color consistent.
- Destructive actions (delete, clear all, revoke) should confirm or be undoable via snackbar.

## Device testing

Full behavior (custom in-call UI, no dialer handoff) requires setting OpenCell as the default
phone app — see README "Running / Testing". Emulator verification steps and the remote API
test recipes live in `docs/TESTING.md`.

## Reporting bugs

Include: device model + Android version, whether OpenCell is the default phone app, logcat
output around the failure, and steps to reproduce.

## License

By contributing, you agree your contributions are licensed under the Apache License 2.0
(see [LICENSE](LICENSE)).
