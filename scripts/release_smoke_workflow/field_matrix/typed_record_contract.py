"""Structural completeness checks for the typed-record acceptance matrix."""

from __future__ import annotations

from collections.abc import Mapping

from ..support import require
from .report_routes import _REPORT_ROUTES
from .scenario_matrix import SCENARIO_MATRIX, ScenarioDomain
from .typed_record_constants import _STANDARD_COMMERCIAL_REPORT_TOKENS
from .typed_record_output import _operation
from .typed_record_scenario_catalog import _TYPED_RECORD_SCENARIOS


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
    commercial_report_operations = {
        operation_id
        for operation_id, route in _REPORT_ROUTES.items()
        if route.context_name == "commercial"
    }
    require(
        len(report_tokens) == len(_STANDARD_COMMERCIAL_REPORT_TOKENS)
        and set(report_tokens) == commercial_report_operations,
        "typed-record commercial report coverage must provide exactly one substantive token for "
        "every commercial report route",
    )
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
