"""Text and CSV identity contracts for query responses."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ..artifact_contracts import expected_public_artifact_path_hint
from ..models import ReleaseSmokeConfig, SmokePath
from ..support import require
from .invocation import csv_table_rows
from .query_contract_catalog import _QUERY_CSV_EXPORT_FAMILIES, _QUERY_TEXT_TITLES


def _require_csv_fact(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output: str,
    required_columns: tuple[str, ...],
    expected_fact: str,
) -> None:
    rows = csv_table_rows(
        output,
        f"{config.label} field-matrix {operation_id}[csv]",
    )
    header = rows[0]
    missing_columns = [column for column in required_columns if column not in header]
    require(
        not missing_columns,
        f"{config.label} field-matrix {operation_id}[csv] omitted response-owned columns: "
        + ", ".join(missing_columns),
    )
    export_family = _QUERY_CSV_EXPORT_FAMILIES.get(operation_id)
    require(
        export_family is not None,
        f"{config.label} field-matrix {operation_id}[csv] has no canonical CSV export family",
    )
    if export_family is None:
        raise AssertionError("CSV query identity requires a configured export family")
    export_family_index = header.index("exportFamily")
    require(
        all(row[export_family_index] == export_family for row in rows[1:]),
        f"{config.label} field-matrix {operation_id}[csv] did not retain its export family on "
        "every data row",
    )
    require(
        any(expected_fact == cell for row in rows[1:] for cell in row),
        f"{config.label} field-matrix {operation_id}[csv] did not retain the known scenario "
        f"fact {expected_fact!r} in a data row",
    )


def _require_query_family(
    payload: Mapping[str, Any],
    operation_id: str,
    purpose: str,
) -> None:
    require(
        payload.get("family") == operation_id,
        f"{purpose} did not retain its query family",
    )


def _public_path_token(path: SmokePath) -> str:
    return expected_public_artifact_path_hint(path)


def _require_text_facts(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output_mode: str,
    output: str,
    *facts: str,
) -> None:
    require(
        output_mode == "text",
        f"{config.label} field-matrix {operation_id} advertised unsupported query mode {output_mode}",
    )
    expected_title = _QUERY_TEXT_TITLES[operation_id]
    first_line = next((line.strip() for line in output.splitlines() if line.strip()), "")
    expected_heading = (
        first_line == expected_title
        if operation_id != "verify-book"
        else first_line.startswith(expected_title)
    )
    require(
        expected_heading,
        f"{config.label} field-matrix {operation_id}[text] did not emit its canonical query "
        f"title {expected_title!r}",
    )
    missing = [fact for fact in facts if fact not in output]
    require(
        not missing,
        f"{config.label} field-matrix {operation_id}[text] omitted durable query facts: "
        + ", ".join(repr(fact) for fact in missing),
    )
