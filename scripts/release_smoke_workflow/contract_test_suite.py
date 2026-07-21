from __future__ import annotations

import pathlib

from .assertions import expected_source_document
from .contract_bridge_test_suite import run_bridge_and_report_contracts
from .contract_open_book_test import assert_attested_open_book_arguments
from .fixtures import prepare_fixture_directories, write_acceptance_fixtures
from .scenario import build_release_smoke_scenario
from .scenario_contract import (
    assert_fixture_generation,
    assert_operation_id_references,
    assert_release_smoke_scenarios,
)
from .scenario_paths import ARGUMENT_PATH_MODE_ABSOLUTE, ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE


def run_contract_suite(repo_root: pathlib.Path) -> None:
    assert_release_smoke_scenarios(
        build_release_smoke_scenario,
        ARGUMENT_PATH_MODE_ABSOLUTE,
        ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE,
    )
    assert_fixture_generation(
        build_release_smoke_scenario,
        prepare_fixture_directories,
        write_acceptance_fixtures,
        expected_source_document,
        ARGUMENT_PATH_MODE_ABSOLUTE,
    )
    assert_operation_id_references(repo_root)
    run_bridge_and_report_contracts(repo_root)
    assert_attested_open_book_arguments(repo_root)
