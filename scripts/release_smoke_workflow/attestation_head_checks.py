from __future__ import annotations

import re
from dataclasses import dataclass
from uuid import UUID

from .cli import run_cli
from .models import ReleaseSmokeConfig
from .support import parse_json_output, require


@dataclass(frozen=True)
class VerifiedAttestationHead:
    """The exact verified chain position returned by the public verify-book surface."""

    book_id: str
    operation_order: str
    operation_head: str
    previous_head: str


@dataclass(frozen=True)
class AttestationCommit:
    """The identity published by a mutation response before verify-book corroboration."""

    operation_order: str
    operation_head: str


_PLAN_ATTESTATION_DISPOSITIONS = frozenset({"appended", "read-only", "no-durable-child-mutation"})


def verified_attestation_head(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    purpose: str,
) -> VerifiedAttestationHead:
    envelope = parse_json_output(
        run_cli(
            config,
            operation_ids["verifyBook"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--output",
            "json",
        ),
        f"{config.label} {purpose} verify-book output was not valid JSON",
    )
    return verified_attestation_head_from_envelope(envelope, config.label, purpose)


def verified_attestation_head_from_envelope(
    envelope: dict[str, object], label: str, purpose: str
) -> VerifiedAttestationHead:
    require(
        envelope.get("status") == "ok",
        f"{label} {purpose} verify-book did not report ok status",
    )
    payload = envelope.get("payload")
    require(
        isinstance(payload, dict),
        f"{label} {purpose} verify-book did not expose a payload object",
    )
    verified_head = payload.get("verifiedAttestationHead")
    require(
        isinstance(verified_head, dict)
        and set(verified_head) == {"operationOrder", "operationHead"},
        f"{label} {purpose} verify-book did not expose the protocol-57 verifiedAttestationHead",
    )
    if not isinstance(verified_head, dict):
        raise TypeError("require must reject a missing verified attestation head")
    return _attestation_head_from_fields(
        payload.get("bookId"),
        verified_head.get("operationOrder"),
        verified_head.get("operationHead"),
        payload.get("previousHead"),
        label,
        purpose,
        "verify-book",
    )


def attestation_commit_from_payload(
    payload: dict[str, object], label: str, purpose: str
) -> AttestationCommit:
    commit = payload.get("attestationCommit")
    require(
        isinstance(commit, dict),
        f"{label} {purpose} did not expose an attestationCommit object",
    )
    return _attestation_commit_from_fields(
        commit.get("operationOrder"),
        commit.get("operationHead"),
        label,
        purpose,
        "attestationCommit",
    )


def require_no_attestation_commit(payload: dict[str, object], label: str, purpose: str) -> None:
    require(
        "attestationCommit" in payload,
        f"{label} {purpose} did not explicitly report attestationCommit",
    )
    require(
        payload.get("attestationCommit") is None,
        f"{label} {purpose} unexpectedly reported an appended attestationCommit",
    )


def require_plan_attestation_disposition(
    payload: dict[str, object], label: str, purpose: str, expected_disposition: str
) -> None:
    """Validates one complete execute-plan append-outcome pairing."""
    require(
        expected_disposition in _PLAN_ATTESTATION_DISPOSITIONS,
        f"{label} {purpose} requested an unknown plan attestation disposition",
    )
    require(
        "attestationDisposition" in payload,
        f"{label} {purpose} did not explicitly report attestationDisposition",
    )
    require(
        payload.get("attestationDisposition") == expected_disposition,
        f"{label} {purpose} reported the wrong attestationDisposition",
    )
    if expected_disposition == "appended":
        attestation_commit_from_payload(payload, label, purpose)
        return
    require_no_attestation_commit(payload, label, purpose)


def require_attestation_commit_matches_verified_head(
    commit: AttestationCommit,
    head: VerifiedAttestationHead,
    label: str,
    purpose: str,
) -> None:
    """Bind a mutation response's reported commit to the independently verified head."""
    require(
        commit.operation_order == head.operation_order
        and commit.operation_head == head.operation_head,
        f"{label} {purpose} did not report the exact verified attestation head",
    )


def _attestation_head_from_fields(
    book_id: object,
    operation_order: object,
    operation_head: object,
    previous_head: object,
    label: str,
    purpose: str,
    source: str,
) -> VerifiedAttestationHead:
    checked_book_id = _require_canonical_book_id(book_id, label, purpose, source)
    commit = _attestation_commit_from_fields(
        operation_order, operation_head, label, purpose, source
    )
    require(
        isinstance(previous_head, str) and re.fullmatch(r"[0-9a-f]{64}", previous_head) is not None,
        f"{label} {purpose} {source} did not expose a canonical attestation previous head",
    )
    if not isinstance(previous_head, str):
        raise TypeError("require must reject a non-canonical previous head")
    return VerifiedAttestationHead(
        checked_book_id,
        commit.operation_order,
        commit.operation_head,
        previous_head,
    )


def _attestation_commit_from_fields(
    operation_order: object,
    operation_head: object,
    label: str,
    purpose: str,
    source: str,
) -> AttestationCommit:
    require(
        isinstance(operation_order, str)
        and re.fullmatch(r"0|[1-9][0-9]*", operation_order) is not None,
        f"{label} {purpose} {source} did not expose a canonical attestation order",
    )
    require(
        isinstance(operation_head, str)
        and re.fullmatch(r"[0-9a-f]{64}", operation_head) is not None,
        f"{label} {purpose} {source} did not expose a canonical attestation operation head",
    )
    if not isinstance(operation_order, str) or not isinstance(operation_head, str):
        raise TypeError("require must reject a malformed attestation commit")
    return AttestationCommit(operation_order, operation_head)


def _require_canonical_book_id(book_id: object, label: str, purpose: str, source: str) -> str:
    require(
        isinstance(book_id, str) and _is_canonical_uuid(book_id),
        f"{label} {purpose} {source} did not expose a canonical book ID",
    )
    if not isinstance(book_id, str):
        raise TypeError("require must reject a non-canonical book ID")
    return book_id


def _is_canonical_uuid(value: str) -> bool:
    try:
        return str(UUID(value)) == value
    except ValueError:
        return False
