"""Fail closed when a live capability catalog drifts from the Java-generated baseline."""

from __future__ import annotations

import json
from collections.abc import Mapping
from pathlib import Path
from typing import Any

from .models import ReleaseSmokeFailure
from .support import require, required_mapping

_SCHEMA_VERSION = 2
_BASELINE_RELATIVE_PATH = Path(
    "contract/src/main/resources/dev/erst/fingrind/contract/protocol/capability-baseline.json"
)


def verify_capability_baseline(
    repo_root: Path,
    label: str,
    live_capabilities: Mapping[str, Any],
) -> None:
    """Require live full-capabilities command descriptors to exactly match the protocol baseline."""
    baseline = _load_capability_baseline(repo_root)
    assert_capability_baseline_matches_live(baseline, live_capabilities, label)


def assert_capability_baseline_matches_live(
    baseline: Mapping[str, Any],
    live_capabilities: Mapping[str, Any],
    label: str,
) -> None:
    """Compare one parsed baseline document exactly with one live full-capabilities envelope."""
    expected_commands = _baseline_commands(baseline)
    live_commands = _live_commands(live_capabilities, label)
    difference = _first_difference(expected_commands, live_commands, "payload.commands")
    require(
        difference is None,
        f"{label} live capabilities command descriptors drift from the Java-generated protocol "
        f"baseline: {difference}",
    )


def _load_capability_baseline(repo_root: Path) -> dict[str, Any]:
    path = repo_root / _BASELINE_RELATIVE_PATH
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ReleaseSmokeFailure(f"missing Java-generated capability baseline: {path}") from exc
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"could not read Java-generated capability baseline: {path}"
        ) from exc
    except json.JSONDecodeError as exc:
        raise ReleaseSmokeFailure(
            f"invalid Java-generated capability baseline JSON: {path}"
        ) from exc
    if not isinstance(document, dict):
        raise ReleaseSmokeFailure(
            f"Java-generated capability baseline must be one JSON object: {path}"
        )
    return document


def _baseline_commands(baseline: Mapping[str, Any]) -> dict[str, Any]:
    require(
        set(baseline) == {"schemaVersion", "commands"},
        "Java-generated capability baseline has an unsupported top-level shape",
    )
    schema_version = baseline.get("schemaVersion")
    require(
        type(schema_version) is int and schema_version == _SCHEMA_VERSION,
        "Java-generated capability baseline has an unsupported schema version",
    )
    commands = required_mapping(dict(baseline), "commands")
    require(commands, "Java-generated capability baseline published no command descriptors")
    return commands


def _live_commands(live_capabilities: Mapping[str, Any], label: str) -> dict[str, Any]:
    require(
        live_capabilities.get("status") == "ok",
        f"{label} capabilities output did not report ok status before baseline comparison",
    )
    payload = required_mapping(dict(live_capabilities), "payload")
    require(
        payload.get("detail") == "full",
        f"{label} capability baseline comparison requires capabilities --detail full",
    )
    return required_mapping(payload, "commands")


def _first_difference(expected: object, actual: object, path: str) -> str | None:
    if type(expected) is not type(actual):
        return f"{path} has type {type(actual).__name__}; expected {type(expected).__name__}"
    if isinstance(expected, dict):
        if not isinstance(actual, dict):
            raise TypeError("matching mapping types must both be dictionaries")
        missing = [key for key in expected if key not in actual]
        if missing:
            return f"{path} is missing key {missing[0]!r}"
        extra = [key for key in actual if key not in expected]
        if extra:
            return f"{path} has unexpected key {extra[0]!r}"
        for key in expected:
            difference = _first_difference(expected[key], actual[key], f"{path}.{key}")
            if difference is not None:
                return difference
        return None
    if isinstance(expected, list):
        if not isinstance(actual, list):
            raise TypeError("matching sequence types must both be lists")
        if len(expected) != len(actual):
            return f"{path} has {len(actual)} entries; expected {len(expected)}"
        for index, expected_item in enumerate(expected):
            actual_item = actual[index]
            difference = _first_difference(expected_item, actual_item, f"{path}[{index}]")
            if difference is not None:
                return difference
        return None
    if expected != actual:
        return f"{path} is {actual!r}; expected {expected!r}"
    return None
