"""Statement classification and rendering helpers for SQLite schema docs."""

from __future__ import annotations

import re
from pathlib import Path

from sqlite_schema_doc_catalog import (
    SECTION_BY_KEY,
    SECTIONS,
    TABLE_SECTION_BY_NAME,
    TRIGGER_SECTION_BY_PREFIX,
)

TABLE_PATTERN = re.compile(r"create table if not exists ([a-z_][a-z0-9_]*)\s*\(", re.IGNORECASE)
INDEX_PATTERN = re.compile(
    r"create (?:unique )?index if not exists ([a-z_][a-z0-9_]*)\s+on\s+([a-z_][a-z0-9_]*)",
    re.IGNORECASE,
)
PRAGMA_PATTERN = re.compile(r"pragma\s+([a-z_]+)\s*=\s*([0-9]+);", re.IGNORECASE)
TRIGGER_PATTERN = re.compile(r"create trigger if not exists ([a-z_][a-z0-9_]*)", re.IGNORECASE)


def split_sql_statements(schema_text: str) -> list[str]:
    statements: list[str] = []
    current_lines: list[str] = []
    for line in schema_text.splitlines():
        current_lines.append(line.rstrip())
        if line.rstrip().endswith(";"):
            while current_lines and current_lines[0] == "":
                current_lines.pop(0)
            statements.append("\n".join(current_lines).rstrip())
            current_lines = []
    if current_lines:
        raise SystemExit("error: canonical schema file ended with one unterminated SQL statement")
    return statements


def render_documents(
    docs_root: Path,
    overview_frontmatter: str,
    version: str,
    updated: str,
    statements: list[str],
) -> dict[Path, str]:
    section_statements = partition_statements(statements)
    table_statements = [
        statement for statement in statements if statement.lower().startswith("create table")
    ]
    index_statements = [statement for statement in statements if " index " in statement.lower()]
    pragma_values = collect_pragma_values(statements)
    rendered: dict[Path, str] = {
        docs_root / "SCHEMA_CORE.md": overview_frontmatter
        + "\n"
        + build_overview_body(table_statements, index_statements, pragma_values)
    }
    for section in SECTIONS:
        sql_fragment = render_sql_fragment(section_statements[section.key])
        rendered[docs_root / section.file_name] = build_section_document(
            SECTION_BY_KEY[section.key], version, updated, sql_fragment
        )
    return rendered


def partition_statements(statements: list[str]) -> dict[str, list[str]]:
    sections = {section.key: [] for section in SECTIONS}
    active_trigger_section: str | None = None
    for statement in statements:
        lower_statement = statement.lstrip().lower()
        if lower_statement == "end;":
            if active_trigger_section is None:
                raise SystemExit("error: encountered trigger terminator without an active trigger")
            sections[active_trigger_section].append(statement)
            active_trigger_section = None
            continue
        if is_trigger_body_statement(lower_statement):
            if active_trigger_section is None:
                raise SystemExit(
                    "error: encountered trigger body statement without an active trigger:\n"
                    + statement
                )
            sections[active_trigger_section].append(statement)
            continue
        if lower_statement.startswith("pragma "):
            sections["foundation"].append(statement)
            continue
        if lower_statement.startswith("create table"):
            sections[classify_table_statement(statement)].append(statement)
            continue
        if lower_statement.startswith("create trigger"):
            active_trigger_section = classify_trigger_statement(statement)
            sections[active_trigger_section].append(statement)
            continue
        if " index " in lower_statement:
            sections["indexes-and-immutability"].append(statement)
            continue
        raise SystemExit(f"error: unclassified schema statement:\n{statement}")
    if active_trigger_section is not None:
        raise SystemExit(
            f"error: schema ended while trigger section {active_trigger_section} was still open"
        )
    for section in SECTIONS:
        if not sections[section.key]:
            raise SystemExit(f"error: schema section {section.key} rendered empty")
    return sections


def collect_pragma_values(statements: list[str]) -> dict[str, str]:
    return {
        match.group(1).lower(): match.group(2)
        for statement in statements
        for match in [PRAGMA_PATTERN.fullmatch(statement.strip())]
        if match is not None
    }


def is_trigger_body_statement(lower_statement: str) -> bool:
    return lower_statement.startswith("select raise") or lower_statement.startswith(
        "with recursive ancestors"
    )


def classify_table_statement(statement: str) -> str:
    match = TABLE_PATTERN.match(statement)
    if match is None:
        raise SystemExit(f"error: could not parse table name from statement:\n{statement}")
    table_name_value = match.group(1)
    if table_name_value not in TABLE_SECTION_BY_NAME:
        raise SystemExit(f"error: unclassified table statement:\n{statement}")
    return TABLE_SECTION_BY_NAME[table_name_value]


def classify_trigger_statement(statement: str) -> str:
    match = TRIGGER_PATTERN.match(statement)
    if match is None:
        raise SystemExit(f"error: could not parse trigger name from statement:\n{statement}")
    trigger_name = match.group(1)
    for prefix, section_key in TRIGGER_SECTION_BY_PREFIX:
        if trigger_name.startswith(prefix):
            return section_key
    raise SystemExit(f"error: unclassified trigger statement:\n{statement}")


def build_overview_body(
    table_statements: list[str],
    index_statements: list[str],
    pragma_values: dict[str, str],
) -> str:
    table_names = ", ".join(f"`{table_name(statement)}`" for statement in table_statements)
    index_names = ", ".join(f"`{index_name(statement)}`" for statement in index_statements)
    schema_map = "\n".join(
        f"- [{section.file_name}](./{section.file_name}): {section.purpose}" for section in SECTIONS
    )
    application_id = pragma_values.get("application_id", "(missing)")
    user_version = pragma_values.get("user_version", "(missing)")
    return f"""# SQLite Core Schema

**Purpose**: Current durable schema for one FinGrind book file.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This overview and its companion pages are rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. Do not hand-edit the generated schema reference set.

## Schema Map

Each companion page embeds the exact canonical SQL for one schema responsibility family, and the full set stays in source order with `book_schema.sql`.

{schema_map}

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


def build_section_document(section, version: str, updated: str, sql_fragment: str) -> str:
    return f"""---
afad: "4.0"
version: "{version}"
domain: {section.domain}
updated: "{updated}"
---

# {section.title}

**Purpose**: {section.purpose}
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: {section.coverage}

```sql
{sql_fragment}
```
"""


def render_sql_fragment(statements: list[str]) -> str:
    return "".join(
        statement
        + separator_for_sql_statements(
            statement, statements[index + 1] if index + 1 < len(statements) else None
        )
        for index, statement in enumerate(statements)
    )


def separator_for_sql_statements(statement: str, next_statement: str | None) -> str:
    if next_statement is None:
        return ""
    if sql_statement_kind(statement) == "pragma" and sql_statement_kind(next_statement) == "pragma":
        return "\n"
    if sql_statement_kind(next_statement) in {"trigger-body", "trigger-end"}:
        return "\n"
    if sql_statement_kind(statement) in {"trigger-open", "trigger-body"}:
        return "\n"
    return "\n\n"


def sql_statement_kind(statement: str) -> str:
    lower_statement = statement.lstrip().lower()
    if lower_statement.startswith("pragma "):
        return "pragma"
    if lower_statement.startswith("create trigger"):
        return "trigger-open"
    if lower_statement == "end;":
        return "trigger-end"
    if is_trigger_body_statement(lower_statement):
        return "trigger-body"
    if " index " in lower_statement:
        return "index"
    return "top-level"


def table_name(statement: str) -> str:
    match = TABLE_PATTERN.match(statement)
    if match is None:
        raise SystemExit(f"error: could not parse table name from schema statement:\n{statement}")
    return match.group(1)


def index_name(statement: str) -> str:
    match = INDEX_PATTERN.match(statement)
    if match is None:
        raise SystemExit(f"error: could not parse index name from schema statement:\n{statement}")
    return match.group(1)
