from __future__ import annotations

import pathlib

from .assertions import expected_source_document
from .fixture_generation_contract import assert_fixture_generation
from .fixture_writers import write_acceptance_fixtures, write_ledger_plan_fixtures
from .fixtures import prepare_fixture_directories
from .scenario import build_release_smoke_scenario
from .scenario_operation_contract import (
    assert_field_matrix_operation_routing,
    assert_operation_id_references,
)
from .scenario_path_contract import (
    assert_release_smoke_scenarios,
    assert_release_smoke_work_root_contract,
)
from .scenario_paths import ARGUMENT_PATH_MODE_ABSOLUTE, ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE


def assert_contract_scenario_contracts(repo_root: pathlib.Path) -> None:
    assert_release_smoke_work_root_contract()
    assert_release_smoke_scenarios(
        build_release_smoke_scenario,
        ARGUMENT_PATH_MODE_ABSOLUTE,
        ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE,
    )
    assert_fixture_generation(
        build_release_smoke_scenario,
        prepare_fixture_directories,
        write_acceptance_fixtures,
        write_ledger_plan_fixtures,
        expected_source_document,
        ARGUMENT_PATH_MODE_ABSOLUTE,
    )
    assert_operation_id_references(repo_root)
    assert_field_matrix_operation_routing(repo_root)
