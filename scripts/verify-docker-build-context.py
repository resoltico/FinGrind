#!/usr/bin/env python3
"""Verify the staged Docker build context against its generated manifest."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify one staged FinGrind Docker build-context directory."
    )
    parser.add_argument(
        "--context-dir",
        required=True,
        type=Path,
        help="Staged Docker build-context directory to verify.",
    )
    return parser.parse_args()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> int:
    args = parse_args()
    context_dir = args.context_dir.resolve()
    manifest_path = context_dir / "docker-build-context-manifest.json"
    require(manifest_path.is_file(), f"missing Docker build-context manifest at {manifest_path}")

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(isinstance(manifest, dict), "Docker build-context manifest must be a JSON object")
    require(manifest.get("formatVersion") == 1, "Docker build-context manifest must declare formatVersion 1")
    files = manifest.get("files")
    require(isinstance(files, list) and files, "Docker build-context manifest must declare one non-empty files array")

    for entry in files:
        require(isinstance(entry, str) and entry.strip(), "Docker build-context file entries must be non-blank strings")
        normalized = Path(entry)
        require(not normalized.is_absolute(), f"Docker build-context file entry must be relative: {entry}")
        require(".." not in normalized.parts, f"Docker build-context file entry must stay inside the context root: {entry}")
        require(
            (context_dir / normalized).is_file(),
            f"Docker build-context manifest listed missing file {(context_dir / normalized)}",
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
