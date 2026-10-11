# opencell (CLI · Python client · MCP server)

Control calls and SMS on your own Android phone running [OpenCell](../README.md) — from a Raspberry Pi, WSL, Linux,
macOS or Windows.

```bash
pipx install ./cli          # or: uv tool install ./cli
opencell pair 100.101.102.103      # phone address from OpenCell > Settings > Computers & API; enter the 6-digit code
opencell sms send +919876543210 "Running late"
opencell call place +919876543210
opencell call list
opencell events                    # live JSON events
```

Reaching the phone:

- **Same Wi-Fi**: use the phone's LAN address.
- **Anywhere**: install [Tailscale](https://tailscale.com) on the phone and the computer, use the phone's `100.x` address.
- **USB**: `adb forward tcp:8787 tcp:8787`, then `opencell pair 127.0.0.1`.

Outbound actions obey the phone's permission settings: by default you approve each one on the phone, and the CLI
prints `waiting for approval`.

## MCP

Add to your MCP client config:

```json
{ "mcpServers": { "opencell": { "command": "opencell", "args": ["mcp"] } } }
```

Tools: `send_sms`, `list_messages`, `place_call`, `list_calls`, `active_calls`, `control_call`, `list_approvals`.

## Python

```python
from opencell import OpenCellClient

with OpenCellClient("http://100.101.102.103:8787", token="oc_...") as phone:
    print(phone.send_sms("+919876543210", "hi"))
    for event in phone.events():
        print(event)
```
