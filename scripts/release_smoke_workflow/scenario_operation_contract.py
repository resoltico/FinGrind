"""Regression checks for operation IDs and field-matrix routing."""

from __future__ import annotations

import json
import pathlib
import re
from types import SimpleNamespace

from contract_values import load_contract_values

from .field_matrix.administrative_scenarios import assert_administrative_scenario_contract
from .field_matrix.report_contexts import ReportBookContext
from .field_matrix.report_routes import _cash_flow_statement_arguments
from .field_matrix.scenario_matrix import SCENARIO_MATRIX
from .field_matrix.tax_report_setup import TaxReportFact
from .field_matrix.typed_record_contract import assert_typed_record_scenario_contract


def assert_operation_id_references(repo_root: pathlib.Path) -> None:
    contract = load_contract_values(repo_root)
    operation_ids = contract["operationIds"]
    referenced_operation_keys = {
        match.group(1)
        for path in (repo_root / "scripts" / "release_smoke_workflow").rglob("*.py")
        for match in re.finditer(
            r'operation_ids\["([a-zA-Z0-9]+)"\]', path.read_text(encoding="utf-8")
        )
    }
    unknown_operation_keys = sorted(referenced_operation_keys.difference(operation_ids))
    if unknown_operation_keys:
        raise AssertionError(
            "release-smoke workflow references unknown operation-id keys: "
            + ", ".join(unknown_operation_keys)
        )


def assert_field_matrix_operation_routing(repo_root: pathlib.Path) -> None:
    operation_contract_path = (
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/operation-id-contract.json"
    )
    contract = json.loads(operation_contract_path.read_text(encoding="utf-8"))
    assert isinstance(contract, dict)
    operation_ids = {value for value in contract.values() if isinstance(value, str)}
    assert len(operation_ids) == len(contract)
    matrix_operation_ids = set(SCENARIO_MATRIX)
    missing_routing = sorted(operation_ids - matrix_operation_ids)
    stale_routing = sorted(matrix_operation_ids - operation_ids)
    assert not missing_routing and not stale_routing, (
        "field-matrix operation routing differs from the canonical operation-id contract; "
        f"missing={missing_routing}; stale={stale_routing}"
    )
    resolved_operation_ids = load_contract_values(repo_root)["operationIds"]
    assert isinstance(resolved_operation_ids, dict)
    assert_administrative_scenario_contract()
    assert_typed_record_scenario_contract(resolved_operation_ids)
    assert_cash_flow_report_route_contract()


def assert_cash_flow_report_route_contract() -> None:
    """Keep the commercial fixture's opening cash observable before selected movement."""
    context = ReportBookContext(
        config=SimpleNamespace(
            book=SimpleNamespace(argument="commercial.sqlite"),
            book_key=SimpleNamespace(argument="commercial.key"),
        ),
        period_start="2026-01-02",
        period_end="2026-01-09",
        as_of="2026-01-09",
        account_code="cash",
        expected_report_tokens=(("cash-flow-statement", "service-revenue"),),
    )
    assert _cash_flow_statement_arguments(
        context,
        TaxReportFact("unused-registration", "unused-tax-code", "1"),
    ) == (
        "--book-file",
        "commercial.sqlite",
        "--book-key-file",
        "commercial.key",
        "--period-start",
        "2026-01-03",
        "--period-end",
        "2026-01-09",
    )
