from __future__ import annotations

import sys

from contract_values import load_contract_values

from .config import load_config
from .failure_checks import (
    verify_deterministic_nonsense_workflows,
    verify_rekey_and_wrong_key_semantics,
)
from .fixtures import prepare_fixture_directories, write_acceptance_fixtures
from .models import ReleaseSmokeConfig, ReleaseSmokeFailure
from .query_checks import verify_operator_queries_and_reports, verify_preflight_and_commit
from .setup_checks import (
    verify_account_registry,
    verify_book_key_generation,
    verify_open_book,
    verify_runtime_contract,
    verify_version_command,
)


def main() -> int:
    try:
        run_release_smoke(load_config())
    except ReleaseSmokeFailure as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


def run_release_smoke(config: ReleaseSmokeConfig) -> None:
    prepare_fixture_directories(config)
    write_acceptance_fixtures(config)
    contract = load_contract_values(config.repo_root)
    operation_ids = contract["operationIds"]
    verify_version_command(config, operation_ids)
    verify_runtime_contract(config, contract, operation_ids)
    verify_book_key_generation(config, operation_ids)
    verify_open_book(config, operation_ids)
    verify_account_registry(config, operation_ids)
    verify_preflight_and_commit(config, operation_ids)
    verify_operator_queries_and_reports(config, operation_ids)
    verify_rekey_and_wrong_key_semantics(config, operation_ids)
    verify_deterministic_nonsense_workflows(config, operation_ids)
    print(f"{config.label}: success")
