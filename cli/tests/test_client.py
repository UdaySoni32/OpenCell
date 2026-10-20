import httpx
import pytest

from opencell.client import OpenCellClient, OpenCellError


def test_send_sms_pending_approval(client, gateway):
    res = client.send_sms("+15550001111", "hello")
    assert res == {"status": "pending_approval", "approval_sid": "AP1"}
    assert gateway.sent == [{"to": "+15550001111", "body": "hello"}]


def test_denied_raises_with_reason(client, gateway):
    gateway.sms_status = "denied"
    with pytest.raises(OpenCellError) as e:
        client.send_sms("+1555", "x")
    assert e.value.status == 403
    assert e.value.message == "kill switch is on"


def test_bad_token_raises_401(gateway):
    c = OpenCellClient("http://phone:8787", "wrong", transport=httpx.MockTransport(gateway.handler))
    with pytest.raises(OpenCellError) as e:
        c.messages()
    assert e.value.status == 401


def test_unreachable_phone_is_a_clean_error():
    def boom(request):
        raise httpx.ConnectError("refused")

    c = OpenCellClient("http://phone:8787", "t", transport=httpx.MockTransport(boom))
    with pytest.raises(OpenCellError) as e:
        c.health()
    assert e.value.status == 0
    assert "cannot reach phone" in e.value.message


def test_call_action_validates_locally(client):
    with pytest.raises(ValueError):
        client.call_action("CA0", "explode")
    assert client.call_action("CA0", "hangup")["sid"] == "CA0"


def test_url_for():
    assert OpenCellClient.url_for("100.64.1.2") == "http://100.64.1.2:8787"
    assert OpenCellClient.url_for("https://phone.ts.net") == "https://phone.ts.net"


def test_ask_assistant_returns_a_draft_or_answer(client):
    assert client.ask_assistant("hello") == {"status": "answer", "reply": "You asked: hello"}
