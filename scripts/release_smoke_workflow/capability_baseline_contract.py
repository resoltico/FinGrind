"""Executable contracts for release-smoke capability-baseline comparison."""

from __future__ import annotations

from copy import deepcopy
from typing import Any

from .capability_baseline import assert_capability_baseline_matches_live
from .models import ReleaseSmokeFailure


def assert_capability_baseline_contract() -> None:
    """Prove descriptor loss or mutation cannot pass field-matrix startup unseen."""
    baseline = _synthetic_baseline()
    assert_capability_baseline_matches_live(
        baseline,
        _live_capabilities(baseline),
        "synthetic capability baseline",
    )

    _require_drift_rejected(
        baseline,
        _changed_live_capabilities(
            baseline,
            lambda commands: commands["query"][0].__setitem__("outputModes", ["json", "text"]),
        ),
        "outputModes",
    )
    _require_drift_rejected(
        baseline,
        _changed_live_capabilities(
            baseline,
            lambda commands: commands["query"][0].__setitem__("artifactOutputs", []),
        ),
        "artifactOutputs",
    )
    _require_drift_rejected(
        baseline,
        _changed_live_capabilities(
            baseline,
            lambda commands: commands["query"][0].__setitem__(
                "displayLabel", "Incorrect report label"
            ),
        ),
        "displayLabel",
    )
    _require_drift_rejected(
        baseline,
        _changed_live_capabilities(
            baseline,
            lambda commands: commands.__setitem__("query", []),
        ),
        "payload.commands.query",
    )


def _require_drift_rejected(
    baseline: dict[str, Any], live_capabilities: dict[str, Any], expected_fragment: str
) -> None:
    try:
        assert_capability_baseline_matches_live(
            baseline,
            live_capabilities,
            "synthetic capability baseline",
        )
    except ReleaseSmokeFailure as exc:
        if expected_fragment in str(exc):
            return
        raise AssertionError(
            "capability baseline rejection did not identify the changed descriptor field"
        ) from exc
    raise AssertionError("capability baseline accepted live descriptor drift")


def _changed_live_capabilities(baseline: dict[str, Any], change: Any) -> dict[str, Any]:
    live_capabilities = _live_capabilities(baseline)
    commands = live_capabilities["payload"]["commands"]
    if not isinstance(commands, dict):
        raise TypeError("synthetic live command catalog must be one object")
    change(commands)
    return live_capabilities


def _live_capabilities(baseline: dict[str, Any]) -> dict[str, Any]:
    return {
        "status": "ok",
        "payload": {
            "detail": "full",
            "commands": deepcopy(baseline["commands"]),
        },
    }


def _synthetic_baseline() -> dict[str, Any]:
    return {
        "schemaVersion": 2,
        "commands": {
            "discovery": [
                {
                    "name": "capabilities",
                    "displayLabel": "Capabilities",
                    "aliases": [],
                    "options": ["[--output <json|text>]"],
                    "executionMode": "json-envelope",
                    "outputModes": ["json", "text"],
                    "selectableOutputDefaults": {
                        "interactiveTerminal": "text",
                        "redirectedStdout": "text",
                    },
                    "artifactOutputs": [],
                    "summary": "Print command capability descriptors.",
                }
            ],
            "query": [
                {
                    "name": "trial-balance",
                    "displayLabel": "Trial Balance",
                    "aliases": [],
                    "options": ["--book-file <path>", "[--output <json|text|csv>]"],
                    "executionMode": "json-envelope",
                    "outputModes": ["json", "text", "csv"],
                    "selectableOutputDefaults": {
                        "interactiveTerminal": "text",
                        "redirectedStdout": "text",
                    },
                    "artifactOutputs": [
                        {
                            "format": "pdf",
                            "option": "--pdf-out <path>",
                            "description": (
                                "Publishes a no-clobber PDF report from a private existing output "
                                "parent whose resolved ancestry resists non-owner substitution, while "
                                "preserving the command's selected stdout output mode."
                            ),
                        }
                    ],
                    "summary": "Render the trial balance.",
                }
            ],
        },
    }
