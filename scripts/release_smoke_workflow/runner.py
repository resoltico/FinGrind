from __future__ import annotations

import sys

from contract_values import load_contract_values

from .config import load_config
from .discovery_checks import (
    verify_help_and_template_surfaces,
    verify_runtime_contract,
    verify_version_command,
)
from .failure_checks import (
    verify_deterministic_nonsense_workflows,
    verify_rekey_and_wrong_key_semantics,
)
from .fixtures import prepare_fixture_directories, write_acceptance_fixtures
from .maintenance_checks import verify_backup_restore_and_rollback_surfaces
from .models import ReleaseSmokeConfig, ReleaseSmokeFailure
from .query_checks import verify_operator_queries_and_reports, verify_preflight_and_commit
from .setup_checks import (
    verify_account_registry,
    verify_book_key_generation,
    verify_open_book,
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
    error_exit_codes = verify_runtime_contract(config, contract, operation_ids)
    verify_help_and_template_surfaces(config, operation_ids)
    verify_book_key_generation(config, operation_ids)
    verify_open_book(config, operation_ids)
    verify_account_registry(config, operation_ids)
    verify_preflight_and_commit(config, operation_ids)
    verify_operator_queries_and_reports(config, operation_ids)
    verify_backup_restore_and_rollback_surfaces(config, operation_ids)
    verify_rekey_and_wrong_key_semantics(config, operation_ids, error_exit_codes)
    verify_deterministic_nonsense_workflows(config, operation_ids, error_exit_codes)
    print(f"{config.label}: success")
