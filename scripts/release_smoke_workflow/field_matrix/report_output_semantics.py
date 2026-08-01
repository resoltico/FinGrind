"""Representation-specific semantic proof for report command outputs."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ..models import ReleaseSmokeFailure
from ..support import parse_json_output, require
from .capabilities import OperationCapability
from .invocation import csv_table_rows
from .report_contexts import ReportBookContext
from .report_routes import _REPORT_CONTENT_ARRAYS, _REPORT_SUBSTANTIVE_FACT_CONTAINERS
from .report_value_semantics import (
    _assert_cash_flow_statement_semantics,
    _assert_tax_obligation_semantics,
    _contains_exact_string,
    _required_mapping,
)
from .tax_report_setup import TaxReportFact


def _assert_report_semantics(
    context: ReportBookContext,
    operation: OperationCapability,
    outputs: Mapping[str, str],
    tax_fact: TaxReportFact,
) -> None:
    for output_mode, output in outputs.items():
        _assert_report_mode_semantics(context, operation, output_mode, output, tax_fact)


def _assert_report_mode_semantics(
    context: ReportBookContext,
    operation: OperationCapability,
    output_mode: str,
    output: str,
    tax_fact: TaxReportFact,
) -> None:
    """Prove one representation retains this scenario's substantive fact."""
    operation_id = operation.operation_id
    if output_mode == "json":
        _assert_report_json_semantics(context, operation_id, output, tax_fact)
        return
    expected_token = context.expected_report_token(operation_id)
    if output_mode == "text":
        _require_report_text_identity(context, operation, output)
        substantive_text = _text_before_context(output)
        require(
            expected_token in substantive_text,
            f"{context.config.label} field-matrix {operation_id}[text] did not retain "
            f"the known scenario fact {expected_token!r} outside query context",
        )
        return
    if output_mode == "csv":
        csv_rows = csv_table_rows(
            output,
            f"{context.config.label} field-matrix {operation_id}[csv]",
        )
        _require_report_csv_identity(context, operation_id, csv_rows)
        require(
            any(expected_token == cell for row in csv_rows[1:] for cell in row),
            f"{context.config.label} field-matrix {operation_id}[csv] did not retain "
            f"the known scenario fact {expected_token!r} in a report row",
        )
        return
    raise ReleaseSmokeFailure(
        f"field-matrix report {operation_id} advertised unsupported output mode {output_mode}"
    )


def _require_report_text_identity(
    context: ReportBookContext,
    operation: OperationCapability,
    output: str,
) -> None:
    """Require the report's canonical document title at the text boundary."""
    expected_title = operation.display_label
    first_line = next((line.strip() for line in output.splitlines() if line.strip()), "")
    require(
        first_line == expected_title,
        f"{context.config.label} field-matrix {operation.operation_id}[text] did not emit its "
        f"canonical report title {expected_title!r}",
    )


def _require_report_csv_identity(
    context: ReportBookContext,
    operation_id: str,
    rows: tuple[tuple[str, ...], ...],
) -> None:
    """Bind every CSV report row to the requested report family."""
    header = rows[0]
    family_columns = tuple(
        index for index, name in enumerate(header) if name in {"family", "exportFamily"}
    )
    require(
        len(family_columns) == 1,
        f"{context.config.label} field-matrix {operation_id}[csv] did not expose exactly one "
        "report-family column",
    )
    if len(family_columns) != 1:
        raise AssertionError("require must reject an ambiguous report-family column")
    family_column = family_columns[0]
    require(
        all(row[family_column] == operation_id for row in rows[1:]),
        f"{context.config.label} field-matrix {operation_id}[csv] did not retain its report "
        "family on every data row",
    )


def _assert_report_json_semantics(
    context: ReportBookContext,
    operation_id: str,
    json_output: str,
    tax_fact: TaxReportFact,
) -> None:
    require(
        isinstance(json_output, str),
        f"{context.config.label} field-matrix {operation_id} did not advertise JSON output",
    )
    if not isinstance(json_output, str):
        raise TypeError("require must reject a missing report JSON output")
    envelope = parse_json_output(
        json_output,
        f"{context.config.label} field-matrix {operation_id}[json] did not emit valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{context.config.label} field-matrix {operation_id}[json] did not report ok status",
    )
    payload = _required_mapping(
        envelope,
        "payload",
        f"{context.config.label} field-matrix {operation_id}[json]",
    )
    require(
        payload.get("family") == operation_id,
        f"{context.config.label} field-matrix {operation_id}[json] did not retain its report family",
    )
    expected_token = context.expected_report_token(operation_id)
    if operation_id == "tax-obligation":
        _assert_tax_obligation_semantics(context, payload, tax_fact)
    else:
        content_array = _REPORT_CONTENT_ARRAYS[operation_id]
        content = payload.get(content_array)
        require(
            isinstance(content, list) and bool(content),
            f"{context.config.label} field-matrix {operation_id}[json] did not expose "
            f"substantive {content_array}",
        )
    if operation_id == "cash-flow-statement":
        _assert_cash_flow_statement_semantics(context, payload)
    _assert_substantive_json_fact(context, operation_id, payload, expected_token)


def _assert_substantive_json_fact(
    context: ReportBookContext,
    operation_id: str,
    payload: Mapping[str, Any],
    expected_token: str,
) -> None:
    containers = _REPORT_SUBSTANTIVE_FACT_CONTAINERS[operation_id]
    require(
        any(
            _contains_exact_string(payload.get(container), expected_token)
            for container in containers
        ),
        f"{context.config.label} field-matrix {operation_id}[json] did not retain "
        f"the known scenario fact {expected_token!r} in substantive report data",
    )


def _text_before_context(output: str) -> str:
    """Return report facts, excluding the canonical trailing request-context block."""
    context_marker = "\n\nContext\n-------\n"
    substantive_text, marker, _context = output.partition(context_marker)
    require(
        bool(marker),
        "field-matrix report text did not retain its canonical Context section",
    )
    return substantive_text
