# OpenCell

An open-source replacement for Android's Phone and SMS apps, with:

- **One call list** for SIM calls *and* WhatsApp, Discord, Telegram, Signal, Messenger, Teams… calls (read from their call notifications), with call-back from the list.
- **A Twilio-style local API + CLI + MCP server** so your own projects (AI agents, scripts) can make and take calls and SMS through your phone — from any computer, anywhere (via Tailscale).
- **A bring-your-own-keys AI assistant** that learns from your instructions and answers/makes calls and SMS — only with your permission.

No root. No VoIP number. No OpenCell cloud.

## Download

Project website: [OpenCell on GitHub Pages](https://udaysoni32.github.io/OpenCell/)

| | |
|---|---|
| 📱 **Phone app** | [**OpenCell APK**: latest release](https://github.com/UdaySoni32/OpenCell/releases/latest) (Android 11+) |
| 💻 **Computer** (Windows / macOS / Linux / Pi) | `pipx install "git+https://github.com/UdaySoni32/OpenCell#subdirectory=cli"`, or the `.whl` from the release |
| 📖 **Setup guide** | [docs/SETUP.md](docs/SETUP.md): install, permissions, AI, Shizuku, pairing a computer, troubleshooting |

The app also walks you through setup the first time you open it.

Design: [`docs/superpowers/specs/2026-10-11-opencell-v2-design.md`](docs/superpowers/specs/2026-10-11-opencell-v2-design.md) ·
Build plan: [`docs/superpowers/plans/2026-10-11-opencell-v2-implementation.md`](docs/superpowers/plans/2026-10-11-opencell-v2-implementation.md)

## Status

| Phase | What | State |
|---|---|---|
| 1 | Dialer, in-call UI, SMS, contacts, unified call list | ✅ done |
| 2 | Gateway API, permissions, `opencell` CLI/MCP ([cli/](cli/README.md)) | ✅ done |
| 3 | AI (text): SMS agent, call screening, memory | ✅ done |
| 4 | Voice agent (Shizuku / speaker modes, root via Sui) | ✅ done (needs real-device tuning) |

### Voice calls

Android does not let apps touch SIM-call audio, so the AI uses one of these, picked automatically:

| Mode | Setup | How it works |
|---|---|---|
| **Shizuku** (best) | Install [Shizuku](https://shizuku.rikka.app), start it via Wireless debugging, tap *Allow Shizuku* in OpenCell | Hears the caller directly (`VOICE_DOWNLINK`), speaks through the speakerphone |
| **Speaker** | Allow the microphone | Speakerphone + mic, half-duplex (lower quality, audible in the room) |
| **Text** | nothing | Declines and continues by SMS (Phase 3 call screening) |

Root users: install Sui (Magisk) — it speaks the Shizuku API. Use it from the in-call screen (**Let AI handle**),
let it answer calls you miss, or have your code place AI calls:

```bash
opencell call place +919876543210 --goal "Ask if the clinic has a slot on Saturday morning"
```

Speech-to-text / text-to-speech run on the phone, on your own server (`opencell brain`: Whisper + Piper, see
[cli/README.md](cli/README.md)) or a cloud API (OpenAI-compatible).

**Check a phone quickly:** during a call, run
`adb shell CLASSPATH=$(adb shell pm path com.opencell | cut -d: -f2) app_process /system/bin com.opencell.voice.shizuku.CallAudioProbe 3`
— it prints whether shell-level call capture works on that device.

Recording and AI-handled calls are regulated differently around the world (in India, the DPDP Act 2023 applies);
"Learn from calls I answer" is off by default.

### AI assistant (Settings → AI assistant)

Bring your own model: your own server (Ollama / any OpenAI-compatible URL, e.g. over Tailscale), OpenAI, Anthropic,
Gemini or Groq, plus an optional fallback. Keys are encrypted with the Android Keystore. The assistant can reply to
SMS, screen unknown callers by SMS, and learn facts from conversations (you can view and delete every fact). It never
answers banks, short codes or one-time codes, and everything it sends goes through **Permissions & activity**
(default: you approve each message).

## Build

### Linux setup (online)

Use **Python 3.14.7** (`.python-version`), **JDK 25** (`.java-version`), and **uv 0.12.18**.
The Gradle wrapper pins Gradle; `cli/uv.lock` pins Python application and test dependencies.
Install a JDK 25 and the Android SDK using Android Studio or Google's command-line tools;
install SDK platform **36** and build tools **36.0.0**. Linux also needs `make`, `git`,
CA certificates, a C/C++ build toolchain, and Python's `venv` support. The Dockerfile below
provides these system dependencies in an image.

From the repository root, set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your SDK.
For example, with a JDK already selected on `PATH` and the usual Linux SDK location:

```bash
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
python3 -m venv .tools/uv
.tools/uv/bin/python -m pip install 'uv==0.12.18'
export PATH="$PWD/.tools/uv/bin:$PATH"
make setup
```

`make setup` (alias `make sync`) downloads dependencies from the existing lock, builds the
Python wheel/sdist, and runs the Android JVM and Python tests while building the debug APK.
It also warms the caches needed for offline work. It does not install the optional
Whisper/Piper voice models; see [cli/README.md](cli/README.md) for that optional setup.

Environments and downloads stay in this checkout: `UV_PROJECT_ENVIRONMENT=cli/.venv`,
`UV_CACHE_DIR=.cache/uv`, `UV_PYTHON_INSTALL_DIR=.tools/python`, and
`GRADLE_USER_HOME=.cache/gradle` (the Makefile resolves these relative to the checkout).
These variables can be overridden explicitly, for example to reuse a populated Gradle cache.
Keep the warmed caches and environment when working offline; rerun setup online after
changing dependencies, Python, SDK, or Gradle versions.

### Test, build, and run (offline after setup)

```bash
make test                       # real :app:testDebugUnitTest + Python pytest; no downloads
make build                      # debug APK + Python wheel/sdist
make run ARGS="--help"           # CLI, using the project-local environment
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK is in `app/build/outputs/apk/debug/`; Python artifacts are in `cli/dist/`.
Open the installed app and follow the setup guide. For CLI pairing, use
`make run ARGS="pair --help"` and [cli/README.md](cli/README.md).
The offline test target needs neither a running phone nor an emulator.

### Docker build and deployment

The Linux Docker build downloads its own system packages, JDK 25, Python 3.14.7,
uv 0.12.18, the fixed Android command-line-tools archive `13114758` (19.0), SDK 36,
and build tools 36.0.0. Python dependency hashes come from `cli/uv.lock`.
The command-line-tools archive is verified against Google's published SHA-1 checksum.
Base images use version tags (JDK major 25); SDK platform/platform-tools updates and
OS package updates follow their upstream repositories, so this is not a bit-for-bit image pin.
The image build needs network access and runs setup, offline tests, and artifact builds:

```bash
docker build -t opencell .
docker run --rm opencell --help
docker run --rm --entrypoint make opencell test
docker create --name opencell-artifacts opencell
docker cp opencell-artifacts:/workspace/app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk
docker cp opencell-artifacts:/workspace/cli/dist ./cli-dist
docker rm opencell-artifacts
```

Deploy the extracted artifacts from the repository root:

```bash
adb install -r app-debug.apk
python3.14 -m venv .tools/deploy
.tools/deploy/bin/python -m pip install ./cli-dist/opencell-0.1.0-py3-none-any.whl
.tools/deploy/bin/opencell --help
```

Use Python 3.14.7 for that virtual environment; wheel dependency installation needs network
access. You can also run the CLI directly from the container. The default
entrypoint is `opencell`; override it with Docker's `--entrypoint` for other commands.
For persistent CLI pairing, mount a private directory at `/root/.config/opencell`:

```bash
docker run --rm -it -v opencell-config:/root/.config/opencell opencell pair --help
```

The phone's gateway must be reachable from the container (for example via its LAN or
Tailscale address). Emulator/device integration remains a **host requirement**: start an
Android emulator or attach a phone on the host and use host `adb`; the image runs JVM
unit tests, not an emulator. Signing keys, local SDK paths, caches, and `.env` files are
excluded from the Docker build context.

Release builds (`./gradlew :app:assembleRelease`) are shrunk with R8 and signed from a git-ignored
`keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without that file they're unsigned.

### Emulator testing

```bash
adb emu gsm call 5551112222          # incoming SIM call
adb emu sms send 5559998888 "hello"  # incoming SMS
```

`testapps/fakecaller` is an emulator-only fixture that pretends to be WhatsApp (`com.whatsapp`) and posts call
notifications, so the unified call list can be tested without real accounts. **Never install it on a real phone.**

```bash
./gradlew :testapps:fakecaller:assembleDebug
adb install -r testapps/fakecaller/build/outputs/apk/debug/fakecaller-debug.apk
adb shell am start -n com.whatsapp/com.opencell.fakecaller.PostActivity --es type missed --es name Riya
```

## Distribution

GitHub Releases and F-Droid. Google Play restricts the call-log/SMS permissions a default dialer needs.

## License

Apache-2.0
