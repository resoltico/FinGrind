"""Shared auditable member traversal for private PowerShell provisioning trees."""

from __future__ import annotations

import os
import stat
from collections.abc import Callable
from pathlib import Path


def require_regular_tree_members(
    tree: Path,
    description: str,
    *,
    is_link_or_reparse_point: Callable[[Path], bool],
    maximum_members: int,
    maximum_bytes: int,
    error_type: type[Exception],
) -> None:
    """Reject links, non-regular entries, hard links, and oversized private trees."""

    member_count = 0
    total_bytes = 0
    pending_directories = [tree]
    while pending_directories:
        directory = pending_directories.pop()
        try:
            entries = sorted(os.scandir(directory), key=lambda entry: entry.name)
        except OSError as error:
            raise error_type(f"could not inspect {description}: {directory}: {error}") from error
        for entry in entries:
            path = Path(entry.path)
            if is_link_or_reparse_point(path):
                raise error_type(f"{description} contains a symlink or reparse point: {path}")
            try:
                metadata = entry.stat(follow_symlinks=False)
            except OSError as error:
                raise error_type(
                    f"could not inspect {description} entry: {path}: {error}"
                ) from error
            if stat.S_ISDIR(metadata.st_mode):
                pending_directories.append(path)
                continue
            if not stat.S_ISREG(metadata.st_mode):
                raise error_type(f"{description} contains a non-regular entry: {path}")
            if metadata.st_nlink != 1:
                raise error_type(f"{description} contains a hard-linked file: {path}")
            member_count += 1
            total_bytes += metadata.st_size
            if member_count > maximum_members:
                raise error_type(f"{description} exceeds the admitted member limit: {tree}")
            if total_bytes > maximum_bytes:
                raise error_type(f"{description} exceeds the admitted byte limit: {tree}")
