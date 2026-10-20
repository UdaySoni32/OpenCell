"""`opencell` command line: pair with a phone, then send SMS, place calls, watch events, run the MCP server."""

from __future__ import annotations

import json
import socket
from datetime import datetime
from typing import Any

import typer

from opencell import config
from opencell.client import DEFAULT_PORT, OpenCellClient, OpenCellError

app = typer.Typer(help="Control calls and SMS on your OpenCell phone.", no_args_is_help=True)
sms_app = typer.Typer(help="Send and list SMS.", no_args_is_help=True)
call_app = typer.Typer(help="Place, list and control calls.", no_args_is_help=True)
approvals_app = typer.Typer(help="Requests waiting for approval on the phone.", no_args_is_help=True)
assistant_app = typer.Typer(help="Ask the assistant on the phone.", no_args_is_help=True)
app.add_typer(sms_app, name="sms")
app.add_typer(call_app, name="call")
app.add_typer(approvals_app, name="approvals")
app.add_typer(assistant_app, name="assistant")


def make_client() -> OpenCellClient:
    """Client for the paired phone. Tests replace this function."""
    cfg = config.load()
    if cfg is None:
        raise typer.BadParameter("not paired yet: run `opencell pair <phone-address>` first")
    return OpenCellClient(cfg.url, cfg.token)


def _out(data: Any, as_json: bool, line: str) -> None:
    typer.echo(json.dumps(data, indent=2) if as_json else line)


def _fmt_time(ms: int) -> str:
    return datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M")


def _action_line(res: dict, what: str) -> str:
    if res.get("status") == "pending_approval":
        return f"{what}: waiting for approval on the phone ({res.get('approval_sid')})"
    return f"{what}: done" + (f" ({res['sid']})" if res.get("sid") else "")


def _run(fn: Any) -> Any:
    try:
        return fn()
    except OpenCellError as e:
        typer.secho(f"error: {e.message}" + (f" (HTTP {e.status})" if e.status else ""), fg="red", err=True)
        raise typer.Exit(1) from e


@app.command()
def pair(
    host: str = typer.Argument(..., help="Phone address shown in OpenCell > Settings > Computers & API (Wi-Fi, Tailscale or 127.0.0.1 with adb forward)"),
    code: str = typer.Option(..., prompt="Pairing code shown on the phone", help="6-digit code from the phone"),
    name: str = typer.Option(socket.gethostname(), help="Name this computer will have on the phone"),
    port: int = typer.Option(DEFAULT_PORT),
) -> None:
    """Pair this computer with the phone and save the token."""
    url = OpenCellClient.url_for(host, port)
    with OpenCellClient(url) as client:
        res = _run(lambda: client.pair(code.replace(" ", ""), name))
    path = config.save(config.Config(url, res["token"]))
    typer.echo(f"Paired as '{name}' with scopes {', '.join(res['scopes'])}. Token saved to {path}")


@app.command()
def status(as_json: bool = typer.Option(False, "--json")) -> None:
    """Check that the phone is reachable and the token works."""
    with make_client() as client:
        health = _run(client.health)
        _run(lambda: client.messages(limit=1))
    _out(health, as_json, f"OK: {health['app']} {health['version']} at {client.base_url}")


@sms_app.command("send")
def sms_send(to: str, body: str, as_json: bool = typer.Option(False, "--json")) -> None:
    """Send an SMS from the phone (may need approval on the phone)."""
    with make_client() as client:
        res = _run(lambda: client.send_sms(to, body))
    _out(res, as_json, _action_line(res, f"SMS to {to}"))


@sms_app.command("list")
def sms_list(limit: int = 20, as_json: bool = typer.Option(False, "--json")) -> None:
    """Recent messages, newest first."""
    with make_client() as client:
        msgs = _run(lambda: client.messages(limit))
    lines = [f"{_fmt_time(m['date_created'])}  {'←' if m['direction'] == 'inbound' else '→'} {m['from'] if m['direction'] == 'inbound' else m['to']}: {m['body']}  [{m['status']}]" for m in msgs]
    _out(msgs, as_json, "\n".join(lines) or "no messages")


@call_app.command("place")
def call_place(
    to: str,
    goal: str = typer.Option(None, help="Let the phone's AI voice agent conduct the call toward this goal"),
    as_json: bool = typer.Option(False, "--json"),
) -> None:
    """Place a call from the phone (may need approval on the phone)."""
    with make_client() as client:
        res = _run(lambda: client.place_call(to, agent_goal=goal))
    _out(res, as_json, _action_line(res, f"Call to {to}" + (" (AI agent)" if goal else "")))


@call_app.command("list")
def call_list(limit: int = 20, as_json: bool = typer.Option(False, "--json")) -> None:
    """Unified call history: SIM and app calls (WhatsApp, Discord…)."""
    with make_client() as client:
        calls = _run(lambda: client.calls(limit))
    lines = [f"{_fmt_time(c['date_created'])}  {c['kind']:<8} {c['source']:<10} {c.get('peer_name') or c.get('peer_number') or 'unknown'}" for c in calls]
    _out(calls, as_json, "\n".join(lines) or "no calls")


@call_app.command("active")
def call_active(as_json: bool = typer.Option(False, "--json")) -> None:
    """Calls in progress right now."""
    with make_client() as client:
        calls = _run(client.active_calls)
    lines = [f"{c['sid']}  {c['status']:<10} {c['direction']:<8} {c.get('name') or c.get('number')}" for c in calls]
    _out(calls, as_json, "\n".join(lines) or "no active calls")


def _call_action(sid: str, action: str, as_json: bool) -> None:
    with make_client() as client:
        res = _run(lambda: client.call_action(sid, action))
    _out(res, as_json, _action_line(res, f"{action} {sid}"))


@call_app.command("answer")
def call_answer(sid: str, as_json: bool = typer.Option(False, "--json")) -> None:
    _call_action(sid, "answer", as_json)


@call_app.command("reject")
def call_reject(sid: str, as_json: bool = typer.Option(False, "--json")) -> None:
    _call_action(sid, "reject", as_json)


@call_app.command("hangup")
def call_hangup(sid: str, as_json: bool = typer.Option(False, "--json")) -> None:
    _call_action(sid, "hangup", as_json)


@approvals_app.command("list")
def approvals_list(as_json: bool = typer.Option(False, "--json")) -> None:
    with make_client() as client:
        items = _run(client.approvals)
    lines = [f"{a['sid']}  {a['status']:<9} {a['actor']} → {a['action']} {a['target']}" + (f": {a['body']}" if a.get("body") else "") for a in items]
    _out(items, as_json, "\n".join(lines) or "no approvals")


@approvals_app.command("approve")
def approvals_approve(sid: str) -> None:
    """Needs the 'May approve requests' switch for this computer on the phone."""
    with make_client() as client:
        res = _run(lambda: client.decide(sid, True))
    typer.echo(f"{sid}: {res['status']}")


@approvals_app.command("deny")
def approvals_deny(sid: str) -> None:
    with make_client() as client:
        res = _run(lambda: client.decide(sid, False))
    typer.echo(f"{sid}: {res['status']}")


@assistant_app.command("ask")
def assistant_ask(message: str, as_json: bool = typer.Option(False, "--json")) -> None:
    """Ask the phone assistant; any call/SMS result is returned as a reviewable draft."""
    with make_client() as client:
        res = _run(lambda: client.ask_assistant(message))
    _out(res, as_json, res.get("reply", ""))


@app.command()
def events() -> None:
    """Stream live events (one JSON object per line) until Ctrl+C."""
    client = make_client()
    try:
        for event in client.events():
            typer.echo(json.dumps(event))
    except KeyboardInterrupt:
        pass
    except OSError as e:
        typer.secho(f"error: event stream closed ({e})", fg="red", err=True)
        raise typer.Exit(1) from e
    finally:
        client.close()


@app.command()
def brain(
    host: str = typer.Option("0.0.0.0", help="Bind address (use your Tailscale IP to keep it private)"),
    port: int = typer.Option(8788),
    whisper_model: str = typer.Option("base", help="faster-whisper model size: tiny, base, small, medium, large-v3"),
    piper_voice: str = typer.Option(..., help="Path to a Piper voice .onnx file"),
    token: str = typer.Option(None, envvar="OPENCELL_BRAIN_TOKEN", help="Require this bearer token (set it as the API key in the phone)"),
) -> None:
    """Run the self-hosted speech server (Whisper + Piper) for the phone's voice agent."""
    from opencell.brain import serve

    serve(host, port, whisper_model, piper_voice, token)


@app.command()
def mcp() -> None:
    """Run the MCP server on stdio (add `opencell mcp` to your AI client's MCP config)."""
    from opencell.mcp_server import build_server

    build_server(make_client()).run()


if __name__ == "__main__":
    app()
