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
    from release_smoke_workflow.cli import (
        run_cli_allow_failure,
        run_cli_with_split_streams,
    )
    from release_smoke_workflow.fixtures import (
        prepare_fixture_directories,
        write_acceptance_fixtures,
    )
    from release_smoke_workflow.path_support import (
        extract_pdf_exported_path,
        normalize_reported_path,
    )
    from release_smoke_workflow.report_text_samples import (
        STANDARD_ACCOUNT_BALANCE_TEXT,
        STANDARD_LIST_POSTINGS_TEXT,
        STANDARD_PERIOD_SUMMARY_TEXT,
        STANDARD_TRIAL_BALANCE_TEXT,
        pdf_export_stderr,
    )
    from release_smoke_workflow.scenario import (
        ARGUMENT_PATH_MODE_ABSOLUTE,
        ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE,
        build_release_smoke_scenario,
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
        extract_pdf_exported_path,
        base_bridge_config,
        smoke_path,
        write_bridge_script,
        STANDARD_LIST_POSTINGS_TEXT,
        STANDARD_ACCOUNT_BALANCE_TEXT,
        STANDARD_TRIAL_BALANCE_TEXT,
        STANDARD_PERIOD_SUMMARY_TEXT,
        pdf_export_stderr,
        structured_account_ledger_csv,
    )
    return 0


def assert_release_smoke_scenarios(
    build_release_smoke_scenario,
    absolute_mode: str,
    relative_mode: str,
) -> None:
    bundle = build_release_smoke_scenario(
        pathlib.Path("/tmp/workspace odd/Rīga büro/2026 Q2 close"),
        absolute_mode,
        "bundle-acceptance",
    )
    assert "Rīga büro" in str(bundle.book.local_path)
    assert bundle.book.argument == str(bundle.book.local_path)
    assert bundle.backup_book.argument == str(bundle.backup_book.local_path)
    assert bundle.second_page_command_id == "bundle-acceptance-sale"

    docker = build_release_smoke_scenario(
        pathlib.Path("/workdir"),
        relative_mode,
        "docker-acceptance",
    )
    assert docker.book.argument == "books odd/Rīga büro/nested/-entity [docker-acceptance].sqlite"
    assert (
        docker.backup_book.argument
        == "backup odd/Rīga büro/nested/-entity backup [docker-acceptance].sqlite"
    )
    assert (
        docker.replacement_book_key.argument
        == "keys odd/Rīga büro/nested/--entity [docker-acceptance]-replacement.key"
    )
    assert docker.actor_prefix == "docker-acceptance"


def assert_fixture_generation(
    build_release_smoke_scenario,
    prepare_fixture_directories,
    write_acceptance_fixtures,
    expected_source_document,
    absolute_mode: str,
) -> None:
    with tempfile.TemporaryDirectory() as fixture_dir:
        fixture_scenario = build_release_smoke_scenario(
            pathlib.Path(fixture_dir),
            absolute_mode,
            "fixture-regression",
        )
        prepare_fixture_directories(fixture_scenario)
        write_acceptance_fixtures(fixture_scenario)

        assert_request_payload(
            json.loads(fixture_scenario.request_sale.local_path.read_text(encoding="utf-8")),
            "CASH_REVENUE",
            "fixture-regression-sale",
            expected_source_document("fixture-regression", "sale", "2026-04-07"),
        )
        assert_request_payload(
            json.loads(fixture_scenario.request_adjustment.local_path.read_text(encoding="utf-8")),
            "CORRECTION_ADJUSTMENT",
            "fixture-regression-adjustment",
            expected_source_document("fixture-regression", "adjustment", "2026-04-08"),
        )

        sale_request = json.loads(
            fixture_scenario.request_sale.local_path.read_text(encoding="utf-8")
        )
        adjustment_request = json.loads(
            fixture_scenario.request_adjustment.local_path.read_text(encoding="utf-8")
        )
        assert sale_request["cashAccountCode"] == "1000"
        assert sale_request["revenueAccountCode"] == "2000"
        assert sale_request["amount"]["minorUnits"] == "1000"
        assert adjustment_request["lines"][0]["accountCode"] == "1000"
        assert (
            json.loads(fixture_scenario.declare_cash.local_path.read_text(encoding="utf-8"))[
                "accountNodeKind"
            ]
            == "POSTABLE"
        )
        assert (
            json.loads(fixture_scenario.declare_revenue.local_path.read_text(encoding="utf-8"))[
                "accountNodeKind"
            ]
            == "POSTABLE"
        )


def assert_request_payload(
    request_payload: dict[str, object],
    expected_entry_kind: str,
    expected_command_id: str,
    expected_document: dict[str, str],
) -> None:
    assert request_payload["entryKind"] == expected_entry_kind
    assert request_payload["evidence"] == {
        "sourceDocuments": [expected_document],
        "approvals": [],
    }
    assert request_payload["provenance"]["commandId"] == expected_command_id


def assert_bridge_and_report_contracts(
    repo_root: pathlib.Path,
    run_cli_allow_failure,
    run_cli_with_split_streams,
    assert_operator_queries_and_reports,
    normalize_reported_path,
    extract_pdf_exported_path,
    base_bridge_config,
    smoke_path,
    write_bridge_script,
    standard_list_postings_text: str,
    standard_account_balance_text: str,
    standard_trial_balance_text: str,
    standard_period_summary_text: str,
    pdf_export_stderr,
    structured_account_ledger_csv,
) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
        temp_path = pathlib.Path(temp_dir)
        dummy = smoke_path(temp_path, pathlib.Path("dummy"))
        bridge_script = write_bridge_script(temp_path)
        assert_bridge_invocation_contracts(
            repo_root,
            temp_path,
            bridge_script,
            dummy,
            run_cli_allow_failure,
            run_cli_with_split_streams,
            base_bridge_config,
            smoke_path,
        )
        assert_pdf_report_contracts(
            repo_root,
            temp_path,
            bridge_script,
            dummy,
            assert_operator_queries_and_reports,
            normalize_reported_path,
            extract_pdf_exported_path,
            base_bridge_config,
            smoke_path,
            standard_list_postings_text,
            standard_account_balance_text,
            standard_trial_balance_text,
            standard_period_summary_text,
            pdf_export_stderr,
            structured_account_ledger_csv,
        )


def assert_bridge_invocation_contracts(
    repo_root: pathlib.Path,
    temp_path: pathlib.Path,
    bridge_script: pathlib.Path,
    dummy,
    run_cli_allow_failure,
    run_cli_with_split_streams,
    base_bridge_config,
    smoke_path,
) -> None:
    config = base_bridge_config(
        repo_root,
        temp_path,
        bridge_script,
        dummy,
        runtime_distribution_key="bundleRuntimeDistribution",
        reported_work_root=None,
        book_key_output_permissions="owner-only-acl",
        pdf_path=smoke_path(
            temp_path,
            pathlib.Path("reports odd") / "trial balance [bridge].pdf",
        ),
        pdf_argument_override=None,
        stderr_path=temp_path / "stderr.txt",
        label="Bridge regression",
    )
    unicode_argument = str(temp_path / "workspace odd" / "Rīga büro" / "key.key")
    output, exit_code = run_cli_allow_failure(
        config,
        "generate-book-key-file",
        "--book-key-file",
        unicode_argument,
    )
    assert exit_code == 0
    assert json.loads(output)["arguments"][2] == unicode_argument

    stdout, stderr = run_cli_with_split_streams(
        config,
        "trial-balance",
        "--book-file",
        unicode_argument,
    )
    assert json.loads(stdout)["arguments"][2] == unicode_argument
    assert stderr == ""


def assert_pdf_report_contracts(
    repo_root: pathlib.Path,
    temp_path: pathlib.Path,
    bridge_script: pathlib.Path,
    dummy,
    assert_operator_queries_and_reports,
    normalize_reported_path,
    extract_pdf_exported_path,
    base_bridge_config,
    smoke_path,
    standard_list_postings_text: str,
    standard_account_balance_text: str,
    standard_trial_balance_text: str,
    standard_period_summary_text: str,
    pdf_export_stderr,
    structured_account_ledger_csv,
) -> None:
    pdf_local_path = temp_path / "reports odd" / "trial balance [bridge].pdf"
    pdf_local_path.parent.mkdir(parents=True, exist_ok=True)
    pdf_local_path.write_bytes(b"%PDF-1.7\nbridge")
    bundle_pdf = smoke_path(temp_path, pathlib.Path("reports odd") / "trial balance [bridge].pdf")
    bundle_config = base_bridge_config(
        repo_root,
        temp_path,
        bridge_script,
        dummy,
        runtime_distribution_key="bundleRuntimeDistribution",
        reported_work_root=None,
        book_key_output_permissions="owner-only-acl",
        pdf_path=bundle_pdf,
        pdf_argument_override=str(pdf_local_path),
        stderr_path=temp_path / "stderr.txt",
        label="Bridge regression",
    )
    assert_operator_queries_and_reports(
        bundle_config,
        list_postings_second_page_output='{"commandId":"bridge-sale"}\n',
        list_postings_text_output=standard_list_postings_text,
        account_balance_text_output=standard_account_balance_text,
        trial_balance_text_output=standard_trial_balance_text,
        pdf_stdout=standard_trial_balance_text,
        pdf_stderr=pdf_export_stderr(str(pdf_local_path)),
        account_ledger_csv_output=structured_account_ledger_csv("bridge"),
        period_summary_text_output=standard_period_summary_text,
    )

    windows_report_stderr = pdf_export_stderr(
        r"D:\a\FinGrind\workspace odd\Rīga büro\reports odd\trial balance [bundle-acceptance].pdf"
    )
    assert normalize_reported_path(extract_pdf_exported_path(windows_report_stderr)) == (
        normalize_reported_path(
            "d:/a/FinGrind/workspace odd/Rīga büro/reports odd/trial balance [bundle-acceptance].pdf"
        )
    )

    docker_pdf = smoke_path(temp_path, pathlib.Path("reports odd") / "trial balance [bridge].pdf")
    docker_config = base_bridge_config(
        repo_root,
        temp_path,
        bridge_script,
        dummy,
        runtime_distribution_key="containerRuntimeDistribution",
        reported_work_root=pathlib.Path("/workdir"),
        book_key_output_permissions="0600",
        pdf_path=docker_pdf,
        pdf_argument_override="reports odd/trial balance [bridge].pdf",
        stderr_path=temp_path / "docker-stderr.txt",
        label="Docker regression",
    )
    assert_operator_queries_and_reports(
        docker_config,
        list_postings_second_page_output='{"commandId":"bridge-sale"}\n',
        list_postings_text_output=standard_list_postings_text,
        account_balance_text_output=standard_account_balance_text,
        trial_balance_text_output=standard_trial_balance_text,
        pdf_stdout=standard_trial_balance_text,
        pdf_stderr=pdf_export_stderr("/workdir/reports odd/trial balance [bridge].pdf"),
        account_ledger_csv_output=structured_account_ledger_csv("bridge"),
        period_summary_text_output=standard_period_summary_text,
    )


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
