#!/usr/bin/env python3
"""Regression checks for the shared release-smoke workflow package contract."""

from __future__ import annotations

import json
import pathlib
import sys
import tempfile


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
    from release_smoke_workflow.open_book_support import open_book
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
        assert_operation_id_references,
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
    assert_operation_id_references(repo_root)
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
    assert_attested_open_book_arguments(
        repo_root,
        base_bridge_config,
        smoke_path,
        write_bridge_script,
        open_book,
    )
    return 0


def assert_attested_open_book_arguments(
    repo_root: pathlib.Path,
    base_bridge_config,
    smoke_path,
    write_bridge_script,
    open_book,
) -> None:
    with tempfile.TemporaryDirectory() as temporary_directory:
        temporary_path = pathlib.Path(temporary_directory)
        bridge_script = write_bridge_script(temporary_path)
        dummy = smoke_path(temporary_path, pathlib.Path("fixture"))
        config = base_bridge_config(
            repo_root,
            temporary_path,
            bridge_script,
            dummy,
            runtime_distribution_key="bundleRuntimeDistribution",
            reported_work_root=None,
            book_key_output_permissions="0600",
            pdf_path=dummy,
            pdf_argument_override=None,
            stderr_path=temporary_path / "stderr.txt",
            label="Attested open-book arguments",
        )
        payload = json.loads(open_book(config, {"openBook": "open-book"}))
        arguments = payload["arguments"]
        assert arguments[-8:] == [
            "--attestation-founder-principal-id",
            "4bc17dd7-145f-4ea7-bb55-167ca2f6ac11",
            "--attestation-founder-key-file",
            str(temporary_path / "fixture"),
            "--attestation-founder-passphrase-file",
            str(temporary_path / "fixture"),
            "--output",
            "json",
        ]


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
