"""Capability-matrix orchestration for administrative and lifecycle mutations.

Each focused workflow creates independent protected books for the modes it owns.
This root owns only cross-workflow routing completeness and invocation order.
"""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig
from ..support import require
from .administrative_account_tax_workflow import _verify_account_and_tax_modes
from .administrative_bootstrap_workflow import _verify_bootstrap_modes
from .administrative_constants import _ADMINISTRATIVE_DOMAINS, _ADMINISTRATIVE_OPERATION_IDS
from .administrative_maintenance_workflow import _verify_maintenance_modes
from .administrative_period_workflow import _verify_period_close_modes
from .administrative_posting_plan_workflow import _verify_posting_and_plan_modes
from .administrative_registry_workflow import _verify_attestation_registry_modes
from .capabilities import CapabilityMatrix, OperationCapability
from .scenario_matrix import SCENARIO_MATRIX


def verify_administrative_matrix(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
    matrix: CapabilityMatrix,
) -> None:
    """Exercise every administrative/lifecycle operation advertised by the live launcher."""
    print(f"{config.label}: verifying administrative and lifecycle capability modes")
    operations = _administrative_operations(matrix)
    _verify_bootstrap_modes(config, operation_ids, operations)
    _verify_account_and_tax_modes(config, operation_ids, operations)
    _verify_attestation_registry_modes(config, operation_ids, operations)
    _verify_posting_and_plan_modes(config, operation_ids, operations)
    _verify_period_close_modes(config, operation_ids, operations, matrix)
    _verify_maintenance_modes(config, operation_ids, operations)


def _administrative_operations(matrix: CapabilityMatrix) -> dict[str, OperationCapability]:
    assert_administrative_scenario_contract()
    return {
        operation_id: matrix.operation(operation_id)
        for operation_id in sorted(_ADMINISTRATIVE_OPERATION_IDS)
    }


def assert_administrative_scenario_contract() -> None:
    """Fail closed if lifecycle scenario ownership drifts from the shared matrix."""
    routed_operation_ids = {
        operation_id
        for operation_id, binding in SCENARIO_MATRIX.items()
        if binding.domain in _ADMINISTRATIVE_DOMAINS
    }
    missing_operations = sorted(routed_operation_ids - _ADMINISTRATIVE_OPERATION_IDS)
    stale_operations = sorted(_ADMINISTRATIVE_OPERATION_IDS - routed_operation_ids)
    require(
        not missing_operations and not stale_operations,
        _administrative_routing_mismatch_message(missing_operations, stale_operations),
    )


def _administrative_routing_mismatch_message(
    missing_operations: list[str], stale_operations: list[str]
) -> str:
    parts = ["administrative matrix routing differs from ScenarioDomain ownership"]
    if missing_operations:
        parts.append("missing administrative scenarios: " + ", ".join(missing_operations))
    if stale_operations:
        parts.append("stale administrative scenarios: " + ", ".join(stale_operations))
    return "; ".join(parts)
