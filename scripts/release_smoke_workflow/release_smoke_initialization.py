from __future__ import annotations

from dataclasses import dataclass

from contract_values import load_contract_values

from .discovery_assertions import RuntimeContractFacts
from .discovery_checks import verify_runtime_contract, verify_version_command
from .fixture_writers import write_acceptance_fixtures
from .fixtures import prepare_fixture_directories
from .models import ReleaseSmokeConfig


@dataclass(frozen=True)
class ReleaseSmokeRunContext:
    config: ReleaseSmokeConfig
    operation_ids: dict[str, str]
    runtime_contract: RuntimeContractFacts


def initialize_release_smoke(config: ReleaseSmokeConfig) -> ReleaseSmokeRunContext:
    prepare_fixture_directories(config)
    write_acceptance_fixtures(config)
    contract = load_contract_values(config.repo_root)
    operation_ids = contract["operationIds"]
    verify_version_command(config, operation_ids)
    runtime_contract = verify_runtime_contract(config, contract, operation_ids)
    return ReleaseSmokeRunContext(config, operation_ids, runtime_contract)
