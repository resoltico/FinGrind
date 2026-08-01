"""Shared attestation-anchor and rejection assertions for receipt response contracts."""

from __future__ import annotations

from collections.abc import Callable

from ..models import ReleaseSmokeFailure, SmokePath


def assert_receipt_response_anchor_contract(receipt_path: SmokePath, receipt_file: str) -> None:
    """Require export and verification responses to expose the same exact anchor shape."""
    from .query_receipt_semantics import (
        _exported_receipt_facts_from_payload,
        _verified_receipt_facts_from_payload,
    )

    book_id = "00000000-0000-4000-8000-000000000042"
    operation_order = "42"
    operation_head = "a" * 64
    receipt_anchor = {"operationOrder": operation_order, "operationHead": operation_head}
    export_facts = _exported_receipt_facts_from_payload(
        receipt_path,
        {
            "receiptFile": receipt_file,
            "bookId": book_id,
            "receiptAttestationAnchor": receipt_anchor,
            "warnings": [],
        },
        "synthetic receipt export response",
    )
    verify_facts = _verified_receipt_facts_from_payload(
        receipt_path,
        {
            "receiptFile": receipt_file,
            "bookId": book_id,
            "receiptAttestationAnchor": receipt_anchor,
            "findings": [],
        },
        "synthetic receipt verification response",
    )
    assert (
        export_facts.book_id == verify_facts.book_id == book_id
        and export_facts.operation_order == verify_facts.operation_order == operation_order
        and export_facts.operation_head == verify_facts.operation_head == operation_head
    )
    require_rejected(
        lambda: _verified_receipt_facts_from_payload(
            receipt_path,
            {
                "receiptFile": receipt_file,
                "bookId": book_id,
                "operationOrder": operation_order,
                "operationHead": operation_head,
                "findings": [],
            },
            "retired flat receipt verification response",
        ),
        "exact receipt response fields",
    )
    require_rejected(
        lambda: _exported_receipt_facts_from_payload(
            receipt_path,
            {
                "receiptFile": receipt_file,
                "bookId": book_id,
                "receiptAttestationAnchor": {
                    **receipt_anchor,
                    "legacyOperationOrder": operation_order,
                },
                "warnings": [],
            },
            "overwide receipt export anchor",
        ),
        "exact receipt attestation anchor",
    )
    require_rejected(
        lambda: _verified_receipt_facts_from_payload(
            receipt_path,
            {
                "receiptFile": receipt_file,
                "bookId": book_id,
                "receiptAttestationAnchor": {
                    "operationOrder": operation_order,
                    "operationHead": operation_head.upper(),
                },
                "findings": [],
            },
            "non-canonical receipt verification anchor",
        ),
        "canonical attestation head",
    )


def require_rejected(action: Callable[[], None], expected_message: str) -> None:
    """Require a receipt response validator to reject its invalid response."""
    try:
        action()
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError(f"receipt artifact contract accepted {expected_message!r}")
