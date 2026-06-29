from __future__ import annotations

from typing import Any


def posting_evidence(actor_prefix: str, evidence_suffix: str, document_date: str) -> dict[str, Any]:
    return {
        "sourceDocuments": [retained_source_document(actor_prefix, evidence_suffix, document_date)],
        "approvals": [],
    }


def retained_source_document(
    actor_prefix: str, evidence_suffix: str, document_date: str
) -> dict[str, str]:
    return {
        "sourceDocumentId": f"{actor_prefix}-{evidence_suffix}-document-1",
        "sourceDocumentType": source_document_type(evidence_suffix),
        "documentDate": document_date,
    }


def posting_provenance(
    actor_prefix: str,
    command_suffix: str,
    idempotency_suffix: str,
    causation_suffix: str,
) -> dict[str, str]:
    return {
        "actorId": actor_prefix,
        "actorType": "AGENT",
        "commandId": actor_prefix + "-" + command_suffix,
        "idempotencyKey": actor_prefix + "-" + idempotency_suffix,
        "causationId": actor_prefix + "-" + causation_suffix,
    }


def source_document_type(evidence_suffix: str) -> str:
    return {
        "sale": "cash-receipt",
        "expense": "expense-receipt",
        "transfer": "bank-deposit",
    }.get(evidence_suffix, "cash-receipt")
