from __future__ import annotations

import pathlib

from .contract_attestation_assertions import assert_attestation_payload_contracts
from .contract_diagnostic_assertions import assert_diagnostic_catalog_contracts
from .contract_external_surface_assertions import assert_external_surface_contracts
from .contract_projection_assertions import assert_contract_projection_assertions
from .contract_scenario_assertions import assert_contract_scenario_contracts


def run_contract_suite(repo_root: pathlib.Path) -> None:
    assert_contract_scenario_contracts(repo_root)
    assert_contract_projection_assertions()
    assert_external_surface_contracts(repo_root)
    assert_diagnostic_catalog_contracts()
    assert_attestation_payload_contracts()
