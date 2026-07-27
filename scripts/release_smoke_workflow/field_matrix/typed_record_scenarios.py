"""Fresh-world acceptance scenarios for every typed business-recording command.

Each scenario builds the retained state that makes its business operation
meaningful, then exercises it once in JSON and once in text on independent
protected books.
"""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig
from ..support import require
from .report_contexts import TypedRecordMatrixWorlds
from .scenario_matrix import SCENARIO_MATRIX, ScenarioDomain
from .typed_record_accounts import _declare_supporting_accounts
from .typed_record_bootstrap import _prepare_world
from .typed_record_constants import _JSON_MODE, _STANDARD_COMMERCIAL_REPORT_TOKENS, _TEXT_MODE
from .typed_record_execution import _run_reversal_sequence, _run_typed_request
from .typed_record_models import TypedRecordRequest, TypedRecordWorld
from .typed_record_output import _operation
from .typed_record_reports import _typed_record_report_worlds
from .typed_record_scenario_catalog import _TYPED_RECORD_SCENARIOS


def verify_typed_record_matrix(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
) -> TypedRecordMatrixWorlds:
    """Exercise typed writes and retain the JSON worlds needed by substantive reports."""
    print(f"{config.label}: verifying typed business-record command matrix")
    assert_typed_record_scenario_contract(operation_ids)
    retained_json_worlds: dict[str, TypedRecordWorld] = {}
    for scenario in _TYPED_RECORD_SCENARIOS:
        for output_mode in (_JSON_MODE, _TEXT_MODE):
            world = _prepare_world(config, operation_ids, scenario, output_mode)
            _declare_supporting_accounts(world, operation_ids, scenario)
            if scenario.scenario_id == "reversal":
                _run_reversal_sequence(world, operation_ids, output_mode)
            else:
                _run_requests(
                    world,
                    operation_ids,
                    scenario.scenario_id,
                    output_mode,
                    scenario.requests(world.config.request_prefix),
                )
            if output_mode == _JSON_MODE:
                retained_json_worlds[scenario.scenario_id] = world
    return _typed_record_report_worlds(retained_json_worlds)


def _run_requests(
    world: TypedRecordWorld,
    operation_ids: Mapping[str, str],
    scenario_id: str,
    output_mode: str,
    requests: tuple[TypedRecordRequest, ...],
) -> None:
    for index, request in enumerate(requests, start=1):
        _run_typed_request(
            world,
            operation_ids,
            scenario_id,
            output_mode,
            index,
            request,
        )


def assert_typed_record_scenario_contract(operation_ids: Mapping[str, str]) -> None:
    """Prove the typed lifecycle fixtures cover exactly the typed-record API slice."""
    _assert_standard_commercial_report_context()
    request_operation_keys = _typed_record_request_operation_keys()
    duplicate_keys = sorted(
        operation_key
        for operation_key in set(request_operation_keys)
        if request_operation_keys.count(operation_key) > 1
    )
    require(
        len(request_operation_keys) == 34,
        "typed-record matrix must enumerate all 34 current typed request operation keys",
    )
    require(
        not duplicate_keys,
        "typed-record matrix repeats request operation keys: " + ", ".join(duplicate_keys),
    )
    request_operation_ids = {
        _operation(operation_ids, operation_key) for operation_key in request_operation_keys
    }
    routed_typed_record_ids = {
        operation_id
        for operation_id, binding in SCENARIO_MATRIX.items()
        if binding.domain == ScenarioDomain.TYPED_RECORD
    }
    missing_request_ids = sorted(routed_typed_record_ids - request_operation_ids)
    stale_request_ids = sorted(request_operation_ids - routed_typed_record_ids)
    require(
        not missing_request_ids and not stale_request_ids,
        _typed_record_routing_mismatch_message(missing_request_ids, stale_request_ids),
    )


def _assert_standard_commercial_report_context() -> None:
    report_tokens = dict(_STANDARD_COMMERCIAL_REPORT_TOKENS)
    require(
        report_tokens.get("cash-flow-statement") == "service-revenue",
        "typed-record commercial cash-flow coverage must retain its settled-sale counterpart "
        "rather than a cash-account token",
    )


def _typed_record_request_operation_keys() -> tuple[str, ...]:
    ordinary_request_keys = tuple(
        request.operation_key
        for scenario in _TYPED_RECORD_SCENARIOS
        for request in scenario.requests("typed-record-contract")
    )
    return (*ordinary_request_keys, "recordReversal")


def _typed_record_routing_mismatch_message(
    missing_request_ids: list[str], stale_request_ids: list[str]
) -> str:
    parts = ["typed-record matrix request keys differ from TYPED_RECORD scenario routing"]
    if missing_request_ids:
        parts.append("missing typed requests: " + ", ".join(missing_request_ids))
    if stale_request_ids:
        parts.append("stale typed requests: " + ", ".join(stale_request_ids))
    return "; ".join(parts)
