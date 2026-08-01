"""Fresh-world acceptance scenarios for every typed business-recording command.

Each scenario builds the retained state that makes its business operation
meaningful, then exercises it once in JSON and once in text on independent
protected books.
"""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig
from .report_contexts import TypedRecordMatrixWorlds
from .typed_record_accounts import _declare_supporting_accounts
from .typed_record_bootstrap import _prepare_world
from .typed_record_constants import _JSON_MODE, _TEXT_MODE
from .typed_record_contract import assert_typed_record_scenario_contract
from .typed_record_execution import _run_reversal_sequence, _run_typed_request
from .typed_record_models import TypedRecordRequest, TypedRecordWorld
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
