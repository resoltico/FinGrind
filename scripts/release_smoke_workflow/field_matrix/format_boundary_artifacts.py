"""Isolated protected-book artifacts used by format-boundary field tests."""

from __future__ import annotations

import hashlib
import shutil
from pathlib import Path

from ..fixtures import prepare_owner_only_directory, prepare_owner_only_file
from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from ..support import require

_FORMAT_BOUNDARY_DIRECTORY = "format-boundary"


def _copy_fresh_book(
    config: ReleaseSmokeConfig,
    boundary_name: str,
) -> tuple[SmokePath, SmokePath]:
    """Copy the just-created protected book and key into an isolated boundary directory."""
    boundary_root = config.book.local_path.parent / _FORMAT_BOUNDARY_DIRECTORY
    prepare_owner_only_directory(boundary_root)
    boundary_book = _anchored_smoke_path(config, boundary_root / f"{boundary_name}-format.sqlite")
    boundary_key = _anchored_smoke_path(config, boundary_root / f"{boundary_name}-format.key")
    for source, target, label in (
        (config.book.local_path, boundary_book.local_path, "fresh protected book"),
        (config.book_key.local_path, boundary_key.local_path, "fresh book key"),
    ):
        require(
            source.is_file() and not source.is_symlink() and not target.exists(),
            f"{config.label} could not isolate {label} for {boundary_name}-format rejection",
        )
        try:
            shutil.copy2(source, target)
        except OSError as exc:
            raise ReleaseSmokeFailure(
                f"{config.label} could not copy {label} for {boundary_name}-format rejection"
            ) from exc
    # copy2 preserves the source file descriptor on Windows rather than deriving a descriptor
    # from the already-private boundary directory. Re-apply the artifact contract to both
    # isolated copies before exercising their protected-book format rejection paths.
    prepare_owner_only_file(boundary_book.local_path)
    prepare_owner_only_file(boundary_key.local_path)
    return boundary_book, boundary_key


def _anchored_smoke_path(config: ReleaseSmokeConfig, local_path: Path) -> SmokePath:
    """Map one boundary-sidecar path back into the configured command path mode."""
    anchor_parent = config.book.local_path.parent
    try:
        relative_tail = local_path.relative_to(anchor_parent)
    except ValueError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} protected-book format boundary path escaped its protected-book directory"
        ) from exc
    relative_path = config.book.relative_path.parent / relative_tail
    argument = (
        str(local_path)
        if config.book.argument == str(config.book.local_path)
        else relative_path.as_posix()
    )
    return SmokePath(relative_path=relative_path, local_path=local_path, argument=argument)


def _file_digest(path: Path) -> str:
    """Return the byte-level digest used to prove refusal paths did not mutate an artifact."""
    try:
        with path.open("rb") as artifact:
            digest = hashlib.sha256()
            while block := artifact.read(1024 * 1024):
                digest.update(block)
            return digest.hexdigest()
    except OSError as exc:
        raise ReleaseSmokeFailure(f"Could not hash release-smoke boundary artifact {path}") from exc


def _require_file_digest(path: Path, expected_digest: str, message: str) -> None:
    """Require one boundary artifact to retain its pre-exercise bytes."""
    require(_file_digest(path) == expected_digest, message)
