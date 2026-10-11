"""HTTP + WebSocket client for the OpenCell gateway running on the phone."""

from __future__ import annotations

import json
from collections.abc import Iterator
from typing import Any

import httpx

DEFAULT_PORT = 8787


class OpenCellError(Exception):
    """The gateway refused or failed a request."""

    def __init__(self, status: int, message: str, payload: Any = None):
        super().__init__(f"{status}: {message}")
        self.status = status
        self.message = message
        self.payload = payload


class OpenCellClient:
    """Thin wrapper over the Twilio-shaped REST API.

    Action calls (send_sms, place_call, call_action) return the gateway's ActionResponse dict:
    ``{"status": "done" | "pending_approval", "sid": ..., "approval_sid": ...}``.
    Denied (403) and failed (422) actions raise :class:`OpenCellError`.
    """

    def __init__(
        self,
        base_url: str,
        token: str | None = None,
        timeout: float = 15.0,
        transport: httpx.BaseTransport | None = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.token = token
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        self._http = httpx.Client(base_url=self.base_url, headers=headers, timeout=timeout, transport=transport)

    @staticmethod
    def url_for(host: str, port: int = DEFAULT_PORT) -> str:
        return host if host.startswith(("http://", "https://")) else f"http://{host}:{port}"

    def close(self) -> None:
        self._http.close()

    def __enter__(self) -> OpenCellClient:
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()

    # --- API -------------------------------------------------------------------------------------------------

    def health(self) -> dict:
        return self._request("GET", "/v1/Health")

    def pair(self, code: str, name: str) -> dict:
        return self._request("POST", "/v1/Pair", json={"code": code, "name": name})

    def messages(self, limit: int = 50) -> list[dict]:
        return self._request("GET", "/v1/Messages", params={"limit": limit})

    def message(self, sid: str) -> dict:
        return self._request("GET", f"/v1/Messages/{sid}")

    def send_sms(self, to: str, body: str) -> dict:
        return self._request("POST", "/v1/Messages", json={"to": to, "body": body})

    def calls(self, limit: int = 50) -> list[dict]:
        return self._request("GET", "/v1/Calls", params={"limit": limit})

    def active_calls(self) -> list[dict]:
        return self._request("GET", "/v1/Calls/Active")

    def place_call(self, to: str) -> dict:
        return self._request("POST", "/v1/Calls", json={"to": to})

    def call_action(self, sid: str, action: str) -> dict:
        if action not in {"answer", "reject", "hangup"}:
            raise ValueError("action must be answer, reject or hangup")
        return self._request("POST", f"/v1/Calls/{sid}", json={"action": action})

    def approvals(self) -> list[dict]:
        return self._request("GET", "/v1/Approvals")

    def decide(self, approval_sid: str, approve: bool) -> dict:
        return self._request("POST", f"/v1/Approvals/{approval_sid}", json={"decision": "approve" if approve else "deny"})

    def events(self) -> Iterator[dict]:
        """Yields gateway events (sms.received, call.state, approval.updated) until the connection closes."""
        from websockets.sync.client import connect

        ws_url = self.base_url.replace("https://", "wss://", 1).replace("http://", "ws://", 1) + "/v1/Events"
        headers = {"Authorization": f"Bearer {self.token}"} if self.token else None
        with connect(ws_url, additional_headers=headers) as ws:
            for raw in ws:
                yield json.loads(raw)

    # --- plumbing --------------------------------------------------------------------------------------------

    def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        try:
            res = self._http.request(method, path, **kwargs)
        except httpx.HTTPError as e:
            raise OpenCellError(0, f"cannot reach phone at {self.base_url} ({e.__class__.__name__})") from e
        try:
            payload = res.json()
        except ValueError:
            payload = None
        if res.status_code >= 400:
            message = (payload or {}).get("error") or (payload or {}).get("reason") or res.reason_phrase
            raise OpenCellError(res.status_code, message, payload)
        return payload
