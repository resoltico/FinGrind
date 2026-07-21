from __future__ import annotations

from typing import Any


def posting_evidence(request_prefix: str, evidence_suffix: str, document_date: str) -> dict[str, Any]:
    return {
        "sourceDocuments": [retained_source_document(request_prefix, evidence_suffix, document_date)],
        "approvals": [],
    }


def retained_source_document(
    request_prefix: str, evidence_suffix: str, document_date: str
) -> dict[str, str]:
    return {
        "sourceDocumentId": f"{request_prefix}-{evidence_suffix}-document-1",
        "sourceDocumentType": source_document_type(evidence_suffix),
        "documentDate": document_date,
    }


def posting_provenance(
    request_prefix: str,
    command_suffix: str,
    idempotency_suffix: str,
    causation_suffix: str,
) -> dict[str, str]:
    return {
        "commandId": request_prefix + "-" + command_suffix,
        "idempotencyKey": request_prefix + "-" + idempotency_suffix,
        "causationId": request_prefix + "-" + causation_suffix,
    }


def source_document_type(evidence_suffix: str) -> str:
    return {
        "sale": "cash-receipt",
        "expense": "expense-receipt",
        "transfer": "bank-deposit",
    }.get(evidence_suffix, "cash-receipt")
