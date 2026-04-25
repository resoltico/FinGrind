#!/usr/bin/env python3
"""Verify that public source archives do not include repo-owned agent metadata."""

from __future__ import annotations

import argparse
import pathlib
import sys
import tarfile
import zipfile


FORBIDDEN_EXACT = {"AGENTS.md", ".codex"}
FORBIDDEN_SEGMENTS = ("/AGENTS.md", "/.codex", "/.codex/")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Verify that one or more GitHub source archives exclude repo-owned agent metadata."
        )
    )
    parser.add_argument("archives", nargs="+", help="Archive paths to inspect.")
    return parser.parse_args()


def archive_entries(archive_path: pathlib.Path) -> list[str]:
    if zipfile.is_zipfile(archive_path):
        with zipfile.ZipFile(archive_path) as archive:
            return archive.namelist()
    try:
        with tarfile.open(archive_path, "r:*") as archive:
            return archive.getnames()
    except tarfile.TarError as exc:
        raise SystemExit(f"unsupported archive format: {archive_path}") from exc


def is_forbidden(entry_name: str) -> bool:
    normalized = entry_name.rstrip("/")
    return normalized in FORBIDDEN_EXACT or any(
        segment in normalized for segment in FORBIDDEN_SEGMENTS
    )


def verify_archive(archive_path: pathlib.Path) -> None:
    for entry_name in archive_entries(archive_path):
        if is_forbidden(entry_name):
            raise SystemExit(
                f"forbidden repo-owned agent metadata leaked into source archive {archive_path}: {entry_name}"
            )


def main() -> int:
    args = parse_args()
    for archive in args.archives:
        verify_archive(pathlib.Path(archive))
    print("source archive verification: success")
    return 0


if __name__ == "__main__":
    sys.exit(main())
