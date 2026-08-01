"""Executable contracts for generated capability-baseline fragment loading."""

from __future__ import annotations

import json
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Any

from .capability_baseline_loading import (
    CAPABILITY_BASELINE_RELATIVE_DIRECTORY,
    SCHEMA_VERSION,
    load_capability_baseline,
)
from .models import ReleaseSmokeFailure


def assert_capability_baseline_loading_contract() -> None:
    """Prove the loader requires a complete, category-bound fragment set."""
    with TemporaryDirectory() as temporary_directory:
        root = Path(temporary_directory)
        directory = root / CAPABILITY_BASELINE_RELATIVE_DIRECTORY
        _write_valid_baseline(directory)

        baseline = load_capability_baseline(root)

        assert baseline == {
            "schemaVersion": SCHEMA_VERSION,
            "commands": {
                "discovery": [{"name": "help"}],
                "administration": [{"name": "open-book"}],
                "query": [{"name": "inspect-book"}],
                "write": [{"name": "post-entry"}],
            },
        }
        unexpected_fragment = directory / "commands/write/unlisted.json"
        unexpected_fragment.write_text("{}", encoding="utf-8")
        _require_rejected(root, "unexpected fragment commands/write/unlisted.json")


def _write_valid_baseline(directory: Path) -> None:
    command_files: dict[str, list[str]] = {
        "discovery": ["commands/discovery/help.json"],
        "administration": ["commands/administration/open-book.json"],
        "query": ["commands/query/inspect-book.json"],
        "write": ["commands/write/post-entry.json"],
    }
    _write_json(
        directory / "index.json",
        {"schemaVersion": SCHEMA_VERSION, "commandFiles": command_files},
    )
    for category, paths in command_files.items():
        command_name = paths[0].removesuffix(".json").rsplit("/", 1)[-1]
        _write_json(
            directory / paths[0],
            {
                "schemaVersion": SCHEMA_VERSION,
                "category": category,
                "command": {"name": command_name},
            },
        )


def _write_json(path: Path, document: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(document), encoding="utf-8")


def _require_rejected(root: Path, expected_message: str) -> None:
    try:
        load_capability_baseline(root)
    except ReleaseSmokeFailure as exc:
        if expected_message in str(exc):
            return
        raise AssertionError("capability baseline loader reported the wrong rejection") from exc
    raise AssertionError("capability baseline loader accepted an unlisted fragment")
