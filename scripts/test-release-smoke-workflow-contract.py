#!/usr/bin/env python3
"""Regression checks for the shared release-smoke workflow package contract."""

from __future__ import annotations

import pathlib
import sys


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        raise SystemExit("usage: test-release-smoke-workflow-contract.py <repo-root>")
    repo_root = pathlib.Path(argv[0]).resolve()
    sys.path.insert(0, str(repo_root / "scripts"))

    from release_smoke_workflow.account_ledger_csv_samples import structured_account_ledger_csv
    from release_smoke_workflow.assertions import (
        assert_operator_queries_and_reports,
        expected_source_document,
    )
    from release_smoke_workflow.bridge_contract_support import (
        base_bridge_config,
        smoke_path,
        write_bridge_script,
    )
    from release_smoke_workflow.bridge_report_contract import assert_bridge_and_report_contracts
    from release_smoke_workflow.cli import (
        run_cli_allow_failure,
        run_cli_with_split_streams,
    )
    from release_smoke_workflow.fixtures import (
        prepare_fixture_directories,
        write_acceptance_fixtures,
    )
    from release_smoke_workflow.path_support import (
        extract_pdf_artifact_path,
        normalize_reported_path,
    )
    from release_smoke_workflow.report_text_samples import (
        STANDARD_ACCOUNT_BALANCE_TEXT,
        STANDARD_LIST_POSTINGS_TEXT,
        STANDARD_PERIOD_SUMMARY_TEXT,
        STANDARD_TRIAL_BALANCE_TEXT,
        pdf_export_stdout,
    )
    from release_smoke_workflow.scenario import (
        ARGUMENT_PATH_MODE_ABSOLUTE,
        ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE,
        build_release_smoke_scenario,
    )
    from release_smoke_workflow.scenario_contract import (
        assert_fixture_generation,
        assert_release_smoke_scenarios,
    )

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
    assert_bridge_and_report_contracts(
        repo_root,
        run_cli_allow_failure,
        run_cli_with_split_streams,
        assert_operator_queries_and_reports,
        normalize_reported_path,
        extract_pdf_artifact_path,
        base_bridge_config,
        smoke_path,
        write_bridge_script,
        STANDARD_LIST_POSTINGS_TEXT,
        STANDARD_ACCOUNT_BALANCE_TEXT,
        STANDARD_TRIAL_BALANCE_TEXT,
        STANDARD_PERIOD_SUMMARY_TEXT,
        pdf_export_stdout,
        structured_account_ledger_csv,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
