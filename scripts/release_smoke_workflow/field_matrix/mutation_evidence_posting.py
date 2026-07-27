"""Posting-response and durable-posting evidence for the field matrix."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure
from ..support import parse_json_output, require, require_labeled_text_value
from .mutation_evidence_support import (
    _require_attestation_commit,
    _required_labeled_text_value,
    _required_text,
)


@dataclass(frozen=True)
class CommittedPostingEvidence:
    """Response-owned facts needed to prove one durable posting mutation."""

    posting_id: str
    idempotency_key: str
    effective_date: str
    entry_kind: str


def assert_committed_posting_response(
    config: ReleaseSmokeConfig,
    operation_id: str,
    request: Mapping[str, object],
    output_mode: str,
    output: str,
    expected_head: VerifiedAttestationHead,
    purpose: str,
) -> CommittedPostingEvidence:
    """Prove a concrete posting response belongs to its requested operation.

    The JSON and text contracts have different shapes, but both must identify
    the created posting, the request idempotency key, the selected effective
    date, and the exact attestation head observed immediately after the write.
    """
    expected = _expected_request_facts(request, config, operation_id, purpose)
    if output_mode == "json":
        return _assert_json_committed_posting(
            config,
            operation_id,
            output,
            expected,
            expected_head,
            purpose,
        )
    if output_mode == "text":
        return _assert_text_committed_posting(
            config,
            operation_id,
            output,
            expected,
            expected_head,
            purpose,
        )
    raise ReleaseSmokeFailure(
        f"{config.label} {purpose} {operation_id} advertised unsupported posting mode {output_mode}"
    )


def assert_persisted_posting_state(
    posting: Mapping[str, Any],
    expected: CommittedPostingEvidence,
    *,
    purpose: str,
) -> None:
    """Prove the query-visible durable posting is the mutation's own result."""
    entry = posting.get("entry")
    require(
        posting.get("postingId") == expected.posting_id
        and posting.get("idempotencyKey") == expected.idempotency_key
        and posting.get("effectiveDate") == expected.effective_date
        and isinstance(entry, Mapping)
        and entry.get("entryKind") == expected.entry_kind,
        f"{purpose} did not persist the response's requested posting identity and entry kind",
    )


def _assert_json_committed_posting(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output: str,
    expected: CommittedPostingEvidence,
    expected_head: VerifiedAttestationHead,
    purpose: str,
) -> CommittedPostingEvidence:
    envelope = parse_json_output(
        output,
        f"{config.label} {purpose} {operation_id}[json] did not emit valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} {purpose} {operation_id}[json] did not report ok status",
    )
    payload = envelope.get("payload")
    require(
        isinstance(payload, Mapping),
        f"{config.label} {purpose} {operation_id}[json] did not expose a posting payload",
    )
    if not isinstance(payload, Mapping):
        raise TypeError("require must reject a non-object committed-posting payload")
    posting_id = _required_text(payload, "postingId", config, operation_id, purpose, "json")
    require(
        payload.get("idempotencyKey") == expected.idempotency_key
        and payload.get("effectiveDate") == expected.effective_date
        and payload.get("idempotentReplay") is False,
        f"{config.label} {purpose} {operation_id}[json] did not identify the newly committed "
        "request posting",
    )
    _require_attestation_commit(payload, expected_head, config, operation_id, purpose, "json")
    return CommittedPostingEvidence(
        posting_id,
        expected.idempotency_key,
        expected.effective_date,
        expected.entry_kind,
    )


def _assert_text_committed_posting(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output: str,
    expected: CommittedPostingEvidence,
    expected_head: VerifiedAttestationHead,
    purpose: str,
) -> CommittedPostingEvidence:
    first_line = next((line.strip() for line in output.splitlines() if line.strip()), "")
    require(
        first_line == "Entry Committed",
        f"{config.label} {purpose} {operation_id}[text] did not emit the committed-entry "
        "response title",
    )
    posting_id = _required_labeled_text_value(
        output,
        "Posting id",
        config,
        operation_id,
        purpose,
    )
    require_labeled_text_value(
        output,
        "Idempotency key",
        expected.idempotency_key,
        f"{config.label} {purpose} {operation_id}[text] did not retain the request idempotency key",
    )
    require_labeled_text_value(
        output,
        "Effective date",
        expected.effective_date,
        f"{config.label} {purpose} {operation_id}[text] did not retain the requested "
        "effective date",
    )
    require_labeled_text_value(
        output,
        "Idempotent replay",
        "No",
        f"{config.label} {purpose} {operation_id}[text] reported a replay instead of a new posting",
    )
    require_labeled_text_value(
        output,
        "Attestation order",
        expected_head.operation_order,
        f"{config.label} {purpose} {operation_id}[text] did not publish its verified newly "
        "appended attestation order",
    )
    require_labeled_text_value(
        output,
        "Attestation head",
        expected_head.operation_head,
        f"{config.label} {purpose} {operation_id}[text] did not publish its verified newly "
        "appended attestation head",
    )
    return CommittedPostingEvidence(
        posting_id,
        expected.idempotency_key,
        expected.effective_date,
        expected.entry_kind,
    )


def _expected_request_facts(
    request: Mapping[str, object],
    config: ReleaseSmokeConfig,
    operation_id: str,
    purpose: str,
) -> CommittedPostingEvidence:
    provenance = request.get("provenance")
    require(
        isinstance(provenance, Mapping),
        f"{config.label} {purpose} {operation_id} did not retain request provenance for proof",
    )
    if not isinstance(provenance, Mapping):
        raise TypeError("posting evidence requires provenance object")
    return CommittedPostingEvidence(
        posting_id="",
        idempotency_key=_required_text(
            provenance, "idempotencyKey", config, operation_id, purpose, "request"
        ),
        effective_date=_required_text(
            request, "effectiveDate", config, operation_id, purpose, "request"
        ),
        entry_kind=_required_text(request, "entryKind", config, operation_id, purpose, "request"),
    )
