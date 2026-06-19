from __future__ import annotations

from hashlib import sha256
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
        "capturedAt": f"{document_date}T10:15:30Z",
        "storageLocator": f"vault://release-smoke/{actor_prefix}/{evidence_suffix}/document-1",
        "contentSha256": evidence_digest(actor_prefix, evidence_suffix),
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


def evidence_digest(actor_prefix: str, evidence_suffix: str) -> str:
    return sha256(f"sha256-{actor_prefix}-{evidence_suffix}".encode("utf-8")).hexdigest()


def source_document_type(evidence_suffix: str) -> str:
    return {
        "sale": "cash-receipt",
        "expense": "expense-receipt",
        "transfer": "bank-deposit",
    }.get(evidence_suffix, "cash-receipt")
