"""Attestation identity assertions for administrative output modes."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeConfig
from ..support import require, require_labeled_text_value, require_match


def _require_no_json_attestation_commit(
    payload: Mapping[str, object],
    config: ReleaseSmokeConfig,
    label: str,
    operation_id: str,
) -> None:
    require(
        "attestationCommit" not in payload,
        f"{config.label} {label} {operation_id} is read-only but published attestationCommit",
    )


def _require_no_text_attestation_commit(
    output: str,
    config: ReleaseSmokeConfig,
    label: str,
    operation_id: str,
) -> None:
    require(
        "Attestation order" not in output and "Attestation head" not in output,
        f"{config.label} {label} {operation_id} is read-only but published attestation identity",
    )


def _require_text_attestation(
    output: str,
    config: ReleaseSmokeConfig,
    label: str,
    operation_id: str,
    *,
    expected_head: VerifiedAttestationHead | None = None,
) -> None:
    if expected_head is not None:
        require_labeled_text_value(
            output,
            "Attestation order",
            expected_head.operation_order,
            f"{config.label} {label} {operation_id} did not publish its verified newly appended attestation order",
        )
        require_labeled_text_value(
            output,
            "Attestation head",
            expected_head.operation_head,
            f"{config.label} {label} {operation_id} did not publish its verified newly appended attestation head",
        )
        return
    require_match(
        output,
        r"Attestation order[[:space:]]*:[[:space:]]*(0|[1-9][0-9]*)",
        f"{config.label} {label} {operation_id} did not publish its newly appended attestation order",
    )
    require_match(
        output,
        r"Attestation head[[:space:]]*:[[:space:]]*[0-9a-f]{64}",
        f"{config.label} {label} {operation_id} did not publish its newly appended attestation head",
    )
