"""Derive private artifact directories beneath a caller-owned release-smoke root."""

from __future__ import annotations

from pathlib import Path

from .models import ReleaseSmokeFailure


def private_workspace_ancestors(work_root: Path, directories: set[Path]) -> tuple[Path, ...]:
    """Return every private artifact ancestor below one caller-owned work root exactly once."""
    checked_work_root = Path(work_root)
    private_ancestors: set[Path] = set()
    for directory in directories:
        checked_directory = Path(directory)
        try:
            relative_directory = checked_directory.relative_to(checked_work_root)
        except ValueError as exc:
            raise ReleaseSmokeFailure(
                f"private release-smoke artifact parent escaped its work root: {checked_directory}"
            ) from exc
        current_directory = checked_work_root
        for segment in relative_directory.parts:
            current_directory = current_directory / segment
            private_ancestors.add(current_directory)
    return tuple(sorted(private_ancestors, key=lambda path: (len(path.parts), str(path))))
