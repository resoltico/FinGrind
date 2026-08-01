#!/usr/bin/env python3
"""Read committed Jazzer run-target projections from the canonical run-target catalog."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="scripts/read-jazzer-topology.py",
        description="Print canonical Jazzer target keys from the committed run-target catalog.",
    )
    parser.add_argument(
        "projection",
        choices=("active-target-keys", "replayable-target-keys"),
        help="Which ordered target-key projection to print.",
    )
    parser.add_argument(
        "--topology-file",
        type=Path,
        default=None,
        help="Optional override for the committed Jazzer run-target JSON path.",
    )
    return parser.parse_args()


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise TypeError(f"{label} must be a JSON object")
    return value


def require_array(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        raise TypeError(f"{label} must be a JSON array")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str):
        raise TypeError(f"{label} must be a string")
    stripped = value.strip()
    if not stripped:
        raise ValueError(f"{label} must not be blank")
    return stripped


def require_boolean(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise TypeError(f"{label} must be a boolean")
    return value


def require_string_list(value: Any, label: str) -> list[str]:
    items = require_array(value, label)
    strings = [require_string(item, f"{label} entry") for item in items]
    if not strings:
        raise ValueError(f"{label} must not be empty")
    return strings


def topology_path(override: Path | None) -> Path:
    if override is not None:
        return override
    return (
        Path(__file__).resolve().parent.parent
        / "jazzer"
        / "src"
        / "main"
        / "resources"
        / "dev"
        / "erst"
        / "fingrind"
        / "jazzer"
        / "support"
        / "jazzer-run-targets.json"
    )


def read_run_targets(path: Path) -> list[dict[str, Any]]:
    try:
        root = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exception:
        raise ValueError(f"Missing Jazzer run-target catalog: {path}") from exception
    except json.JSONDecodeError as exception:
        raise ValueError(f"Invalid Jazzer run-target JSON: {path}") from exception

    run_targets = require_array(root, "runTargets")
    if not run_targets:
        raise ValueError("runTargets must not be empty")
    normalized_targets: list[dict[str, Any]] = []
    for index, target in enumerate(run_targets):
        target_object = require_object(target, f"runTargets[{index}]")
        normalized_targets.append(
            {
                "key": require_string(target_object.get("key"), f"runTargets[{index}].key"),
                "activeFuzzing": require_boolean(
                    target_object.get("activeFuzzing"),
                    f"runTargets[{index}].activeFuzzing",
                ),
                "harnessKeys": require_string_list(
                    target_object.get("harnessKeys"),
                    f"runTargets[{index}].harnessKeys",
                ),
            }
        )
    return normalized_targets


def project_target_keys(projection: str, run_targets: list[dict[str, Any]]) -> list[str]:
    if projection == "active-target-keys":
        return [target["key"] for target in run_targets if target["activeFuzzing"]]
    if projection == "replayable-target-keys":
        return [target["key"] for target in run_targets if len(target["harnessKeys"]) == 1]
    raise ValueError(f"Unsupported topology projection: {projection}")


def main() -> int:
    args = parse_args()
    try:
        for target_key in project_target_keys(
            args.projection,
            read_run_targets(topology_path(args.topology_file)),
        ):
            print(target_key)
    except ValueError as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
