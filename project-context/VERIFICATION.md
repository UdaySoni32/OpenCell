# Verification and continuation guide

## Scope

Prepared on 2026-10-19 for the `docs/project-context` documentation change, with application code at `84300e4`. Fresh checks below are separated from earlier emulator evidence. All commands run from the repository root unless a different working directory is specified.

## Android setup, tests, and build

Use a compatible JDK (the successful documentation-session run used OpenJDK 17), Android SDK platform/build tools required by the Gradle project, and the checked-in Gradle wrapper. Set `ANDROID_HOME` and `ANDROID_SDK_ROOT` to your own SDK installation; put its `platform-tools` on `PATH` for ADB. No machine-specific SDK path belongs in committed files.

After dependencies have been downloaded once, the exact offline check used for this change was:

```bash
./gradlew --offline :app:testDebugUnitTest --rerun :app:assembleDebug
```

- **Unit tests: passed**, 91 tests in 16 suites, no failures/errors/skips. `--rerun` forces the unit-test task to execute even when Gradle considers its outputs current.
- **Debug build: passed**, `BUILD SUCCESSFUL`; the APK assembly outputs were already up to date.
- APK output: `app/build/outputs/apk/debug/app-debug.apk`.
- Unit-test report: `app/build/reports/tests/testDebugUnitTest/index.html` (generated, not committed).
- **Standalone lint: not run** for this documentation-only task.
- **Standalone typecheck: not run**; the Android build includes compilation, mostly up to date in this session.
- **Release build/signing: not run**; no release artifact or deployment is claimed.

For a first online build, omit `--offline`. A clean machine still needs the SDK, JDK, Gradle distribution, and dependencies provisioned. A cached offline build is not evidence of a fully self-contained clean rebuild.

## Python checks

Run in `cli/`:

```bash
uv run --frozen --offline --group dev pytest
```

**Blocked before test collection** in this task: `httpx==0.28.1` was not in the UV cache. Earlier offline work also encountered uncached `starlette==1.7.0`. No Python tests are claimed passed or skipped by pytest because it did not start collecting tests.

On a machine allowed to download dependencies, run `uv sync --frozen --group dev` in `cli/`, then repeat the offline test command. Keep `cli/uv.lock` unchanged unless a dependency update is intended.

Syntax-only fallback from the repository root:

```bash
python3 -m compileall -q cli/src cli/tests
```

**Passed** in this documentation session. This checks Python parsing only, not HTTP, CLI, MCP, or speech behavior. It is not a replacement for pytest.

## Earlier emulator smoke evidence

The earlier smoke run used the API 36 x86_64 AVD `OpenCellApi36` (`emulator-5554`) at application revision `84300e4`. These checks were **not rerun** for the documentation change.

| Action | Observed result |
|---|---|
| Install debug APK and launch `com.opencell/.ui.MainActivity` | App installed; main activity resumed |
| Open Assistant | Existing chat visible |
| Send `Remember` without a provider | Expected no-provider response; no memory saved |
| Send `Remember that I prefer short replies` | User/assistant messages persisted; confirmed PREFERENCE memory saved |
| Send `task:buy` | Task `buy` persisted with OPEN status |
| Send `remind me stretch in 1 minute` | Reminder persisted PENDING, later FIRED by the receiver |
| Inspect Room database | `PRAGMA user_version` was 6; expected assistant tables present |
| Check sampled logcat | No fatal exception, OpenCell ANR, SQLiteException, or Room error observed |

Notification permission was absent, so the reminder did not show a visible notification. The FIRED row proves receiver execution, not visible delivery. No configured-provider conversation, memory edit/delete, SMS draft execution, task completion, forced process-restart persistence, or remote assistant end-to-end success was established by this smoke run.

ADB space-escaped text input was unreliable in the software emulator. Entering words separately with `adb shell input text <word>` and `adb shell input keyevent 62` between them worked. Inspect the draft before pressing Send; an incomplete command is not a valid feature failure.

## Physical-device continuation

Have the owner connect the phone, enable USB debugging, and authorize the computer. Do not invent a device serial or install the fake WhatsApp fixture on the phone.

```bash
adb devices -l
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <device-serial> shell am start -n com.opencell/.ui.MainActivity
```

Then:

1. Complete default Phone/SMS roles and requested runtime permissions in the UI. Grant notification access only with the owner's consent.
2. Repeat the explicit-memory/task/reminder commands above, checking visible notifications and read-back after app restart. Confirm and then delete test memories; complete test tasks in the task screen.
3. Configure the owner's chosen AI provider in Settings without logging or committing its key. Verify a normal answer and memory-related answer.
4. Review an SMS draft without sending to a real number unless the owner explicitly authorizes the recipient and message. Verify rejection/cancellation and policy/audit behavior.
5. To test the gateway, enable it in Settings, forward USB, and pair using the code shown on the phone:

   ```bash
   adb -s <device-serial> forward tcp:8787 tcp:8787
   ```

   In `cli/`, with dependencies installed:

   ```bash
   uv run --frozen opencell pair 127.0.0.1
   uv run --frozen opencell status
   uv run --frozen opencell assistant ask "What should I follow up on?"
   ```

   The last command is an unverified end-to-end check, not a known-working claim. Investigate the empty-history remote adapter if the question does not reach the model.

6. Exercise real SIM audio/Shizuku and third-party call notifications separately with the owner. Keep private phone data, tokens, transcripts, and raw notification dumps out of Git.

## Documentation and Git checks

Before committing, compare the seven snapshot copies against their source documents, allowing only the README link relocation and whitespace differences. Confirm that every tracked original Markdown document is represented and that every local Markdown link in this folder resolves.

The documentation-session validation **passed** for all seven source/copy pairs, all 11 context Markdown files, and all 16 relative file links. It also checked balanced code fences and parsed the JUnit XML to confirm the 91-test total. A targeted scan found no private-key blocks, common GitHub/provider token patterns, or machine-specific workspace/temporary paths in this folder; this is not a comprehensive credential audit of the full repository.

```bash
git diff --check
git diff --cached --check
git status --short --branch
```

Stage only `project-context/`. The new commit must have `udaysoni.hbd@gmail.com` as both author and committer, with no co-author trailers. Inspect both identities with:

```bash
git show -s --format=fuller HEAD
```

Push only the feature branch:

```bash
git push -u origin docs/project-context
```

The initial non-writing push dry-run failed because this environment had no HTTPS GitHub credentials. Authenticate in the pushing terminal (for example `gh auth login` followed by `gh auth setup-git` if GitHub CLI is available), then retry. Never paste a token into a Markdown file, remote URL, or chat. The GitHub CLI was not installed in the documentation-session container.

## Remaining reproducibility/deployment gaps

- No Dockerfile, single offline test entry point, or dedicated runtime-version pin files were present in the inspected project. This task records those gaps rather than claiming they are fixed or adding unrelated build infrastructure.
- Pages publication needs repository Settings → Pages → GitHub Actions and a permitted workflow run. Pushing `docs/project-context` is not a `main` website deployment.
- No pull request, merge, GitHub Release, F-Droid publication, or signed APK release is part of this task.
