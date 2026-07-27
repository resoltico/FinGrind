"""Filesystem-bound request and artifact handling for administrative worlds."""

from __future__ import annotations

import json
import os
import re
import stat
from pathlib import Path

from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from ..support import require
from .administrative_constants import _BOOK_KEY_TEXT
from .administrative_models import AdministrativeWorld, JsonObject


def _write_request(world: AdministrativeWorld, label: str, request: JsonObject) -> SmokePath:
    normalized_label = re.sub(r"[^a-z0-9]+", "-", label.lower()).strip("-")
    path = _world_path(world, world.request_directory / (normalized_label + ".json"))
    path.local_path.write_text(json.dumps(request, indent=2) + "\n", encoding="utf-8")
    return path


def _world_path(world: AdministrativeWorld, local_path: Path) -> SmokePath:
    return _path_from_anchor(world.path_anchor_config, local_path)


def _path_from_anchor(config: ReleaseSmokeConfig, local_path: Path) -> SmokePath:
    anchor_parent = config.book.local_path.parent
    try:
        relative_tail = local_path.relative_to(anchor_parent)
    except ValueError as exc:
        raise ReleaseSmokeFailure(
            f"administrative matrix path escaped the release-smoke work root: {local_path}"
        ) from exc
    relative_path = config.book.relative_path.parent / relative_tail
    argument = (
        str(local_path)
        if config.book.argument == str(config.book.local_path)
        else relative_path.as_posix()
    )
    return SmokePath(relative_path=relative_path, local_path=local_path, argument=argument)


def _validate_book_key_file(path: Path, config: ReleaseSmokeConfig, label: str) -> None:
    _require_nonempty_file(path, config, label)
    text = path.read_text(encoding="utf-8").strip()
    require(
        _BOOK_KEY_TEXT.fullmatch(text) is not None,
        f"{config.label} {label} was not one URL-safe key token",
    )
    if config.book_key_output_permissions == "0600" and os.name == "posix":
        require(
            stat.S_IMODE(path.stat().st_mode) == 0o600,
            f"{config.label} {label} did not have 0600 permissions",
        )


def _validate_attestation_key_file(path: Path, config: ReleaseSmokeConfig, label: str) -> None:
    _require_nonempty_file(path, config, label)
    require(
        path.read_bytes().startswith(b"FGATK"),
        f"{config.label} {label} did not carry the FGATK encrypted-key header",
    )


def _require_nonempty_file(path: Path, config: ReleaseSmokeConfig, label: str) -> None:
    require(
        path.is_file() and bool(path.read_bytes()),
        f"{config.label} {label} did not create a non-empty file at {path}",
    )


def _require_absent(path: Path, config: ReleaseSmokeConfig, label: str) -> None:
    require(
        not path.exists(),
        f"{config.label} {label} target already exists: {path}",
    )


def _remove_source_book_for_standalone_restore(world: AdministrativeWorld) -> None:
    """Remove only this fresh world's source inputs before restoring its backup."""
    for source_path, label in (
        (world.config.book.local_path, "source protected book"),
        (world.config.book_key.local_path, "source book key"),
    ):
        try:
            source_path.relative_to(world.root)
        except ValueError as exc:
            raise ReleaseSmokeFailure(
                f"{world.config.label} {label} escaped its fresh administrative world: {source_path}"
            ) from exc
        require(
            source_path.is_file() and not source_path.is_symlink(),
            f"{world.config.label} {label} was not a regular fresh-world file: {source_path}",
        )
        source_path.unlink()
        require(
            not source_path.exists(),
            f"{world.config.label} could not remove {label} before standalone restore",
        )
