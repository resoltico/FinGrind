"""Path construction and request-file writing for typed-record matrix worlds."""

from __future__ import annotations

import json
from pathlib import Path

from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from .typed_record_constants import _FIELD_MATRIX_DIRECTORY
from .typed_record_models import JsonObject, TypedRecordWorld


def _world_root(config: ReleaseSmokeConfig, scenario_id: str, output_mode: str) -> Path:
    return config.book.local_path.parent / _FIELD_MATRIX_DIRECTORY / scenario_id / output_mode


def _world_smoke_path(config: ReleaseSmokeConfig, local_path: Path) -> SmokePath:
    relative_parent = config.book.relative_path.parent
    local_parent = config.book.local_path.parent
    try:
        relative_tail = local_path.relative_to(local_parent)
    except ValueError as exc:
        raise ReleaseSmokeFailure(
            f"typed-record matrix path escaped its release-smoke work root: {local_path}"
        ) from exc
    relative_path = relative_parent / relative_tail
    argument = (
        str(local_path)
        if config.book.argument == str(config.book.local_path)
        else relative_path.as_posix()
    )
    return SmokePath(relative_path=relative_path, local_path=local_path, argument=argument)


def _request_path(world: TypedRecordWorld, file_name: str) -> SmokePath:
    # A world book lives one directory below the scenario root.  Request files
    # are siblings of that book directory, so their CLI spelling must remain
    # anchored at the original release-smoke work root rather than at the
    # replacement book path in ``world.config``.
    return _world_smoke_path(world.path_anchor_config, world.request_directory / file_name)


def _write_json(path: Path, payload: JsonObject) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
