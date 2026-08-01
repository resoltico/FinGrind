"""Synthetic identity checks for text and CSV query output routes."""

from __future__ import annotations

from collections.abc import Callable
from types import SimpleNamespace

from ..models import ReleaseSmokeFailure
from .query_text_contract import _require_csv_fact, _require_text_facts


def assert_query_identity_is_not_inferred_from_shared_facts() -> None:
    """Reject another query's valid-looking document when its identity is wrong."""
    config = SimpleNamespace(label="synthetic field-matrix query")
    _require_text_facts(
        config,
        "attestation-review",
        "text",
        "Attestation Review\n==================\n\nBook ID : book-1\nAttestation order : 7\n",
        "book-1",
        "7",
    )
    require_rejected(
        lambda: _require_text_facts(
            config,
            "attestation-review",
            "text",
            "Book Attestation Valid\n======================\n\nBook ID : book-1\nAttestation order : 7\n",
            "book-1",
            "7",
        ),
        "canonical query title",
        "field-matrix accepted verify-book text as attestation-review",
    )
    _require_csv_fact(
        config,
        "list-postings",
        "exportFamily,recordKind,postingId\npostings,postings,posting-1\n",
        ("exportFamily", "recordKind", "postingId"),
        "posting-1",
    )
    require_rejected(
        lambda: _require_csv_fact(
            config,
            "list-postings",
            "exportFamily,recordKind,postingId\naccounts,postings,posting-1\n",
            ("exportFamily", "recordKind", "postingId"),
            "posting-1",
        ),
        "export family on every data row",
        "field-matrix accepted another query's CSV family",
    )


def require_rejected(
    action: Callable[[], object], expected_message: str, failure_message: str
) -> None:
    """Require one identity assertion to reject an output that belongs to another route."""
    try:
        action()
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError(failure_message)
