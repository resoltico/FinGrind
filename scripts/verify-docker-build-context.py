#!/usr/bin/env python3
"""Verify the staged Docker build context against its generated manifest."""

from __future__ import annotations

import argparse
import hashlib
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
    parser.add_argument(
        "--source-root",
        type=Path,
        help="Repository root or copied source root used to verify that the staged context matches current inputs.",
    )
    return parser.parse_args()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def compute_source_fingerprint(source_root: Path, source_files: list[str]) -> str:
    digest = hashlib.sha3_256()
    for relative_name in source_files:
        relative_path = Path(relative_name)
        require(
            not relative_path.is_absolute(),
            f"Docker source file entry must be relative: {relative_name}",
        )
        require(
            ".." not in relative_path.parts,
            f"Docker source file entry must stay inside the source root: {relative_name}",
        )
        absolute_path = source_root / relative_path
        require(
            absolute_path.is_file(),
            f"Docker build-context source fingerprint listed missing file {absolute_path}",
        )
        digest.update(relative_name.encode("utf-8"))
        digest.update(b"\0")
        digest.update(absolute_path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def main() -> int:
    args = parse_args()
    context_dir = args.context_dir.resolve()
    source_root = args.source_root.resolve() if args.source_root else None
    manifest_path = context_dir / "docker-build-context-manifest.json"
    require(manifest_path.is_file(), f"missing Docker build-context manifest at {manifest_path}")

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(isinstance(manifest, dict), "Docker build-context manifest must be a JSON object")
    require(
        manifest.get("formatVersion") == 2,
        "Docker build-context manifest must declare formatVersion 2",
    )
    files = manifest.get("files")
    require(
        isinstance(files, list) and files,
        "Docker build-context manifest must declare one non-empty files array",
    )

    for entry in files:
        require(
            isinstance(entry, str) and entry.strip(),
            "Docker build-context file entries must be non-blank strings",
        )
        normalized = Path(entry)
        require(
            not normalized.is_absolute(),
            f"Docker build-context file entry must be relative: {entry}",
        )
        require(
            ".." not in normalized.parts,
            f"Docker build-context file entry must stay inside the context root: {entry}",
        )
        require(
            (context_dir / normalized).is_file(),
            f"Docker build-context manifest listed missing file {(context_dir / normalized)}",
        )

    source_fingerprint = manifest.get("sourceFingerprintSha3")
    source_files = manifest.get("sourceFiles")
    require(
        isinstance(source_fingerprint, str) and source_fingerprint.strip(),
        "Docker build-context manifest must declare one non-blank sourceFingerprintSha3 string",
    )
    require(
        isinstance(source_files, list) and source_files,
        "Docker build-context manifest must declare one non-empty sourceFiles array",
    )
    for entry in source_files:
        require(
            isinstance(entry, str) and entry.strip(),
            "Docker source file entries must be non-blank strings",
        )

    if source_root is not None:
        actual_source_fingerprint = compute_source_fingerprint(source_root, source_files)
        require(
            actual_source_fingerprint == source_fingerprint,
            "staged Docker build context is stale relative to the current source inputs; rerun "
            "./gradlew :cli:stageDockerBuildContext before docker build",
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
