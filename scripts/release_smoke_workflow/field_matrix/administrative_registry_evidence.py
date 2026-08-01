"""Route-specific registry-mutation evidence against the verified registry state."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_head_checks import VerifiedAttestationHead
from ..support import require, require_labeled_text_value
from .administrative_attestation_output import _require_text_attestation
from .administrative_constants import _JSON_MODE
from .administrative_models import AdministrativeWorld, JsonObject
from .administrative_reads import _verified_registry
from .administrative_response import (
    _request_text,
    _require_response_attestation_commit,
    _require_text_title,
    _response_payload,
)


def _assert_registry_mutation_evidence(
    world: AdministrativeWorld,
    operation_id: str,
    output_mode: str,
    output: str,
    request: JsonObject,
    expected_head: VerifiedAttestationHead,
    label: str,
) -> None:
    if output_mode == _JSON_MODE:
        payload = _response_payload(world, operation_id, output, label)
        require(
            payload.get("operationKind") == operation_id,
            f"{world.config.label} {label} {operation_id}[json] did not retain its operation identity",
        )
        _require_response_attestation_commit(
            payload, expected_head, world, operation_id, "json", label
        )
    else:
        _require_text_title(world, operation_id, output, "Attestation Registry Updated", label)
        require_labeled_text_value(
            output,
            "Operation kind",
            operation_id,
            f"{world.config.label} {label} {operation_id}[text] did not retain its operation identity",
        )
        _require_text_attestation(
            output,
            world.config,
            label,
            operation_id,
            expected_head=expected_head,
        )
    registry = _verified_registry(world, expected_head, label)
    credentials = registry.get("credentials")
    policies = registry.get("capabilityPolicies")
    grants = registry.get("principalCapabilities")
    require(
        isinstance(credentials, list) and isinstance(policies, list) and isinstance(grants, list),
        f"{world.config.label} {label} {operation_id} did not expose verified registry state",
    )
    if operation_id in {"enroll-key", "rollover-key", "revoke-key"}:
        principal_id = _request_text(request, None, "principalId", world, label)
        credential_spki = _request_text(request, None, "credentialSpki", world, label)
        matching_credentials = [
            credential
            for credential in credentials
            if isinstance(credential, Mapping)
            and credential.get("principalId") == principal_id
            and credential.get("credentialSpki") == credential_spki
        ]
        require(
            len(matching_credentials) == 1,
            f"{world.config.label} {label} {operation_id} did not persist its requested credential",
        )
        if len(matching_credentials) != 1:
            raise AssertionError("registry state proof requires one requested credential")
        if operation_id == "revoke-key":
            require(
                matching_credentials[0].get("state") == "revoked",
                f"{world.config.label} {label} revoke-key did not persist credential revocation",
            )
            return
        expected_action = "enroll" if operation_id == "enroll-key" else "rollover"
        require(
            matching_credentials[0].get("bindingAction") == expected_action
            and matching_credentials[0].get("state") == "active",
            f"{world.config.label} {label} {operation_id} did not persist its active credential "
            "binding semantics",
        )
        if operation_id == "rollover-key":
            predecessor_spki = _request_text(
                request,
                None,
                "predecessorCredentialSpki",
                world,
                label,
            )
            predecessors = [
                credential
                for credential in credentials
                if isinstance(credential, Mapping)
                and credential.get("principalId") == principal_id
                and credential.get("credentialSpki") == predecessor_spki
            ]
            require(
                len(predecessors) == 1 and predecessors[0].get("state") == "superseded",
                f"{world.config.label} {label} rollover-key did not supersede its requested "
                "predecessor credential",
            )
        return
    policy_rules = request.get("policyRules")
    capability_grants = request.get("capabilityGrants")
    require(
        isinstance(policy_rules, list) and isinstance(capability_grants, list),
        f"{world.config.label} {label} alter-policy request did not retain policy changes",
    )
    for rule in policy_rules:
        require(
            isinstance(rule, Mapping)
            and any(
                isinstance(policy, Mapping)
                and policy.get("capability") == rule.get("capability")
                and policy.get("quorum") == rule.get("quorum")
                for policy in policies
            ),
            f"{world.config.label} {label} alter-policy did not persist a requested quorum rule",
        )
    for grant in capability_grants:
        require(
            isinstance(grant, Mapping)
            and any(
                isinstance(current, Mapping)
                and current.get("principalId") == grant.get("principalId")
                and current.get("capability") == grant.get("capability")
                and current.get("eligible") is (grant.get("state") == "grant")
                for current in grants
            ),
            f"{world.config.label} {label} alter-policy did not persist a requested capability grant",
        )
