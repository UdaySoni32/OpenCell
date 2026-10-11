import asyncio

from opencell.mcp_server import build_server


def _call(server, name, args):
    result = asyncio.run(server.call_tool(name, args))
    return " ".join(getattr(c, "text", "") for c in result.content)


def test_lists_all_tools(client):
    tools = {t.name for t in asyncio.run(build_server(client).list_tools())}
    assert tools == {"send_sms", "list_messages", "place_call", "list_calls", "active_calls", "control_call", "list_approvals"}


def test_send_sms_returns_pending(client, gateway):
    out = _call(build_server(client), "send_sms", {"to": "+15550001111", "body": "hi"})
    assert "pending_approval" in str(out)
    assert gateway.sent == [{"to": "+15550001111", "body": "hi"}]


def test_errors_become_readable_results(client, gateway):
    gateway.sms_status = "denied"
    out = _call(build_server(client), "send_sms", {"to": "+1555", "body": "x"})
    assert "kill switch is on" in str(out)


def test_control_call_rejects_bad_action(client):
    out = _call(build_server(client), "control_call", {"sid": "CA0", "action": "explode"})
    assert "action must be" in str(out)
