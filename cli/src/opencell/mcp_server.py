"""MCP server exposing the phone to AI agents. Every outbound action still obeys the phone's permission policy."""

from __future__ import annotations

from mcp.server.mcpserver import MCPServer

from opencell.client import OpenCellClient, OpenCellError

INSTRUCTIONS = (
    "Tools to send SMS and place/control phone calls through the user's own Android phone (OpenCell). "
    "Outbound actions may return status 'pending_approval': the user must approve on the phone; do not retry, "
    "check list_approvals later. Never send SMS or call anyone the user did not ask you to contact."
)


def _safe(fn):  # noqa: ANN001, ANN202 - tiny adapter
    """Turn gateway errors into a result the model can read instead of a protocol error."""
    try:
        return fn()
    except OpenCellError as e:
        return {"error": e.message, "http_status": e.status}


def build_server(client: OpenCellClient) -> MCPServer:
    server = MCPServer("opencell", instructions=INSTRUCTIONS)

    @server.tool()
    def send_sms(to: str, body: str) -> dict:
        """Send an SMS from the user's phone number. `to` is a phone number like +919876543210."""
        return _safe(lambda: client.send_sms(to, body))

    @server.tool()
    def list_messages(limit: int = 20) -> list[dict] | dict:
        """Recent SMS (incoming and outgoing), newest first."""
        return _safe(lambda: client.messages(limit))

    @server.tool()
    def place_call(to: str, agent_goal: str | None = None) -> dict:
        """Start a phone call from the user's phone. With `agent_goal` (e.g. "book a table for 2 at 8pm"),
        the phone's AI voice agent conducts the call; otherwise the user talks."""
        return _safe(lambda: client.place_call(to, agent_goal=agent_goal))

    @server.tool()
    def list_calls(limit: int = 20) -> list[dict] | dict:
        """Unified call history: SIM calls plus WhatsApp/Discord/Telegram/... calls, newest first."""
        return _safe(lambda: client.calls(limit))

    @server.tool()
    def active_calls() -> list[dict] | dict:
        """Calls currently ringing or in progress, with their sid for control_call."""
        return _safe(client.active_calls)

    @server.tool()
    def control_call(sid: str, action: str) -> dict:
        """Answer, reject or hang up an active call. action is one of: answer, reject, hangup."""
        if action not in {"answer", "reject", "hangup"}:
            return {"error": "action must be answer, reject or hangup"}
        return _safe(lambda: client.call_action(sid, action))

    @server.tool()
    def list_approvals() -> list[dict] | dict:
        """Actions waiting for (or decided by) the user on the phone."""
        return _safe(client.approvals)

    @server.tool()
    def ask_assistant(message: str) -> dict:
        """Ask the phone's personal assistant. SMS/call results are drafts and require review."""
        return _safe(lambda: client.ask_assistant(message))

    return server
