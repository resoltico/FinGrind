#!/usr/bin/env python3
"""Verify the managed SQLite runtime contract from a FinGrind capabilities payload."""

from __future__ import annotations

import json
import pathlib
import sys
from typing import Any

from contract_values import load_contract_values


def fail(message: str) -> None:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        fail(f"missing {label} object")
    return value


def main() -> None:
    document = json.load(sys.stdin)
    repo_root = pathlib.Path(__file__).resolve().parent.parent
    contract = load_contract_values(repo_root)
    runtime_surface = require_mapping(contract.get("runtimeSurface"), "runtimeSurface")
    managed_sqlite = require_mapping(contract.get("managedSqlite"), "managedSqlite")
    payload = require_mapping(document.get("payload"), "payload")
    environment = require_mapping(payload.get("environment"), "payload.environment")
    sqlite = require_mapping(environment.get("sqlite"), "payload.environment.sqlite")

    checks = [
        (
            sqlite.get("libraryMode") == runtime_surface.get("sqliteLibraryMode"),
            "missing managed-only sqlite library mode",
        ),
        (
            sqlite.get("requiredMinimumSqliteVersion")
            == managed_sqlite.get("requiredMinimumSqliteVersion"),
            "missing required minimum SQLite version",
        ),
        (sqlite.get("runtimeStatus") == "ready", "missing ready SQLite runtime status"),
        (
            sqlite.get("loadedSqliteVersion")
            == managed_sqlite.get("requiredMinimumSqliteVersion"),
            "missing loaded SQLite version",
        ),
        (
            sqlite.get("loadedSqlite3mcVersion")
            == managed_sqlite.get("requiredSqlite3mcVersion"),
            "missing loaded SQLite3 Multiple Ciphers version",
        ),
    ]

    failures = [message for passed, message in checks if not passed]
    if failures:
        fail("; ".join(failures))

    print("managed SQLite runtime verification: success")


if __name__ == "__main__":
    main()
