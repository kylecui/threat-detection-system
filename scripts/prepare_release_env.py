#!/usr/bin/env python3
"""Create a validated runtime .env without printing any secret values."""

from __future__ import annotations

import argparse
import base64
import os
import re
import secrets
from datetime import datetime, timezone
from pathlib import Path

KEY_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
NAME_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
AUTO_GENERATORS = {
    "POSTGRES_PASSWORD": lambda: secrets.token_hex(24),
    "JWT_SECRET": lambda: secrets.token_hex(48),
    "TIRE_FERNET_KEY": lambda: base64.urlsafe_b64encode(os.urandom(32)).decode("ascii"),
    "TIRE_SESSION_SECRET": lambda: secrets.token_hex(48),
    "CONFIG_ENCRYPTION_KEY": lambda: base64.urlsafe_b64encode(os.urandom(32)).decode("ascii"),
    "TIRE_INTERNAL_SERVICE_TOKEN": lambda: secrets.token_hex(48),
    "ALERT_INTERNAL_SERVICE_TOKEN": lambda: secrets.token_hex(48),
    "BUILD_DATE": lambda: datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
}
REQUIRED = (
    "COMPOSE_PROJECT_NAME",
    "RELEASE_VERSION",
    "POSTGRES_DB",
    "POSTGRES_USER",
    "POSTGRES_PASSWORD",
    "JWT_SECRET",
    "TIRE_FERNET_KEY",
    "TIRE_SESSION_SECRET",
    "CONFIG_ENCRYPTION_KEY",
    "TIRE_INTERNAL_SERVICE_TOKEN",
    "ALERT_INTERNAL_SERVICE_TOKEN",
    "EMQX_WS_HOST_PORT",
)


def parse_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            raise ValueError(f"{path}:{number}: expected KEY=VALUE")
        key, value = line.split("=", 1)
        key = key.strip()
        if not KEY_RE.fullmatch(key):
            raise ValueError(f"{path}:{number}: invalid key")
        values[key] = value.strip()
    return values


def validate(values: dict[str, str]) -> None:
    for key in REQUIRED:
        value = values.get(key, "")
        if not value or value.lower() in {"auto", "change_me", "changeme"}:
            raise ValueError(f"{key} is missing or unresolved")
    if not NAME_RE.fullmatch(values["POSTGRES_DB"]):
        raise ValueError("POSTGRES_DB must contain only letters, digits, and underscore")
    if not NAME_RE.fullmatch(values["POSTGRES_USER"]):
        raise ValueError("POSTGRES_USER must contain only letters, digits, and underscore")
    if not re.fullmatch(r"[0-9A-Za-z._-]+", values["RELEASE_VERSION"]):
        raise ValueError("RELEASE_VERSION contains unsupported characters")
    if not re.fullmatch(r"[A-Za-z0-9._-]{24,}", values["POSTGRES_PASSWORD"]):
        raise ValueError("POSTGRES_PASSWORD must be at least 24 URL-safe characters")
    if len(values["JWT_SECRET"]) < 64:
        raise ValueError("JWT_SECRET must contain at least 64 characters")
    try:
        if len(base64.urlsafe_b64decode(values["TIRE_FERNET_KEY"].encode("ascii"))) != 32:
            raise ValueError
    except Exception as exc:
        raise ValueError("TIRE_FERNET_KEY must be a URL-safe Fernet key") from exc
    if len(values["TIRE_SESSION_SECRET"]) < 64:
        raise ValueError("TIRE_SESSION_SECRET must contain at least 64 characters")
    try:
        if len(base64.urlsafe_b64decode(values["CONFIG_ENCRYPTION_KEY"].encode("ascii"))) != 32:
            raise ValueError
    except Exception as exc:
        raise ValueError("CONFIG_ENCRYPTION_KEY must be a URL-safe base64 32-byte key") from exc
    if len(values["TIRE_INTERNAL_SERVICE_TOKEN"]) < 64:
        raise ValueError("TIRE_INTERNAL_SERVICE_TOKEN must contain at least 64 characters")
    if len(values["ALERT_INTERNAL_SERVICE_TOKEN"]) < 64:
        raise ValueError("ALERT_INTERNAL_SERVICE_TOKEN must contain at least 64 characters")
    try:
        emqx_ws_host_port = int(values["EMQX_WS_HOST_PORT"])
    except ValueError as exc:
        raise ValueError("EMQX_WS_HOST_PORT must be an integer") from exc
    if not 1 <= emqx_ws_host_port <= 65535:
        raise ValueError("EMQX_WS_HOST_PORT must be between 1 and 65535")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("template", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--existing", type=Path)
    args = parser.parse_args()

    template_lines = args.template.read_text(encoding="utf-8").splitlines()
    template_values = parse_env(args.template)
    existing_values = parse_env(args.existing) if args.existing else {}

    resolved: dict[str, str] = {}
    for key, default in template_values.items():
        # Build metadata must follow the newly installed release. Secrets and
        # operator-provided settings are preserved across upgrades.
        candidate = (
            default
            if key in {"BUILD_DATE", "RELEASE_VERSION"}
            else existing_values.get(key, default)
        )
        if candidate.lower() == "auto":
            generator = AUTO_GENERATORS.get(key)
            if generator is None:
                raise ValueError(f"{key} is marked auto but has no generator")
            candidate = generator()
        resolved[key] = candidate

    # Preserve forward-compatible or locally added keys on upgrade.
    for key, value in existing_values.items():
        resolved.setdefault(key, value)

    validate(resolved)

    rendered: list[str] = []
    emitted: set[str] = set()
    for raw in template_lines:
        stripped = raw.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key = stripped.split("=", 1)[0].removeprefix("export ").strip()
            rendered.append(f"{key}={resolved[key]}")
            emitted.add(key)
        else:
            rendered.append(raw)
    extras = sorted(set(resolved) - emitted)
    if extras:
        rendered.extend(["", "# Preserved installation-specific settings."])
        rendered.extend(f"{key}={resolved[key]}" for key in extras)

    args.output.write_text("\n".join(rendered).rstrip() + "\n", encoding="utf-8")
    if os.name == "posix":
        args.output.chmod(0o600)
    print("Runtime environment prepared and validated; secret values were not printed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
