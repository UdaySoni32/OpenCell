# OpenCell UI/UX Audit & Design Decisions

_Created while fixing the first-pass issues; kept as the project's design log._

## Audit (issues found during device + emulator testing)

### Fixed

1. **Inconsistent brand color** — Theme.kt overrode only primary/secondary/tertiary from the
   template, so surfaces, errors, and containers still had the Compose-template purple while
   screens mixed in a hard-coded `0xFF2E7D32` "expressive green" in 5+ places.
   → Full Material 3 light/dark schemes in `ui/theme/Color.kt`, teal brand palette, single
   `successColor()` token for call/answer/confirm green; all hard-coded greens replaced.
2. **`/v1/contacts` always returned `[]`** — the gateway built a throwaway `ContactsViewModel()`
   whose StateFlow (via `stateIn(WhileSubscribed)`) never loads outside composition.
   → Shared `ContactStore` singleton; gateway and UI read/write the same list.
3. **Contacts lost on process death** — the list was in a ViewModel's in-memory StateFlow.
   → `ContactStore` survives across screens; next step is Room persistence.
4. **Phone screen did not fit smaller displays** — fixed `Arrangement.SpaceBetween` + fixed-size
   dialpad could clip the call button below the fold.
   → Dialpad gets `weight(1f)`; banners, number display, dialpad, and call button all fit.
5. **Dialpad only deleted one digit at a time** — no way to clear a wrong number quickly.
   → Backspace now supports **long-press to clear**.
6. **Duplicated in-call controls** — PhoneScreen's overlay duplicated mute/speaker controls that
   belong to the real custom call UI (`InCallActivity`), risking conflicting state.
   → Overlay is now a lightweight status+hangup fallback; full controls live in one place.
7. **Settings About card lied** — "Version 1.0.0 / API 37" were hard-coded strings.
   → Uses `BuildConfig.VERSION_NAME` and live `Build.VERSION` values.

### Known issues (open, tracked)

- Contacts are session-only (no Room persistence yet).
- The "mock telephony" toggle in Dev prefs is not read by `CallEngine` (no live-call simulation
  path on real devices; the engine's own modem-unavailable fallback covers emulators).
- `call.created` gateway events can fire twice (route emits once, engine emits again).
- Dev-screen log list is capped at 30 visible entries; older entries are dropped from view.
- UI strings are not localized (all in-code, English only).

## Design principles for contributors

- **One green.** Any call/answer/success affordance uses `successColor()` from the theme.
- **Theme-driven everything.** No literal `Color(0xFF...)` in screens.
- **One place per control.** Call controls belong to `InCallScreen`; the phone-tab overlay is
  a fallback only.
- **Fit first.** Every screen must survive a 640dp-tall display: prefer `weight()` +
  scrollable content over fixed heights.
- **Discoverability beats cleverness.** Long-press actions must have a visible hint in their
  `contentDescription` (see backspace: "Delete Digit (long-press to clear)").
