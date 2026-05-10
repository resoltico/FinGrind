#!/usr/bin/env python3
"""Verify one FinGrind SQLite runtime contract from a capabilities payload."""

from __future__ import annotations

import argparse
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--expected-runtime-distribution-key",
        choices=("directJavaRuntimeDistribution", "sourceCheckoutRuntimeDistribution"),
        required=True,
    )
    parser.add_argument(
        "--expected-runtime-provenance",
        choices=("environment-configured", "source-checkout-managed"),
        required=True,
    )
    parser.add_argument("--label", required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    document = json.load(sys.stdin)
    repo_root = pathlib.Path(__file__).resolve().parent.parent
    contract = load_contract_values(repo_root)
    runtime_surface = require_mapping(contract.get("runtimeSurface"), "runtimeSurface")
    managed_sqlite = require_mapping(contract.get("managedSqlite"), "managedSqlite")
    payload = require_mapping(document.get("payload"), "payload")
    environment = require_mapping(payload.get("environment"), "payload.environment")
    distribution = require_mapping(
        environment.get("distribution"), "payload.environment.distribution"
    )
    sqlite = require_mapping(environment.get("sqlite"), "payload.environment.sqlite")
    runtime = require_mapping(sqlite.get("runtime"), "payload.environment.sqlite.runtime")

    expected_runtime_distribution = runtime_surface.get(args.expected_runtime_distribution_key)

    checks = [
        (
            distribution.get("runtimeDistribution") == expected_runtime_distribution,
            f"{args.label} runtime distribution drifted from the canonical contract",
        ),
        (
            sqlite.get("libraryMode") == runtime_surface.get("sqliteLibraryMode"),
            f"{args.label} missing managed-only sqlite library mode",
        ),
        (
            sqlite.get("requiredMinimumSqliteVersion")
            == managed_sqlite.get("requiredMinimumSqliteVersion"),
            f"{args.label} missing required minimum SQLite version",
        ),
        (
            runtime.get("status") == "ready",
            f"{args.label} missing ready SQLite runtime status",
        ),
        (
            runtime.get("loadedSqliteVersion")
            == managed_sqlite.get("requiredMinimumSqliteVersion"),
            f"{args.label} missing loaded SQLite version",
        ),
        (
            runtime.get("loadedSqlite3mcVersion") == managed_sqlite.get("requiredSqlite3mcVersion"),
            f"{args.label} missing loaded SQLite3 Multiple Ciphers version",
        ),
        (
            sqlite.get("requiredSqliteSourceId") == managed_sqlite.get("requiredSqliteSourceId"),
            f"{args.label} missing required SQLite source id",
        ),
        (
            runtime.get("loadedSqliteSourceId") == managed_sqlite.get("requiredSqliteSourceId"),
            f"{args.label} missing loaded SQLite source id",
        ),
        (
            sqlite.get("requiredCompileOptions") == managed_sqlite.get("requiredCompileOptions"),
            f"{args.label} missing canonical SQLite compile options",
        ),
        (
            sqlite.get("forbiddenCompileOptions") == managed_sqlite.get("forbiddenCompileOptions"),
            f"{args.label} missing canonical forbidden SQLite compile options",
        ),
        (
            sqlite.get("requiresSecureMemorySupport")
            == managed_sqlite.get("requiresSecureMemorySupport"),
            f"{args.label} missing canonical SQLite3MC secure-memory requirement",
        ),
        (
            runtime.get("compileOptionsVerification") == "verified",
            f"{args.label} missing verified SQLite compile-options status",
        ),
        (
            runtime.get("runtimeProvenance") == args.expected_runtime_provenance,
            f"{args.label} missing expected SQLite runtime provenance",
        ),
        (
            isinstance(runtime.get("loadedLibraryPath"), str)
            and bool(runtime.get("loadedLibraryPath").strip()),
            f"{args.label} missing loaded SQLite library path",
        ),
    ]

    failures = [message for passed, message in checks if not passed]
    if failures:
        fail("; ".join(failures))

    print(f"{args.label}: success")


if __name__ == "__main__":
    main()
