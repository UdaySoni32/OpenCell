from __future__ import annotations

import json

import httpx
import pytest

from opencell.client import OpenCellClient


class FakeGateway:
    """In-memory stand-in for the phone's gateway, speaking the same JSON."""

    def __init__(self) -> None:
        self.token = "oc_test"
        self.sent: list[dict] = []
        self.sms_status = "pending_approval"

    def handler(self, request: httpx.Request) -> httpx.Response:
        path, method = request.url.path, request.method
        if path == "/v1/Health":
            return httpx.Response(200, json={"status": "ok", "app": "OpenCell", "version": "test"})
        if path == "/v1/Pair":
            body = json.loads(request.content)
            if body["code"] != "123456":
                return httpx.Response(401, json={"error": "invalid or expired pairing code"})
            return httpx.Response(201, json={"token": self.token, "device_id": 1, "scopes": ["read", "sms:send"]})
        if request.headers.get("Authorization") != f"Bearer {self.token}":
            return httpx.Response(401, json={"error": "missing or invalid token"})
        if path == "/v1/Messages" and method == "GET":
            return httpx.Response(200, json=[{"sid": "SM1", "from": "+1555", "to": "me", "body": "hi", "status": "received", "direction": "inbound", "date_created": 0}])
        if path == "/v1/Messages" and method == "POST":
            body = json.loads(request.content)
            self.sent.append(body)
            if self.sms_status == "denied":
                return httpx.Response(403, json={"status": "denied", "reason": "kill switch is on"})
            if self.sms_status == "done":
                return httpx.Response(201, json={"status": "done", "sid": "SM9"})
            return httpx.Response(202, json={"status": "pending_approval", "approval_sid": "AP1"})
        if path == "/v1/Calls" and method == "POST":
            return httpx.Response(201, json={"status": "done"})
        if path == "/v1/Calls" and method == "GET":
            return httpx.Response(200, json=[{"sid": "app-1", "source": "whatsapp", "kind": "missed", "peer_name": "Riya", "peer_number": None, "date_created": 0, "duration": 0}])
        if path == "/v1/Calls/Active":
            return httpx.Response(200, json=[{"sid": "CA0", "number": "+1555", "name": None, "status": "ringing", "direction": "inbound"}])
        if path.startswith("/v1/Calls/CA"):
            return httpx.Response(201, json={"status": "done", "sid": path.rsplit("/", 1)[1]})
        if path == "/v1/Approvals":
            return httpx.Response(200, json=[{"sid": "AP1", "actor": "computer \"t\"", "action": "send_sms", "target": "+1555", "body": "x", "status": "pending", "date_created": 0}])
        return httpx.Response(404, json={"error": "not found"})


@pytest.fixture
def gateway() -> FakeGateway:
    return FakeGateway()


@pytest.fixture
def client(gateway: FakeGateway) -> OpenCellClient:
    return OpenCellClient("http://phone:8787", gateway.token, transport=httpx.MockTransport(gateway.handler))
