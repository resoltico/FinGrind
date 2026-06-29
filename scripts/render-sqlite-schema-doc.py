#!/usr/bin/env python3
"""Render the canonical SQLite schema reference set from the source schema file."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from sqlite_schema_doc_renderer import render_documents, split_sql_statements

FRONTMATTER_PATTERN = re.compile(r"\A---\n.*?\n---\n", re.DOTALL)
FRONTMATTER_FIELD_PATTERN = re.compile(r"^([a-z_]+): \"([^\"]+)\"$", re.MULTILINE)


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Render docs/sqlite schema reference pages from the canonical SQLite schema file."
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Repository root containing docs/sqlite schema docs and sqlite/.../book_schema.sql",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--check", action="store_true", help="Fail if any rendered schema document would differ."
    )
    mode.add_argument(
        "--write", action="store_true", help="Write the rendered schema documents back in place."
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    repo_root = arguments.repo_root.resolve()
    docs_root = repo_root / "docs/sqlite"
    overview_path = docs_root / "SCHEMA_CORE.md"
    schema_path = repo_root / "sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql"

    frontmatter, version, updated = read_overview_metadata(overview_path)
    schema_text = read_schema_text(schema_path)
    statements = split_sql_statements(schema_text)
    rendered_documents = render_documents(docs_root, frontmatter, version, updated, statements)
    unexpected_generated = discover_unexpected_generated(docs_root, rendered_documents)

    if arguments.check:
        check_rendered_documents(repo_root, rendered_documents, unexpected_generated)
        return 0

    if arguments.write or has_render_drift(rendered_documents):
        write_rendered_documents(docs_root, rendered_documents, unexpected_generated)
    return 0


def read_overview_metadata(overview_path: Path) -> tuple[str, str, str]:
    if not overview_path.is_file():
        raise SystemExit(f"error: missing schema document at {overview_path}")
    existing_overview = overview_path.read_text(encoding="utf-8")
    frontmatter_match = FRONTMATTER_PATTERN.match(existing_overview)
    if frontmatter_match is None:
        raise SystemExit(f"error: {overview_path} is missing AFAD frontmatter")
    frontmatter = frontmatter_match.group(0)
    frontmatter_fields = dict(FRONTMATTER_FIELD_PATTERN.findall(frontmatter))
    version = frontmatter_fields.get("version")
    updated = frontmatter_fields.get("updated")
    if version is None or updated is None:
        raise SystemExit(f"error: {overview_path} is missing version or updated frontmatter keys")
    return frontmatter, version, updated


def read_schema_text(schema_path: Path) -> str:
    if not schema_path.is_file():
        raise SystemExit(f"error: missing canonical schema file at {schema_path}")
    return schema_path.read_text(encoding="utf-8").strip()


def discover_unexpected_generated(
    docs_root: Path, rendered_documents: dict[Path, str]
) -> set[Path]:
    existing_generated = set(docs_root.glob("SCHEMA_CORE_*.md"))
    expected_generated = {path for path in rendered_documents if path.name != "SCHEMA_CORE.md"}
    return existing_generated - expected_generated


def check_rendered_documents(
    repo_root: Path, rendered_documents: dict[Path, str], unexpected_generated: set[Path]
) -> None:
    mismatches: list[str] = []
    for path, rendered in rendered_documents.items():
        if not path.is_file():
            mismatches.append(f"missing generated schema document {path.relative_to(repo_root)}")
            continue
        if path.read_text(encoding="utf-8") != rendered:
            mismatches.append(
                f"{path.relative_to(repo_root)} is out of sync with the schema renderer"
            )
    for path in sorted(unexpected_generated):
        mismatches.append(f"unexpected generated schema document {path.relative_to(repo_root)}")
    if mismatches:
        raise SystemExit("error: " + "\n".join(mismatches))


def has_render_drift(rendered_documents: dict[Path, str]) -> bool:
    return any(
        not path.is_file() or path.read_text(encoding="utf-8") != rendered
        for path, rendered in rendered_documents.items()
    )


def write_rendered_documents(
    docs_root: Path, rendered_documents: dict[Path, str], unexpected_generated: set[Path]
) -> None:
    docs_root.mkdir(parents=True, exist_ok=True)
    for path, rendered in rendered_documents.items():
        path.write_text(rendered, encoding="utf-8")
    for path in sorted(unexpected_generated):
        path.unlink()


if __name__ == "__main__":
    sys.exit(main())
