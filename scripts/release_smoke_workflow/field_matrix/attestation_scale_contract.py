"""Canonical constants and bootstrap proof for the posting-attestation scale scenario."""

from ..models import ReleaseSmokeConfig
from .mutation_evidence_bootstrap import assert_generated_book_key_response

SCALE_DIRECTORY = "attestation-provenance-scale"
SCALE_POSTING_COUNT = 40
SCALE_PAGE_LIMIT = 7
SCALE_EFFECTIVE_DATE = "2026-04-07"
POSTING_CSV_HEADER = [
    "exportFamily",
    "rowId",
    "recordKind",
    "effectiveDate",
    "recordedAt",
    "postingId",
    "postingKind",
    "postingOriginKind",
    "reversalState",
    "reversesPostingId",
    "reversedByPostingId",
    "attestationOperationOrder",
    "attestationOperationHead",
    "currencyCode",
    "debitTotal",
    "creditTotal",
    "accountCodes",
    "sourceDocumentIds",
    "sourceDocumentTypes",
    "approvalIds",
    "approvalDecisions",
    "message",
]


def assert_scale_book_key_bootstrap(config: ReleaseSmokeConfig, output: str) -> None:
    """Require the scale world's generated key to expose transaction-only publication evidence."""
    assert_generated_book_key_response(
        config,
        "json",
        output,
        "attestation provenance scale bootstrap",
    )
