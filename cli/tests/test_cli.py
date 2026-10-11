import json

import httpx
import pytest
from typer.testing import CliRunner

from opencell import cli, config
from opencell.client import OpenCellClient

runner = CliRunner()


@pytest.fixture(autouse=True)
def isolated_config(tmp_path, monkeypatch):
    monkeypatch.setenv("OPENCELL_CONFIG_DIR", str(tmp_path))
    monkeypatch.delenv("OPENCELL_URL", raising=False)
    monkeypatch.delenv("OPENCELL_TOKEN", raising=False)


@pytest.fixture
def patched(gateway, monkeypatch):
    transport = httpx.MockTransport(gateway.handler)
    original = OpenCellClient.__init__

    def init(self, base_url, token=None, timeout=15.0, transport_=None, **kw):
        original(self, base_url, token, timeout, transport)

    monkeypatch.setattr(OpenCellClient, "__init__", init)
    return gateway


def test_pair_saves_token(patched):
    res = runner.invoke(cli.app, ["pair", "phone", "--code", "123 456", "--name", "laptop"])
    assert res.exit_code == 0, res.output
    saved = config.load()
    assert saved.url == "http://phone:8787"
    assert saved.token == "oc_test"


def test_pair_wrong_code_fails(patched):
    res = runner.invoke(cli.app, ["pair", "phone", "--code", "000000"])
    assert res.exit_code == 1
    assert config.load() is None


def test_commands_need_pairing():
    res = runner.invoke(cli.app, ["sms", "list"])
    assert res.exit_code != 0
    assert "not paired" in res.output


def test_sms_send_and_list(patched):
    config.save(config.Config("http://phone:8787", "oc_test"))
    res = runner.invoke(cli.app, ["sms", "send", "+15550001111", "hello"])
    assert res.exit_code == 0, res.output
    assert "waiting for approval" in res.output
    res = runner.invoke(cli.app, ["sms", "list", "--json"])
    assert json.loads(res.output)[0]["sid"] == "SM1"


def test_call_commands(patched):
    config.save(config.Config("http://phone:8787", "oc_test"))
    assert "Riya" in runner.invoke(cli.app, ["call", "list"]).output
    assert "CA0" in runner.invoke(cli.app, ["call", "active"]).output
    assert "done" in runner.invoke(cli.app, ["call", "hangup", "CA0"]).output
    assert "done" in runner.invoke(cli.app, ["call", "place", "+1555"]).output


def test_env_overrides_config(monkeypatch):
    monkeypatch.setenv("OPENCELL_URL", "http://x:1")
    monkeypatch.setenv("OPENCELL_TOKEN", "t")
    assert config.load() == config.Config("http://x:1", "t")
