"""Load and validate generated per-command capability-baseline fragments."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .models import ReleaseSmokeFailure
from .support import required_mapping

SCHEMA_VERSION = 3
CAPABILITY_BASELINE_RELATIVE_DIRECTORY = Path(
    "contract/src/main/resources/dev/erst/fingrind/contract/protocol/capability-baseline"
)
_INDEX_FILE_NAME = "index.json"
_COMMAND_CATEGORIES = ("discovery", "administration", "query", "write")


def load_capability_baseline(repo_root: Path) -> dict[str, Any]:
    """Load the closed generated-fragment set as one normalized command catalog."""
    directory = (repo_root / CAPABILITY_BASELINE_RELATIVE_DIRECTORY).resolve()
    index_path = directory / _INDEX_FILE_NAME
    index = _read_json(index_path, "Java-generated capability baseline index")
    if not isinstance(index, dict):
        raise ReleaseSmokeFailure(
            f"Java-generated capability baseline index must be one JSON object: {index_path}"
        )
    if set(index) != {"schemaVersion", "commandFiles"}:
        raise ReleaseSmokeFailure(
            "Java-generated capability baseline index has an unsupported top-level shape"
        )
    schema_version = index.get("schemaVersion")
    if type(schema_version) is not int or schema_version != SCHEMA_VERSION:
        raise ReleaseSmokeFailure(
            "Java-generated capability baseline index has an unsupported schema version"
        )
    command_files = required_mapping(index, "commandFiles")
    if set(command_files) != set(_COMMAND_CATEGORIES):
        raise ReleaseSmokeFailure(
            "Java-generated capability baseline index has unsupported command categories"
        )
    expected_paths = {Path(_INDEX_FILE_NAME)}
    commands: dict[str, list[dict[str, Any]]] = {}
    for category in _COMMAND_CATEGORIES:
        category_paths = command_files[category]
        if not isinstance(category_paths, list) or not category_paths:
            raise ReleaseSmokeFailure(
                f"Java-generated capability baseline index has no command files for {category}"
            )
        category_commands: list[dict[str, Any]] = []
        for path_text in category_paths:
            fragment_path = _fragment_path(directory, path_text)
            relative_path = fragment_path.relative_to(directory)
            if relative_path in expected_paths:
                raise ReleaseSmokeFailure(
                    f"Java-generated capability baseline index repeats fragment {relative_path}"
                )
            expected_paths.add(relative_path)
            category_commands.append(_load_command_fragment(fragment_path, category))
        commands[category] = category_commands
    _require_complete_fragment_set(directory, expected_paths)
    return {"schemaVersion": schema_version, "commands": commands}


def _load_command_fragment(fragment_path: Path, category: str) -> dict[str, Any]:
    fragment = _read_json(
        fragment_path,
        f"Java-generated capability baseline command fragment {fragment_path.name}",
    )
    if not isinstance(fragment, dict):
        raise ReleaseSmokeFailure(
            f"Java-generated capability baseline command fragment must be one JSON object: "
            f"{fragment_path}"
        )
    if set(fragment) != {"schemaVersion", "category", "command"}:
        raise ReleaseSmokeFailure(
            f"Java-generated capability baseline command fragment has an unsupported shape: "
            f"{fragment_path}"
        )
    if fragment.get("schemaVersion") != SCHEMA_VERSION:
        raise ReleaseSmokeFailure(
            f"Java-generated capability baseline command fragment has an unsupported schema "
            f"version: {fragment_path}"
        )
    if fragment.get("category") != category:
        raise ReleaseSmokeFailure(
            f"Java-generated capability baseline command fragment has the wrong category: "
            f"{fragment_path}"
        )
    command = fragment.get("command")
    if not isinstance(command, dict):
        raise ReleaseSmokeFailure(
            f"Java-generated capability baseline command fragment has no command object: "
            f"{fragment_path}"
        )
    return command


def _require_complete_fragment_set(directory: Path, expected_paths: set[Path]) -> None:
    actual_paths = {
        path.relative_to(directory) for path in directory.rglob("*.json") if path.is_file()
    }
    if actual_paths == expected_paths:
        return
    unexpected = sorted(str(path) for path in actual_paths - expected_paths)
    missing = sorted(str(path) for path in expected_paths - actual_paths)
    difference = (
        f"unexpected fragment {unexpected[0]}" if unexpected else f"missing fragment {missing[0]}"
    )
    raise ReleaseSmokeFailure(
        f"Java-generated capability baseline fragment set is incomplete: {difference}"
    )


def _read_json(path: Path, label: str) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ReleaseSmokeFailure(f"missing {label}: {path}") from exc
    except OSError as exc:
        raise ReleaseSmokeFailure(f"could not read {label}: {path}") from exc
    except json.JSONDecodeError as exc:
        raise ReleaseSmokeFailure(f"invalid {label} JSON: {path}") from exc


def _fragment_path(directory: Path, path_text: object) -> Path:
    if not isinstance(path_text, str) or not path_text.strip():
        raise ReleaseSmokeFailure(
            "Java-generated capability baseline index contains an invalid command fragment path"
        )
    relative_path = Path(path_text)
    if (
        relative_path.is_absolute()
        or ".." in relative_path.parts
        or relative_path.suffix != ".json"
    ):
        raise ReleaseSmokeFailure(
            f"Java-generated capability baseline index contains an unsafe command fragment path: "
            f"{path_text!r}"
        )
    fragment_path = (directory / relative_path).resolve()
    if directory not in fragment_path.parents:
        raise ReleaseSmokeFailure(
            f"Java-generated capability baseline fragment escapes its directory: {path_text!r}"
        )
    return fragment_path
