# OpenCell setup guide

Two parts:

1. **[On your phone](#1-install-on-your-phone)**: required. The Phone + SMS app, with one call list for all your calls.
2. **[On a computer](#2-install-on-a-computer-optional)**: optional. Control the phone from a CLI, scripts or AI agents (Windows, macOS, Linux, Raspberry Pi).

Downloads live on the **[Releases page](https://github.com/UdaySoni32/OpenCell/releases/latest)**.

---

## 1. Install on your phone

**Needs:** Android 11 or newer. No root.

### 1.1 Download and install

1. On the phone, open the [latest release](https://github.com/UdaySoni32/OpenCell/releases/latest) and download **`OpenCell-<version>.apk`**.
2. Tap the downloaded file. If Android asks, allow **Install unknown apps** for your browser or file manager.
3. Google Play Protect may say the app is unknown. Tap **More details › Install anyway**. OpenCell is open source and isn't on the Play Store, because Google restricts the call-log and SMS permissions a phone app needs.

Optional: check the download against `SHA256SUMS.txt` from the release.

```bash
sha256sum OpenCell-*.apk
```

### 1.2 First launch: the setup guide

OpenCell opens a short guide. Each step has a button:

| Step | What to tap | Why |
|---|---|---|
| Make OpenCell your phone app | **Set as phone app › OpenCell › Set as default** | Only the default phone app can show the call screen and answer calls |
| Make OpenCell your SMS app | **Set as SMS app › OpenCell › Set as default** | Only the default SMS app receives texts |
| Allow permissions | **Allow** on each prompt | Calls, call log, contacts, SMS, notifications |
| See WhatsApp/Discord calls *(optional)* | **Open notification access**, switch OpenCell on, come back | Reads other apps' *call* notifications, so their missed calls appear in your list |

You can replay the guide from **Settings › Setup guide**. To switch back to your old apps, use **Android Settings › Apps › Default apps**.

### 1.3 Keep it running (important on Xiaomi, Oppo, Vivo, Realme, Samsung)

Some phones kill background apps aggressively. To avoid missed SMS or a stopped gateway:

- **Settings › Apps › OpenCell › Battery**: set to **Unrestricted** (or "No restrictions").
- On Xiaomi/Redmi/POCO, also turn on **Autostart** for OpenCell.

### 1.4 Optional: AI assistant

**Settings › AI assistant**:

1. Pick a provider chip and save:
   - **Your server (Ollama)**: free and private. Runs on your own PC or Pi (see [2.6](#26-optional-run-the-ai-on-your-own-computer)).
   - **OpenAI, Anthropic Claude, Google Gemini, Groq**: paste your own API key. It's stored encrypted on the phone.
2. Tap **Test AI**.
3. Turn on what you want:
   - **Reply to SMS**: drafts replies.
   - **Screen unknown callers**: declines unknown numbers and asks by SMS what the call is about.
   - **Learn from my SMS**: remembers facts.
   - **AI answers missed calls**: picks up calls you don't answer.
4. Write a few lines in **About you › Instructions**, for example "I'm a student, reply in Hinglish to friends, decline sales calls."

**Safety:** by default, nothing is sent without your approval. Approve or deny in the notification, or in **Settings › Permissions & activity**, which also has the **kill switch** and the full activity log. The assistant never replies to banks, short codes or one-time codes.

### 1.5 Optional: better AI voice with Shizuku (no root)

Android doesn't let apps hear SIM calls. With **Shizuku**, the AI hears the caller directly. Without it, the AI uses the speakerphone and microphone.

1. Install [Shizuku](https://shizuku.rikka.app/download/) from the Play Store or GitHub.
2. Enable **Developer options** (tap *Build number* 7 times), then turn on **Wireless debugging**.
3. Open Shizuku › **Start via Wireless debugging** and follow its pairing steps.
4. In OpenCell › Settings › AI assistant › Voice calls, tap **Allow Shizuku**.

Shizuku has to be started again after each reboot. Rooted phones can install **Sui** (a Magisk module) instead, which works the same way.

**Recording laws vary.** In India the DPDP Act 2023 applies, and some countries require telling callers they're talking to an AI. "Learn from calls I answer" is off by default.

---

## 2. Install on a computer (optional)

The `opencell` package gives you a **CLI**, a **Python library** and an **MCP server** for AI agents. The same install works on Windows, macOS, Linux, WSL and Raspberry Pi.

### 2.1 Install Python and pipx

| System | Command |
|---|---|
| Windows | Install [Python 3.10+](https://www.python.org/downloads/) (tick *Add to PATH*), then `py -m pip install --user pipx` and `py -m pipx ensurepath` |
| macOS | `brew install pipx && pipx ensurepath` |
| Ubuntu / Debian / Raspberry Pi OS / WSL | `sudo apt install pipx && pipx ensurepath` |

Open a new terminal afterwards.

### 2.2 Install opencell

Latest from GitHub:

```bash
pipx install "git+https://github.com/UdaySoni32/OpenCell#subdirectory=cli"
```

Or install the wheel from a release (no git needed):

```bash
pipx install https://github.com/UdaySoni32/OpenCell/releases/download/v0.1.0/opencell-0.1.0-py3-none-any.whl
```

Check it worked:

```bash
opencell --help
```

### 2.3 Connect the computer to the phone

Choose one:

| Way | When | Phone address to use |
|---|---|---|
| **Same Wi-Fi** | At home | The phone's Wi-Fi IP, shown in OpenCell › Settings › Computers & API |
| **Tailscale** (recommended) | From anywhere: mobile data, other networks | Install [Tailscale](https://tailscale.com/download) on the phone **and** the computer, sign in to the same account, use the phone's `100.x.x.x` address |
| **USB cable** | No network | Run `adb forward tcp:8787 tcp:8787`, then use `127.0.0.1` |

### 2.4 Pair

1. On the phone: **Settings › Computers & API**, switch **Gateway** on, tap **Show pairing code**.
2. On the computer:
   ```bash
   opencell pair 100.101.102.103
   ```
   Use your phone's address, then type the 6-digit code when asked.
3. Test the connection:
   ```bash
   opencell status
   ```

Each computer gets its own key. You can revoke it, or allow "act without asking", per computer on the phone.

### 2.5 Use it

```bash
opencell sms send +919876543210 "Running 10 min late"
opencell sms list
opencell call place +919876543210
opencell call place +919876543210 --goal "Ask if the clinic has a Saturday slot"
opencell call list
opencell call active
opencell events
```

- `call place --goal` lets the phone's AI voice agent handle the call.
- `call active` shows each call's sid; use it with `call hangup <sid>`.
- `events` streams live events as JSON.

Outbound actions show `waiting for approval on the phone` until you approve them there, unless you changed the policy.

**MCP (AI agents).** Add this to your MCP client config (Claude Desktop, Claude Code, etc.):

```json
{ "mcpServers": { "opencell": { "command": "opencell", "args": ["mcp"] } } }
```

**Python:**

```python
from opencell import OpenCellClient
with OpenCellClient("http://100.101.102.103:8787", token="oc_...") as phone:
    phone.send_sms("+919876543210", "hi")
```

### 2.6 Optional: run the AI on your own computer

**LLM with Ollama.** Install [Ollama](https://ollama.com/download), then:

```bash
ollama pull gemma3:4b
```

Make Ollama listen on the network:

- macOS/Linux: `OLLAMA_HOST=0.0.0.0 ollama serve`
- Windows: set the environment variable `OLLAMA_HOST=0.0.0.0`, then restart Ollama.

In OpenCell › AI assistant, choose **Your server (Ollama)** and set the URL to `http://<computer-tailscale-ip>:11434/v1`.

**Speech with opencell brain** (Whisper speech-to-text + Piper voices):

```bash
pipx install --force "opencell[brain] @ git+https://github.com/UdaySoni32/OpenCell#subdirectory=cli"
opencell brain --piper-voice ~/voices/en_US-amy-medium.onnx --token choose-a-secret
```

Download a Piper voice (`.onnx` plus `.onnx.json`) from the [Piper voices list](https://huggingface.co/rhasspy/piper-voices). In OpenCell › AI assistant › Voice calls, choose **Your server (opencell brain)**, set the URL to `http://<computer-tailscale-ip>:8788/v1`, and use your token as the API key.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| "App not installed" | Uninstall any older OpenCell build first (it was signed with a different key) |
| Calls ring in the old dialer | Settings › Setup guide › set OpenCell as the phone app again |
| WhatsApp/Discord calls missing from the list | Turn on notification access for OpenCell; make sure those apps show call notifications |
| `cannot reach phone` | Gateway switched on? Same Wi-Fi or Tailscale on both? Battery set to Unrestricted? |
| `token lacks scope approve` | Approve on the phone, or enable "May approve requests" for that computer |
| AI replies don't send | They're waiting in Settings › Permissions & activity, or the kill switch is on |
| Voice AI says "allow the microphone or Shizuku" | Settings › AI assistant › Voice calls › Allow microphone (or set up Shizuku) |

To uninstall, set your previous apps as default again (Android Settings › Apps › Default apps), then uninstall OpenCell like any app. The computer token stops working once you revoke the computer on the phone. The CLI config lives at `~/.config/opencell/config.json`.
