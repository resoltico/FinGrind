"""Attestation-registry administrative capability workflow."""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig
from .administrative_constants import _JSON_MODE
from .administrative_key_generation import _generate_additional_credential
from .administrative_modes import _modes_for, _supported_mode
from .administrative_request_runner import _run_request_mutation
from .administrative_requests import _enroll_key_request, _principal_id
from .administrative_world_bootstrap import _new_world
from .capabilities import OperationCapability


def _verify_attestation_registry_modes(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
    operations: Mapping[str, OperationCapability],
) -> None:
    group = (
        operations["enroll-key"],
        operations["rollover-key"],
        operations["revoke-key"],
        operations["alter-policy"],
    )
    for output_mode in _modes_for(*group):
        world = _new_world(config, operation_ids, operations, "attestation-registry", output_mode)
        enrolled_principal = _principal_id(world, "enrolled")
        enrolled_spki = _generate_additional_credential(world, operations, "enrolled")
        _run_request_mutation(
            world,
            operations["enroll-key"],
            _enroll_key_request(enrolled_principal, enrolled_spki),
            output_mode,
            "enroll-key capability mode",
        )

        rollover_principal = _principal_id(world, "rollover")
        predecessor_spki = _generate_additional_credential(world, operations, "rollover-old")
        _run_request_mutation(
            world,
            operations["enroll-key"],
            _enroll_key_request(rollover_principal, predecessor_spki),
            _supported_mode(operations["enroll-key"], _JSON_MODE),
            "prepare rollover predecessor",
        )
        replacement_spki = _generate_additional_credential(world, operations, "rollover-new")
        _run_request_mutation(
            world,
            operations["rollover-key"],
            {
                "principalId": rollover_principal,
                "credentialSpki": replacement_spki,
                "credentialPurpose": "operator",
                "predecessorCredentialSpki": predecessor_spki,
            },
            output_mode,
            "rollover-key capability mode",
        )

        revoke_principal = _principal_id(world, "revoke")
        revoke_spki = _generate_additional_credential(world, operations, "revoke")
        _run_request_mutation(
            world,
            operations["enroll-key"],
            _enroll_key_request(revoke_principal, revoke_spki),
            _supported_mode(operations["enroll-key"], _JSON_MODE),
            "prepare revocable credential",
        )
        _run_request_mutation(
            world,
            operations["revoke-key"],
            {
                "principalId": revoke_principal,
                "credentialSpki": revoke_spki,
                "reason": "administrative-lifecycle-field-matrix",
            },
            output_mode,
            "revoke-key capability mode",
        )
        _run_request_mutation(
            world,
            operations["alter-policy"],
            {
                "policyRules": [
                    {
                        "capability": "post",
                        "quorum": 2,
                    }
                ],
                "capabilityGrants": [
                    {
                        "principalId": enrolled_principal,
                        "capability": "post",
                        "state": "grant",
                    }
                ],
            },
            output_mode,
            "alter-policy capability mode",
        )
