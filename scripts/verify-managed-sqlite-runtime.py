#!/usr/bin/env python3
"""Verify the managed SQLite runtime contract from a FinGrind capabilities payload."""

from __future__ import annotations

import json
import sys
from typing import Any


def fail(message: str) -> None:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        fail(f"missing {label} object")
    return value


def main() -> None:
    document = json.load(sys.stdin)
    payload = require_mapping(document.get("payload"), "payload")
    environment = require_mapping(payload.get("environment"), "payload.environment")
    sqlite = require_mapping(environment.get("sqlite"), "payload.environment.sqlite")

    checks = [
        (sqlite.get("libraryMode") == "managed-only", "missing managed-only sqlite library mode"),
        (
            sqlite.get("requiredMinimumSqliteVersion") == "3.53.0",
            "missing required minimum SQLite version",
        ),
        (sqlite.get("runtimeStatus") == "ready", "missing ready SQLite runtime status"),
        (sqlite.get("loadedSqliteVersion") == "3.53.0", "missing loaded SQLite version"),
        (sqlite.get("loadedSqlite3mcVersion") == "2.3.3", "missing loaded SQLite3 Multiple Ciphers version"),
    ]

    failures = [message for passed, message in checks if not passed]
    if failures:
        fail("; ".join(failures))

    print("managed SQLite runtime verification: success")


if __name__ == "__main__":
    main()
