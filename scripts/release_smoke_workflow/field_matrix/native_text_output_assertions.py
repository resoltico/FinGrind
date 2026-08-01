"""Native text-document rejection of JSON and CSV fallbacks."""

from __future__ import annotations

import json
from collections.abc import Callable

from ..models import ReleaseSmokeFailure
from .capabilities import OperationCapability

CsvRows = Callable[[str, str], tuple[tuple[str, ...], ...]]


def assert_native_text_document(
    operation: OperationCapability,
    output: str,
    purpose: str,
    csv_table_rows: CsvRows,
) -> None:
    """Reject machine documents from the human-readable stdout channel."""
    try:
        json.loads(output)
    except json.JSONDecodeError:
        pass
    else:
        raise ReleaseSmokeFailure(
            f"field-matrix {purpose} {operation.operation_id}[text] emitted a JSON document"
        )
    try:
        csv_table_rows(
            output,
            f"field-matrix {purpose} {operation.operation_id}[text] CSV probe",
        )
    except ReleaseSmokeFailure:
        return
    raise ReleaseSmokeFailure(
        f"field-matrix {purpose} {operation.operation_id}[text] emitted a CSV table"
    )
