"""Ordering proof for receipt verification before matrix artifact credit."""

from __future__ import annotations

import inspect


def assert_query_matrix_verifies_before_coverage_credit() -> None:
    """Keep exact response proof and receipt verification ahead of matrix credit."""
    from .query_receipt_scenarios import _export_matrix_receipts

    source = inspect.getsource(_export_matrix_receipts)
    response_validation = source.index("assert_exported_receipt_artifact(")
    receipt_verification = source.index("_verify_exported_receipt(")
    coverage_credit = source.index("record_verified_artifact(")
    assert response_validation < receipt_verification < coverage_credit, (
        "field-matrix recorded a receipt artifact before validating its JSON response and receipt"
    )
