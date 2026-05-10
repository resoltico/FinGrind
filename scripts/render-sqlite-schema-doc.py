#!/usr/bin/env python3
"""Render the canonical SQLite schema reference from the source schema file."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

FRONTMATTER_PATTERN = re.compile(r"\A---\n.*?\n---\n", re.DOTALL)
TABLE_PATTERN = re.compile(r"create table if not exists ([a-z_][a-z0-9_]*)\s*\(", re.IGNORECASE)
INDEX_PATTERN = re.compile(
    r"create (?:unique )?index if not exists ([a-z_][a-z0-9_]*)\s+on\s+([a-z_][a-z0-9_]*)",
    re.IGNORECASE,
)
PRAGMA_PATTERN = re.compile(r"pragma\s+([a-z_]+)\s*=\s*([0-9]+);", re.IGNORECASE)
TABLE_CONSTRAINT_PREFIXES = ("primary", "foreign", "unique", "check", "constraint")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Render docs/sqlite/SCHEMA_CORE.md from the canonical SQLite schema file."
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Repository root containing docs/sqlite/SCHEMA_CORE.md and sqlite/.../book_schema.sql",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--check", action="store_true", help="Fail if the rendered document would differ."
    )
    mode.add_argument(
        "--write", action="store_true", help="Write the rendered document back in place."
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    repo_root = arguments.repo_root.resolve()
    document_path = repo_root / "docs/sqlite/SCHEMA_CORE.md"
    schema_path = repo_root / "sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql"

    if not document_path.is_file():
        raise SystemExit(f"error: missing schema document at {document_path}")
    if not schema_path.is_file():
        raise SystemExit(f"error: missing canonical schema file at {schema_path}")

    existing_document = document_path.read_text(encoding="utf-8")
    frontmatter_match = FRONTMATTER_PATTERN.match(existing_document)
    if frontmatter_match is None:
        raise SystemExit(f"error: {document_path} is missing AFAD frontmatter")
    frontmatter = frontmatter_match.group(0)

    schema_text = schema_path.read_text(encoding="utf-8").strip()
    statements = split_sql_statements(schema_text)
    table_statements = [
        statement for statement in statements if statement.lower().startswith("create table")
    ]
    index_statements = [statement for statement in statements if " index " in statement.lower()]
    pragma_values = {
        match.group(1).lower(): match.group(2)
        for statement in statements
        for match in [PRAGMA_PATTERN.fullmatch(statement.strip())]
        if match is not None
    }

    rendered_document = (
        frontmatter
        + "\n"
        + build_body(schema_text, table_statements, index_statements, pragma_values)
    )

    if arguments.check:
        if existing_document != rendered_document:
            raise SystemExit(
                "error: docs/sqlite/SCHEMA_CORE.md is out of sync with "
                "sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql"
            )
        return 0

    if arguments.write or existing_document != rendered_document:
        document_path.write_text(rendered_document, encoding="utf-8")
    return 0


def split_sql_statements(schema_text: str) -> list[str]:
    statements: list[str] = []
    current_lines: list[str] = []
    for line in schema_text.splitlines():
        current_lines.append(line.rstrip())
        if line.rstrip().endswith(";"):
            statements.append("\n".join(current_lines).strip())
            current_lines = []
    if current_lines:
        raise SystemExit("error: canonical schema file ended with one unterminated SQL statement")
    return statements


def build_body(
    schema_text: str,
    table_statements: list[str],
    index_statements: list[str],
    pragma_values: dict[str, str],
) -> str:
    table_sections = "\n\n".join(render_table_section(statement) for statement in table_statements)
    index_rows = "\n".join(render_index_row(statement) for statement in index_statements)
    table_names = ", ".join(f"`{table_name(statement)}`" for statement in table_statements)
    index_names = ", ".join(f"`{index_name(statement)}`" for statement in index_statements)
    application_id = pragma_values.get("application_id", "(missing)")
    user_version = pragma_values.get("user_version", "(missing)")
    return f"""# SQLite Core Schema

**Purpose**: Current durable schema for one FinGrind book file.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This document is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. Do not hand-edit the derived schema inventory below.

## Canonical SQL

```sql
{schema_text}
```

## Durable Tables

{table_sections}

## Durable Indexes

{index_rows}

## Runtime Integrity Semantics

- Initialized FinGrind books record both `book_meta.initialized_at` and `book_meta.schema_fingerprint_sha256`.
- An opened book is accepted as canonical only when `PRAGMA integrity_check` returns `ok`, `PRAGMA foreign_key_check` returns no rows, the recorded schema fingerprint matches the live canonical schema-object fingerprint, every persisted posting owns journal lines and balances to zero inside one currency bucket, and every persisted money triple decodes through the exact-money codec.
- Posting commits stage journal lines in temporary `pending_journal_line` rows and persist them only after the SQL aggregate gate proves at least two lines, at least one debit, at least one credit, exactly one currency bucket, and a zero signed minor-unit total.

## Schema Posture

- `application_id`: `{application_id}`
- `user_version`: `{user_version}`
- Canonical durable tables: {table_names}
- Canonical durable indexes: {index_names}
- There is no schema version table.
- There are no migration files.
- The current public line rejects non-matching book formats instead of upgrading them in place.
"""


def render_table_section(statement: str) -> str:
    table = table_name(statement)
    columns, constraints = parse_table_body(statement)
    column_rows = "\n".join(f"- `{name}`: `{definition}`" for name, definition in columns)
    constraint_rows = (
        "\n".join(f"- `{constraint}`" for constraint in constraints) if constraints else "- None."
    )
    return f"""### `{table}`

Columns:
{column_rows}

Table-level constraints:
{constraint_rows}"""


def table_name(statement: str) -> str:
    match = TABLE_PATTERN.match(statement)
    if match is None:
        raise SystemExit(f"error: could not parse table name from schema statement:\n{statement}")
    return match.group(1)


def parse_table_body(statement: str) -> tuple[list[tuple[str, str]], list[str]]:
    strict_marker = statement.lower().rfind(") strict;")
    if strict_marker < 0:
        raise SystemExit(
            f"error: could not find STRICT table terminator in statement:\n{statement}"
        )
    body_start = statement.index("(") + 1
    body = statement[body_start:strict_marker]
    columns: list[tuple[str, str]] = []
    constraints: list[str] = []
    for entry in split_top_level_commas(body):
        normalized = normalize_whitespace(entry)
        name, _, remainder = normalized.partition(" ")
        if name.lower() in TABLE_CONSTRAINT_PREFIXES:
            constraints.append(normalized)
        else:
            columns.append((name, remainder))
    return columns, constraints


def render_index_row(statement: str) -> str:
    name = index_name(statement)
    match = INDEX_PATTERN.match(statement)
    if match is None:
        raise SystemExit(f"error: could not parse index target from schema statement:\n{statement}")
    normalized = normalize_whitespace(statement)
    return f"- `{name}` on `{match.group(2)}`: `{normalized}`"


def index_name(statement: str) -> str:
    match = INDEX_PATTERN.match(statement)
    if match is None:
        raise SystemExit(f"error: could not parse index name from schema statement:\n{statement}")
    return match.group(1)


def split_top_level_commas(body: str) -> list[str]:
    entries: list[str] = []
    current: list[str] = []
    depth = 0
    in_single_quote = False
    index = 0
    while index < len(body):
        character = body[index]
        if character == "'":
            current.append(character)
            if in_single_quote and index + 1 < len(body) and body[index + 1] == "'":
                current.append(body[index + 1])
                index += 2
                continue
            in_single_quote = not in_single_quote
        elif not in_single_quote:
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
            elif character == "," and depth == 0:
                entries.append("".join(current).strip())
                current = []
                index += 1
                continue
            current.append(character)
        else:
            current.append(character)
        index += 1
    trailing = "".join(current).strip()
    if trailing:
        entries.append(trailing)
    return entries


def normalize_whitespace(value: str) -> str:
    return " ".join(part.strip() for part in value.strip().splitlines())


if __name__ == "__main__":
    sys.exit(main())
