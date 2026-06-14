from __future__ import annotations

import subprocess
from pathlib import Path

from .models import ReviewedSurface
from .reviewed_surfaces import REVIEWED_SURFACES

MARKDOWN_EXCLUDED_PREFIXES = (
    ".git/",
    ".gradle/",
    "build/",
    "tmp/",
)

JSON_EXCLUDED_PREFIXES = MARKDOWN_EXCLUDED_PREFIXES
JSON_EXCLUDED_SEGMENTS = (
    "/src/test/resources/",
    "/src/fuzz/resources/",
)


def repository_markdown_files(repo_root: Path) -> list[Path]:
    tracked_files = tracked_repository_files(repo_root)
    if tracked_files is None:
        return sorted(
            path
            for path in repo_root.rglob("*.md")
            if path.is_file() and include_markdown_path(path.relative_to(repo_root))
        )
    return sorted(
        path
        for path in tracked_files
        if path.suffix == ".md" and include_markdown_path(path.relative_to(repo_root))
    )


def repository_json_files(repo_root: Path) -> list[Path]:
    tracked_files = tracked_repository_files(repo_root)
    if tracked_files is None:
        return sorted(
            path
            for path in repo_root.rglob("*.json")
            if path.is_file() and include_json_path(path.relative_to(repo_root))
        )
    return sorted(
        path
        for path in tracked_files
        if path.suffix == ".json" and include_json_path(path.relative_to(repo_root))
    )


def tracked_repository_files(repo_root: Path) -> list[Path] | None:
    result = subprocess.run(
        ["git", "-C", str(repo_root), "ls-files"],
        capture_output=True,
        check=False,
        encoding="utf-8",
    )
    if result.returncode != 0:
        return None
    return [
        repo_root / relative_path
        for relative_path in result.stdout.splitlines()
        if relative_path.strip()
    ]


def include_markdown_path(relative_path: Path) -> bool:
    path_text = relative_path.as_posix()
    return not any(path_text.startswith(prefix) for prefix in MARKDOWN_EXCLUDED_PREFIXES)


def include_json_path(relative_path: Path) -> bool:
    path_text = relative_path.as_posix()
    if any(path_text.startswith(prefix) for prefix in JSON_EXCLUDED_PREFIXES):
        return False
    return not any(segment in path_text for segment in JSON_EXCLUDED_SEGMENTS)


def reviewed_surfaces_matching(predicate) -> dict[str, ReviewedSurface]:
    return {
        relative_path: reviewed
        for relative_path, reviewed in REVIEWED_SURFACES.items()
        if predicate(Path(relative_path))
    }
