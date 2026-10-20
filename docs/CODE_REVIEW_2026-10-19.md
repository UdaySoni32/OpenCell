# Code review and verification — 19 October 2026

## Scope

Reviewed the Android gateway, approvals and policy, assistant, SMS status handling,
reminders, voice-call goal handoff, Python CLI/client/MCP/speech adapters, and build
setup. Baseline: `7e183fd`. Pre-existing tracked changes were file-mode changes,
not source edits; they were left intact. This is a bounded review, not a guarantee
that every device, provider, or communications path works.

## Findings addressed

| Priority | Finding | Correction and evidence |
|---|---|---|
| High | Concurrent approval decisions could execute one SMS/call more than once | Serialize submission and decisions, consume approval before side effects; regression tests reproduced the old race |
| High | Concurrent automatic submissions could bypass the daily limit | Serialize check, execution and audit; concurrency regression tests |
| High | Remote approval bypassed the kill switch | Propagate the actual deciding actor and check current remote policy; local explicit approval semantics retained |
| High | Pending call goals were shared by phone number before approval | Pass the persisted goal at execution and associate a unique token with Telecom call extras; isolation tests |
| High | Revoked event-stream clients continued receiving private events | Reauthenticate before sending each event; tests cover revocation and read-scope removal |
| High | Remote assistant questions were absent from model messages | Normalize current user turn into bounded history exactly once; stateless/UI/repeated-turn tests |
| High | Android 11–12 notification checks queried a nonexistent permission | Central SDK-aware permission helper and setup permission list; API boundary tests |
| Medium | Reminders were not rescheduled after boot | Restore pending Room reminders independently of gateway enablement; overdue reminders schedule immediately |
| Medium | Synchronous SMS send failures reported success | Failed setup/send attempts mark failure and return null; helper tests |
| Medium | Last-part success overwrote multipart SMS failures | Durable per-part aggregation with sticky failure; out-of-order, duplicate and delivery-status tests |
| Medium | Gateway UI advertised inaccessible LAN/Tailscale addresses | Retain loopback binding, show USB forwarding instructions, remove unused interface enumeration |

## Conservative de-bloat

- Removed the unused Phase-1 assistant prompt builder, keeping shared errors and
  the no-provider message. The structured orchestrator is the production path.
- Consolidated notification permission checks instead of duplicating SDK guards.
- Removed misleading network-address discovery and notification text.
- Did not delete supported features, historical design documents, database
  schemas, the CLI, or emulator fixtures merely to reduce line counts.

Added runtime-version files, project-local dependency/cache defaults, a Dockerfile,
and `make setup`, `make test`, and `make build`. Online provisioning is required
once; an offline check does not imply that an uncached machine needs no downloads.

## Checks run

The installed Android Studio runtime and SDK were discovered and reused. No new
Android Studio installation was necessary. Java: 25.0.3; Python: 3.14.7.

```sh
# Export JAVA_HOME and ANDROID_HOME to your installed JDK and SDK first.
# This session reused an already provisioned Gradle cache:
GRADLE_USER_HOME="$HOME/.gradle" make test
GRADLE_USER_HOME="$HOME/.gradle" make build
./gradlew --offline :testapps:fakecaller:assembleDebug
git diff --check
```

- Android JVM tests: **123 passed**, zero failures or skipped tests.
- Python tests: **23 passed** (client, CLI, MCP, and stubbed speech endpoints).
- Debug APK, emulator fixture APK, Python wheel and sdist: **built successfully**.
- Full suite rerun after integration and final gateway UI cleanup: **passed**.
- Android lint: **incomplete**. Online attempt timed out after ten minutes;
  offline retry lacked `intellij-core-32.4.0.jar` and
  `kotlin-compiler-32.4.0.jar`. No lint pass is claimed.
- Docker image: **unverified**. Build could not access the Docker daemon socket;
  no privileged workaround or Docker group membership change was performed.

## Android Studio emulator smoke checks

Used existing AVD `Test`, Android 17/API 37, x86_64, 16 KB pages, emulator-5554.
Installed the newly built APK and granted roles/permissions only on this emulator.

| Check | Observed result |
|---|---|
| Launch and onboarding | App launched; guide and main navigation displayed |
| Default Phone/SMS roles and permissions | Setup reported “OpenCell is ready” |
| Explicit remember command without provider | Confirmation appeared in chat |
| Create task | Task confirmation appeared in chat |
| Force-stop and relaunch | Earlier chat and confirmations remained visible |
| One-minute reminder | Scheduling confirmation; notification 50001 posted on `assistant_reminders` |
| Incoming simulated SMS | Message body appeared in Messages and a notification |
| Incoming simulated SIM call | OpenCell notification shown; Answer changed Telecom state to ACTIVE |
| SIM call history | Ended simulated call appeared in Calls |
| Fake WhatsApp notification | `ReviewFixture` appeared with WHATSAPP source beside SIM history |
| USB gateway | Health returned 200; pairing returned 201 |
| Authenticated gateway reads | Messages, Calls, Active calls and Approvals returned 200 |
| Assistant without provider | Explicit error response, not invented success |
| Remote SMS request | Returned 202 pending approval; notification showed the expected draft |
| Test device revocation | Paired device removed from UI |
| Sampled logcat | No fatal exception, OpenCell ANR or SQLite/Room error in the checked window |

The queued test SMS was denied through its notification; no real phone or real
recipient was used. Pairing credentials stayed in process memory, and the test
device was revoked. Gateway was switched off and USB forwarding removed afterward.
The emulator remains running for inspection. The fake caller fixture is installed
only there; never install it on a physical device with WhatsApp data.

## Remaining limits and follow-up

- No configured-provider chat or real STT/TTS model run; provider calls in unit
  tests are fakes. No universal SIM-audio/Shizuku/voice quality claim.
- Voice goal token propagation through real Telecom calls still needs a focused
  emulator/physical-device check, beyond the execution and token-isolation tests.
- Boot restoration has unit coverage, but actual reboot-and-alarm delivery was
  not exercised. Android alarms remain inexact.
- Multipart persistence logic has unit coverage; real modem reports, carrier
  delivery receipts and process death during callbacks need device checks.
  Messages already in flight before this update lack the new aggregate state.
- A consumed approval interrupted between persistence and execution is not
  retried automatically: at-most-once safety takes precedence over guaranteed
  delivery. The single app executor supplies in-process serialization.
- Event authorization is checked before each send; idle sockets are not
  proactively closed at the instant a device is revoked.
- Direct LAN/Tailscale gateway exposure remains intentionally disabled. Older
  setup guides describe the original remote-access design and need alignment.
- No signed release, deployment, APK-size benchmark or performance claim.
- Clean-machine Docker reproducibility still requires a successful image build
  in an environment with Docker access. Do not equate the cached checks with it.
