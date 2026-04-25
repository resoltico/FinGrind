"""Helpers for reading canonical FinGrind contract values from repo-owned resources."""

from __future__ import annotations

import json
from pathlib import Path


def repository_root(script_path: str | Path) -> Path:
    return Path(script_path).resolve().parent.parent


def load_contract_values(repo_root: Path) -> dict[str, object]:
    runtime_surface = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-surface-contract.json"
    )
    public_distribution = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/public-distribution-contract.json"
    )
    operation_ids = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/operation-id-contract.json"
    )
    gradle_properties = read_properties(repo_root / "gradle.properties")
    return {
        "runtimeSurface": runtime_surface,
        "publicDistribution": {
            "supportedPublicCliBundleTargets": list(public_distribution.get("supportedPublicCliBundleTargets", [])),
            "unsupportedPublicCliOperatingSystems": list(
                public_distribution.get("unsupportedPublicCliOperatingSystems", [])
            ),
        },
        "operationIds": {
            "help": required_value(operation_ids, "HELP"),
            "capabilities": required_value(operation_ids, "CAPABILITIES"),
            "printRequestTemplate": required_value(operation_ids, "PRINT_REQUEST_TEMPLATE"),
            "printPlanTemplate": required_value(operation_ids, "PRINT_PLAN_TEMPLATE"),
        },
        "managedSqlite": {
            "requiredMinimumSqliteVersion": required_value(
                gradle_properties, "fingrindManagedSqliteVersion"
            ),
            "requiredSqlite3mcVersion": required_value(
                gradle_properties, "fingrindManagedSqlite3mcVersion"
            ),
        },
    }


def read_json(path: Path) -> dict[str, object]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"expected top-level JSON object in {path}")
    return data


def read_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" not in line:
            raise ValueError(f"invalid properties line in {path}: {raw_line}")
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def required_value(properties: dict[str, str], key: str) -> str:
    value = properties.get(key, "").strip()
    if not value:
        raise ValueError(f"missing required contract property: {key}")
    return value
