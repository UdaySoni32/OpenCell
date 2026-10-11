"""Where the CLI keeps the phone address and token. Env vars OPENCELL_URL / OPENCELL_TOKEN override the file."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Config:
    url: str
    token: str


def config_path() -> Path:
    base = os.environ.get("OPENCELL_CONFIG_DIR") or os.environ.get("XDG_CONFIG_HOME") or str(Path.home() / ".config")
    return Path(base) / "opencell" / "config.json"


def load() -> Config | None:
    env_url, env_token = os.environ.get("OPENCELL_URL"), os.environ.get("OPENCELL_TOKEN")
    if env_url and env_token:
        return Config(env_url, env_token)
    path = config_path()
    if not path.exists():
        return None
    data = json.loads(path.read_text(encoding="utf-8"))
    return Config(env_url or data["url"], env_token or data["token"])


def save(cfg: Config) -> Path:
    path = config_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"url": cfg.url, "token": cfg.token}, indent=2), encoding="utf-8")
    # The token can send SMS from your phone: keep it readable by you only (no-op on Windows).
    path.chmod(0o600)
    return path
