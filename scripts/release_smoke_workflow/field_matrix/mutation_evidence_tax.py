"""Tax-registration mutation and durable-state evidence."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure
from ..support import parse_json_output, require, require_labeled_text_value
from .mutation_evidence_support import (
    _require_attestation_commit,
    _required_text,
    _success_payload,
)


@dataclass(frozen=True)
class TaxRegistrationMutationEvidence:
    """The durable tax-registration identity returned by its mutation route."""

    registration_id: str
    payable_account_code: str
    recoverable_account_code: str


def assert_tax_registration_mutation_response(
    config: ReleaseSmokeConfig,
    operation_id: str,
    request: Mapping[str, object],
    output_mode: str,
    output: str,
    expected_head: VerifiedAttestationHead,
    purpose: str,
) -> TaxRegistrationMutationEvidence:
    """Bind tax-registration response and state to the declaration request."""
    require(
        operation_id == "declare-tax-registration",
        f"{config.label} {purpose} has no tax-registration proof for {operation_id}",
    )
    expected_id = _required_text(
        request, "taxRegistrationId", config, operation_id, purpose, "request"
    )
    payable = _required_text(
        request, "payableAccountCode", config, operation_id, purpose, "request"
    )
    recoverable = _required_text(
        request, "recoverableAccountCode", config, operation_id, purpose, "request"
    )
    if output_mode == "json":
        envelope = parse_json_output(
            output,
            f"{config.label} {purpose} {operation_id}[json] did not emit valid JSON",
        )
        payload = _success_payload(envelope, config, operation_id, purpose, "json")
        registration = payload.get("registration")
        require(
            payload.get("outcome") == "declared"
            and isinstance(registration, Mapping)
            and registration.get("taxRegistrationId") == expected_id
            and registration.get("payableAccountCode") == payable
            and registration.get("recoverableAccountCode") == recoverable,
            f"{config.label} {purpose} {operation_id}[json] did not identify its requested "
            "tax registration",
        )
        _require_attestation_commit(payload, expected_head, config, operation_id, purpose, "json")
    elif output_mode == "text":
        first_line = next((line.strip() for line in output.splitlines() if line.strip()), "")
        require(
            first_line == "Tax Registration Declared",
            f"{config.label} {purpose} {operation_id}[text] did not emit its declaration title",
        )
        for label, expected_value in (
            ("Tax registration id", expected_id),
            ("Payable account", payable),
            ("Recoverable account", recoverable),
            ("Attestation order", expected_head.operation_order),
            ("Attestation head", expected_head.operation_head),
        ):
            require_labeled_text_value(
                output,
                label,
                expected_value,
                f"{config.label} {purpose} {operation_id}[text] did not retain {label}",
            )
    else:
        raise ReleaseSmokeFailure(
            f"{config.label} {purpose} {operation_id} advertised unsupported tax mode {output_mode}"
        )
    return TaxRegistrationMutationEvidence(expected_id, payable, recoverable)


def assert_persisted_tax_registration_state(
    registration: Mapping[str, Any],
    expected: TaxRegistrationMutationEvidence,
    *,
    purpose: str,
) -> None:
    """Require the list view to expose the declared registration's durable identity."""
    require(
        registration.get("taxRegistrationId") == expected.registration_id
        and registration.get("payableAccountCode") == expected.payable_account_code
        and registration.get("recoverableAccountCode") == expected.recoverable_account_code,
        f"{purpose} did not persist the declared tax-registration identity",
    )
