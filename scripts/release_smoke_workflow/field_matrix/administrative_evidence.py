"""Route-specific evidence dispatch for every administrative mutation family."""

from __future__ import annotations

from .. import support
from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeFailure
from . import (
    administrative_maintenance_evidence,
    administrative_period_evidence,
    administrative_plan_evidence,
    administrative_reads,
    administrative_registry_evidence,
    mutation_evidence_accounts,
    mutation_evidence_posting,
    mutation_evidence_tax,
)
from .administrative_constants import _ADMINISTRATIVE_OPERATION_IDS
from .administrative_models import AdministrativeWorld, JsonObject
from .capabilities import OperationCapability
from .scenario_matrix import SCENARIO_MATRIX, ScenarioDomain


def _assert_administrative_operation_evidence(
    world: AdministrativeWorld,
    operation: OperationCapability,
    output_mode: str,
    output: str,
    label: str,
    *,
    request: JsonObject | None,
    arguments: tuple[str, ...],
    after_head: VerifiedAttestationHead | None,
) -> None:
    """Prove this route's own response and durable state before coverage credit.

    An attestation-head transition establishes that *some* durable operation
    was appended.  These route-specific checks establish that it was the
    requested account, posting, tax registration, registry, plan, period-close,
    or maintenance operation rather than an unrelated successful response.
    """
    operation_id = operation.operation_id
    binding = SCENARIO_MATRIX[operation_id]
    is_matrix_mutation = (
        operation_id in _ADMINISTRATIVE_OPERATION_IDS
        or binding.domain == ScenarioDomain.TYPED_RECORD
    )
    if not is_matrix_mutation:
        return
    if operation_id in {"declare-account", "amend-account", "retire-account"}:
        route_request = _require_mutation_request(world, operation_id, request, label)
        verified_head = _require_mutation_head(world, operation_id, after_head, label)
        evidence = mutation_evidence_accounts.assert_account_mutation_response(
            world.config,
            operation_id,
            route_request,
            output_mode,
            output,
            verified_head,
            label,
        )
        mutation_evidence_accounts.assert_persisted_account_state(
            administrative_reads._persisted_account(world, evidence.account_code, label),
            evidence,
            purpose=f"{world.config.label} {label} {operation_id}",
        )
        return
    if operation_id == "declare-tax-registration":
        route_request = _require_mutation_request(world, operation_id, request, label)
        verified_head = _require_mutation_head(world, operation_id, after_head, label)
        evidence = mutation_evidence_tax.assert_tax_registration_mutation_response(
            world.config,
            operation_id,
            route_request,
            output_mode,
            output,
            verified_head,
            label,
        )
        mutation_evidence_tax.assert_persisted_tax_registration_state(
            administrative_reads._persisted_tax_registration(
                world, evidence.registration_id, label
            ),
            evidence,
            purpose=f"{world.config.label} {label} {operation_id}",
        )
        return
    if operation_id == "preflight-entry":
        administrative_plan_evidence._assert_preflight_entry_evidence(
            world,
            output_mode,
            output,
            _require_mutation_request(world, operation_id, request, label),
            label,
        )
        return
    if operation_id == "post-entry" or binding.domain == ScenarioDomain.TYPED_RECORD:
        route_request = _require_mutation_request(world, operation_id, request, label)
        verified_head = _require_mutation_head(world, operation_id, after_head, label)
        evidence = mutation_evidence_posting.assert_committed_posting_response(
            world.config,
            operation_id,
            route_request,
            output_mode,
            output,
            verified_head,
            label,
        )
        mutation_evidence_posting.assert_persisted_posting_state(
            administrative_reads._persisted_posting(world, evidence.posting_id, label),
            evidence,
            purpose=f"{world.config.label} {label} {operation_id}",
        )
        return
    if operation_id == "execute-plan":
        administrative_plan_evidence._assert_execute_plan_evidence(
            world,
            output_mode,
            output,
            _require_mutation_request(world, operation_id, request, label),
            _require_mutation_head(world, operation_id, after_head, label),
            label,
        )
        return
    if operation_id in {"enroll-key", "rollover-key", "revoke-key", "alter-policy"}:
        administrative_registry_evidence._assert_registry_mutation_evidence(
            world,
            operation_id,
            output_mode,
            output,
            _require_mutation_request(world, operation_id, request, label),
            _require_mutation_head(world, operation_id, after_head, label),
            label,
        )
        return
    if operation_id in {"interim-result-sweep", "fiscal-year-close"}:
        administrative_period_evidence._assert_period_close_evidence(
            world,
            operation_id,
            output_mode,
            output,
            arguments,
            _require_mutation_head(world, operation_id, after_head, label),
            label,
        )
        return
    if operation_id in {"backup-book", "restore-book", "rekey-book"}:
        administrative_maintenance_evidence._assert_maintenance_response_evidence(
            world,
            operation_id,
            output_mode,
            output,
            arguments,
            _require_mutation_head(world, operation_id, after_head, label),
            label,
        )
        return
    raise ReleaseSmokeFailure(
        f"{world.config.label} {label} lacks route-specific field-matrix evidence for "
        f"mutation {operation_id}"
    )


def _require_mutation_request(
    world: AdministrativeWorld,
    operation_id: str,
    request: JsonObject | None,
    label: str,
) -> JsonObject:
    support.require(
        request is not None,
        f"{world.config.label} {label} did not retain the request needed to prove {operation_id}",
    )
    if request is None:
        raise AssertionError("request-routed mutation proof requires its original request")
    return request


def _require_mutation_head(
    world: AdministrativeWorld,
    operation_id: str,
    after_head: VerifiedAttestationHead | None,
    label: str,
) -> VerifiedAttestationHead:
    support.require(
        after_head is not None,
        f"{world.config.label} {label} did not retain a verified post-mutation head for "
        f"{operation_id}",
    )
    if after_head is None:
        raise AssertionError("appending mutation proof requires a verified post-mutation head")
    return after_head
