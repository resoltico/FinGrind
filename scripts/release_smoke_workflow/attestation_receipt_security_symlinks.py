"""Symlink construction with release-smoke failure context."""

from __future__ import annotations

from pathlib import Path

from .models import ReleaseSmokeFailure


def make_directory_symlink(link_path: Path, target: Path, label: str) -> None:
    """Creates one directory symlink or raises the scenario-specific failure."""
    try:
        link_path.symlink_to(target, target_is_directory=True)
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"{label} could not create release-smoke directory symlink {link_path}"
        ) from exc


def make_file_symlink(link_path: Path, target: Path, label: str) -> None:
    """Creates one file symlink or raises the scenario-specific failure."""
    try:
        link_path.symlink_to(target)
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"{label} could not create release-smoke file symlink {link_path}"
        ) from exc
