"""Canonical query-matrix routing and output-identity facts."""

from __future__ import annotations

from collections.abc import Callable, Mapping

_BOOK_QUERY_ARGUMENTS: dict[str, tuple[str, ...]] = {
    "inspect-book": (),
    "verify-book": (),
    "attestation-review": (),
    "list-accounts": ("--limit", "50"),
    "list-tax-registrations": ("--limit", "50"),
    "list-postings": ("--limit", "25"),
}

_QUERY_OPERATION_IDS = frozenset(
    {
        "inspect-attestation-key-file",
        "inspect-book",
        "verify-book",
        "attestation-review",
        "export-attestation-receipt",
        "verify-receipt",
        "list-accounts",
        "list-tax-registrations",
        "get-posting",
        "list-postings",
    }
)

# Canonical first headings for the human-readable query documents.  Facts such
# as a book ID or posting ID can legitimately occur on several query surfaces;
# the heading is the response-owned operation identity that prevents one
# command's text from satisfying another command's scenario.
_QUERY_TEXT_TITLES: Mapping[str, str] = {
    "inspect-attestation-key-file": "Attestation Key File",
    "inspect-book": "Book Inspection",
    "verify-book": "Book Attestation Valid",
    "attestation-review": "Attestation Review",
    "export-attestation-receipt": "Attestation Receipt Exported",
    "verify-receipt": "Attestation Receipt Valid",
    "list-accounts": "Accounts",
    "list-tax-registrations": "Tax Registrations",
    "get-posting": "Posting",
    "list-postings": "Postings",
}

_QUERY_CSV_EXPORT_FAMILIES: Mapping[str, str] = {
    "list-accounts": "accounts",
    "list-tax-registrations": "list-tax-registrations",
    "list-postings": "postings",
}

_RECEIPT_EXPORT_RESPONSE_FIELDS = frozenset(
    {"receiptFile", "bookId", "receiptAttestationAnchor", "warnings"}
)
_RECEIPT_VERIFICATION_RESPONSE_FIELDS = frozenset(
    {"receiptFile", "bookId", "receiptAttestationAnchor", "findings"}
)
_RECEIPT_ATTESTATION_ANCHOR_FIELDS = frozenset({"operationOrder", "operationHead"})
_MAX_UNSIGNED_64 = (1 << 64) - 1

ModeSemanticAssertion = Callable[[str, str], None]
