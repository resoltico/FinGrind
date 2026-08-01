"""Semantic output processing and attestation-chain credit for administrative calls."""

from __future__ import annotations

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeConfig
from ..support import parse_json_output, require
from .administrative_attestation_output import (
    _require_no_json_attestation_commit,
    _require_no_text_attestation_commit,
    _require_text_attestation,
)
from .administrative_constants import _JSON_MODE, _TEXT_MODE
from .administrative_models import JsonObject
from .administrative_response import _payload
from .capabilities import OperationCapability
from .context import record_new_attestation_append
from .scenario_matrix import SCENARIO_MATRIX


def _process_operation_output(
    operation: OperationCapability,
    output_mode: str,
    output: str,
    config: ReleaseSmokeConfig,
    label: str,
    *,
    before_head: VerifiedAttestationHead | None = None,
    after_head: VerifiedAttestationHead | None = None,
) -> JsonObject | None:
    require(
        bool(output.strip()),
        f"{config.label} {label} {operation.operation_id}[{output_mode}] emitted empty stdout",
    )
    require(
        output_mode in {_JSON_MODE, _TEXT_MODE},
        f"{config.label} {label} {operation.operation_id} advertised {output_mode} without "
        "an administrative semantic-output assertion",
    )
    binding = SCENARIO_MATRIX[operation.operation_id]
    if output_mode == _JSON_MODE:
        envelope = parse_json_output(
            output,
            f"{config.label} {label} {operation.operation_id} output was not valid JSON",
        )
        require(
            envelope.get("status") == "ok",
            f"{config.label} {label} {operation.operation_id} did not report ok status",
        )
        _payload(envelope, config, label)
        if binding.requires_new_attestation_append:
            require(
                after_head is not None,
                f"{config.label} {label} did not verify the post-mutation attestation head",
            )
            if after_head is None:
                raise AssertionError(
                    "required append evidence must have a verified post-mutation head"
                )
            record_new_attestation_append(
                operation.operation_id,
                envelope,
                before_head=before_head,
                after_head=after_head,
            )
        else:
            _require_no_json_attestation_commit(
                _payload(envelope, config, label),
                config,
                label,
                operation.operation_id,
            )
        return envelope
    if output_mode == _TEXT_MODE:
        if binding.requires_new_attestation_append:
            require(
                after_head is not None,
                f"{config.label} {label} did not verify the post-mutation attestation head",
            )
            if after_head is None:
                raise AssertionError(
                    "required text append evidence must have a verified post-mutation head"
                )
            _require_text_attestation(
                output,
                config,
                label,
                operation.operation_id,
                expected_head=after_head,
            )
        else:
            _require_no_text_attestation_commit(output, config, label, operation.operation_id)
    return None
