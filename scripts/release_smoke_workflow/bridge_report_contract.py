"""Regression checks for release-smoke bridge invocation and report output contracts."""

from __future__ import annotations

import json
import pathlib
import tempfile


def assert_bridge_and_report_contracts(
    repo_root: pathlib.Path,
    run_cli_allow_failure,
    run_cli_with_split_streams,
    assert_operator_queries_and_reports,
    normalize_reported_path,
    extract_pdf_artifact_path,
    base_bridge_config,
    smoke_path,
    write_bridge_script,
    standard_list_postings_text: str,
    standard_account_balance_text: str,
    standard_trial_balance_text: str,
    standard_period_summary_text: str,
    pdf_export_stdout,
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
            extract_pdf_artifact_path,
            base_bridge_config,
            smoke_path,
            standard_list_postings_text,
            standard_account_balance_text,
            standard_trial_balance_text,
            standard_period_summary_text,
            pdf_export_stdout,
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
    extract_pdf_artifact_path,
    base_bridge_config,
    smoke_path,
    standard_list_postings_text: str,
    standard_account_balance_text: str,
    standard_trial_balance_text: str,
    standard_period_summary_text: str,
    pdf_export_stdout,
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
        pdf_stdout=pdf_export_stdout(str(pdf_local_path)),
        pdf_stderr="",
        account_ledger_csv_output=structured_account_ledger_csv("bridge"),
        period_summary_text_output=standard_period_summary_text,
    )

    windows_report_stdout = pdf_export_stdout(
        r"D:\a\FinGrind\workspace odd\Rīga büro\reports odd\trial balance [bundle-acceptance].pdf"
    )
    assert normalize_reported_path(extract_pdf_artifact_path(windows_report_stdout)) == (
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
        pdf_stdout=pdf_export_stdout("/workdir/reports odd/trial balance [bridge].pdf"),
        pdf_stderr="",
        account_ledger_csv_output=structured_account_ledger_csv("bridge"),
        period_summary_text_output=standard_period_summary_text,
    )
