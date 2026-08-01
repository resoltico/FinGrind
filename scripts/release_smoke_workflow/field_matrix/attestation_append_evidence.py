"""Validation of the head transition that proves one mutable matrix operation appended."""

from __future__ import annotations

import re
from collections.abc import Mapping
from typing import TYPE_CHECKING, Any

from ..support import require
from .scenario_matrix import ScenarioBinding

if TYPE_CHECKING:
    from ..attestation_head_checks import VerifiedAttestationHead

_ATTESTATION_ORDER = re.compile(r"0|[1-9][0-9]*")
_ATTESTATION_HEAD = re.compile(r"[0-9a-f]{64}")
_BOOK_ID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
_ZERO_HEAD = "0" * 64


def assert_new_attestation_append(
    binding: ScenarioBinding,
    operation_id: str,
    envelope: Mapping[str, Any],
    *,
    before_head: VerifiedAttestationHead | None,
    after_head: VerifiedAttestationHead,
) -> None:
    """Prove response evidence and observed heads describe one new chain operation."""
    require(
        binding.requires_new_attestation_append,
        f"field-matrix received unexpected append evidence for non-appending {operation_id}",
    )
    payload = envelope.get("payload")
    require(
        isinstance(payload, dict),
        f"field-matrix {operation_id} append evidence did not expose a payload object",
    )
    if not isinstance(payload, dict):
        raise TypeError("require must reject an invalid append payload")
    commit = payload.get("attestationCommit")
    require(
        isinstance(commit, dict),
        f"field-matrix {operation_id} did not append a new attestation operation",
    )
    if not isinstance(commit, dict):
        raise TypeError("require must reject a null or malformed attestation commit")
    operation_order = commit.get("operationOrder")
    operation_head = commit.get("operationHead")
    require(
        isinstance(operation_order, str)
        and _ATTESTATION_ORDER.fullmatch(operation_order) is not None,
        f"field-matrix {operation_id} append evidence had an invalid operation order",
    )
    require(
        isinstance(operation_head, str) and _ATTESTATION_HEAD.fullmatch(operation_head) is not None,
        f"field-matrix {operation_id} append evidence had an invalid operation head",
    )
    (
        verified_after_book_id,
        verified_after_order,
        verified_after_head,
        verified_after_previous,
    ) = verified_head_values(after_head, operation_id, "after")
    require(
        operation_order == verified_after_order and operation_head == verified_after_head,
        f"field-matrix {operation_id} append response did not match the verified "
        "post-mutation attestation head",
    )
    if before_head is None:
        require(
            verified_after_order == "0",
            f"field-matrix {operation_id} omitted a verified pre-mutation head "
            "for a non-genesis append",
        )
        require(
            verified_after_previous == _ZERO_HEAD,
            f"field-matrix {operation_id} genesis did not retain the required zero previous head",
        )
        return
    (
        verified_before_book_id,
        verified_before_order,
        verified_before_head,
        _,
    ) = verified_head_values(before_head, operation_id, "before")
    require(
        int(verified_after_order) == int(verified_before_order) + 1,
        f"field-matrix {operation_id} append did not advance the verified "
        "attestation order by exactly one",
    )
    require(
        verified_after_head != verified_before_head,
        f"field-matrix {operation_id} append did not change the verified "
        "attestation operation head",
    )
    require(
        verified_after_book_id == verified_before_book_id,
        f"field-matrix {operation_id} append changed the verified book identity",
    )
    require(
        verified_after_previous == verified_before_head,
        f"field-matrix {operation_id} append did not retain the verified prior head as its parent",
    )


def verified_head_values(
    head: VerifiedAttestationHead,
    operation_id: str,
    position: str,
) -> tuple[str, str, str, str]:
    """Read and validate the canonical values carried by one verified head."""
    book_id = getattr(head, "book_id", None)
    operation_order = getattr(head, "operation_order", None)
    operation_head = getattr(head, "operation_head", None)
    previous_head = getattr(head, "previous_head", None)
    require(
        isinstance(book_id, str) and _BOOK_ID.fullmatch(book_id) is not None,
        f"field-matrix {operation_id} {position}-mutation verified head had an invalid book ID",
    )
    require(
        isinstance(operation_order, str)
        and _ATTESTATION_ORDER.fullmatch(operation_order) is not None,
        f"field-matrix {operation_id} {position}-mutation verified head had an invalid order",
    )
    require(
        isinstance(operation_head, str) and _ATTESTATION_HEAD.fullmatch(operation_head) is not None,
        f"field-matrix {operation_id} {position}-mutation verified head had an invalid "
        "operation head",
    )
    require(
        isinstance(previous_head, str) and _ATTESTATION_HEAD.fullmatch(previous_head) is not None,
        f"field-matrix {operation_id} {position}-mutation verified head had an invalid previous head",
    )
    if (
        not isinstance(book_id, str)
        or not isinstance(operation_order, str)
        or not isinstance(operation_head, str)
        or not isinstance(previous_head, str)
    ):
        raise TypeError("require must reject a malformed verified attestation head")
    return book_id, operation_order, operation_head, previous_head
