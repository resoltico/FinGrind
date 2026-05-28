#!/usr/bin/env python3
"""Regression checks for the shared release-smoke workflow package contract."""

from __future__ import annotations

import csv
import json
import pathlib
import sys
import tempfile
from io import StringIO


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        raise SystemExit("usage: test-release-smoke-workflow-contract.py <repo-root>")
    repo_root = pathlib.Path(argv[0]).resolve()
    sys.path.insert(0, str(repo_root / "scripts"))

    from release_smoke_workflow.assertions import (
        assert_operator_queries_and_reports,
        expected_source_document,
    )
    from release_smoke_workflow.cli import (
        run_cli_allow_failure,
        run_cli_with_split_streams,
    )
    from release_smoke_workflow.fixtures import (
        prepare_fixture_directories,
        write_acceptance_fixtures,
    )
    from release_smoke_workflow.models import ReleaseSmokeConfig, SmokePath
    from release_smoke_workflow.scenario import (
        ARGUMENT_PATH_MODE_ABSOLUTE,
        ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE,
        build_release_smoke_scenario,
    )
    from release_smoke_workflow.support import (
        extract_pdf_exported_path,
        normalize_reported_path,
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
        ReleaseSmokeConfig,
        SmokePath,
        normalize_reported_path,
        extract_pdf_exported_path,
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
    assert bundle.second_page_command_id == "bundle-acceptance-sale"

    docker = build_release_smoke_scenario(
        pathlib.Path("/workdir"),
        relative_mode,
        "docker-acceptance",
    )
    assert docker.book.argument == "books odd/Rīga büro/nested/-entity [docker-acceptance].sqlite"
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
    release_smoke_config_type,
    smoke_path_type,
    normalize_reported_path,
    extract_pdf_exported_path,
) -> None:
    dummy = smoke_path_type(
        relative_path=pathlib.Path("dummy"),
        local_path=pathlib.Path("/tmp/dummy"),
        argument="dummy",
    )
    with tempfile.TemporaryDirectory() as temp_dir:
        temp_path = pathlib.Path(temp_dir)
        bridge_script = write_bridge_script(temp_path)
        assert_bridge_invocation_contracts(
            repo_root,
            temp_path,
            bridge_script,
            dummy,
            release_smoke_config_type,
            run_cli_allow_failure,
            run_cli_with_split_streams,
        )
        assert_pdf_report_contracts(
            repo_root,
            temp_path,
            bridge_script,
            dummy,
            release_smoke_config_type,
            smoke_path_type,
            assert_operator_queries_and_reports,
            normalize_reported_path,
            extract_pdf_exported_path,
        )


def write_bridge_script(temp_path: pathlib.Path) -> pathlib.Path:
    bridge_script = temp_path / "bridge.py"
    bridge_script.write_text(
        "\n".join(
            [
                "import json",
                "import pathlib",
                "import sys",
                "request = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding='utf-8'))",
                "json.dump(request, sys.stdout, ensure_ascii=False)",
                "sys.stdout.write('\\n')",
            ]
        ),
        encoding="utf-8",
    )
    return bridge_script


def assert_bridge_invocation_contracts(
    repo_root: pathlib.Path,
    temp_path: pathlib.Path,
    bridge_script: pathlib.Path,
    dummy,
    release_smoke_config_type,
    run_cli_allow_failure,
    run_cli_with_split_streams,
) -> None:
    config = base_bridge_config(
        release_smoke_config_type,
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
    release_smoke_config_type,
    smoke_path_type,
    assert_operator_queries_and_reports,
    normalize_reported_path,
    extract_pdf_exported_path,
) -> None:
    pdf_local_path = temp_path / "reports odd" / "trial balance [bridge].pdf"
    pdf_local_path.parent.mkdir(parents=True, exist_ok=True)
    pdf_local_path.write_bytes(b"%PDF-1.7\nbridge")
    bundle_pdf = smoke_path_type(
        relative_path=pathlib.Path("reports odd") / "trial balance [bridge].pdf",
        local_path=pdf_local_path,
        argument=str(pdf_local_path),
    )
    bundle_config = base_bridge_config(
        release_smoke_config_type,
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
        list_postings_text_output=STANDARD_LIST_POSTINGS_TEXT,
        account_balance_text_output=STANDARD_ACCOUNT_BALANCE_TEXT,
        trial_balance_text_output=STANDARD_TRIAL_BALANCE_TEXT,
        pdf_stdout=STANDARD_TRIAL_BALANCE_TEXT,
        pdf_stderr=pdf_export_stderr(str(pdf_local_path)),
        account_ledger_csv_output=structured_account_ledger_csv("bridge"),
        period_summary_text_output=STANDARD_PERIOD_SUMMARY_TEXT,
    )

    windows_report_stderr = pdf_export_stderr(
        r"D:\a\FinGrind\workspace odd\Rīga büro\reports odd\trial balance [bundle-acceptance].pdf"
    )
    assert normalize_reported_path(extract_pdf_exported_path(windows_report_stderr)) == (
        normalize_reported_path(
            "d:/a/FinGrind/workspace odd/Rīga büro/reports odd/trial balance [bundle-acceptance].pdf"
        )
    )

    docker_pdf = smoke_path_type(
        relative_path=pathlib.Path("reports odd") / "trial balance [bridge].pdf",
        local_path=pdf_local_path,
        argument="reports odd/trial balance [bridge].pdf",
    )
    docker_config = base_bridge_config(
        release_smoke_config_type,
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
        list_postings_text_output=STANDARD_LIST_POSTINGS_TEXT,
        account_balance_text_output=STANDARD_ACCOUNT_BALANCE_TEXT,
        trial_balance_text_output=STANDARD_TRIAL_BALANCE_TEXT,
        pdf_stdout=STANDARD_TRIAL_BALANCE_TEXT,
        pdf_stderr=pdf_export_stderr("/workdir/reports odd/trial balance [bridge].pdf"),
        account_ledger_csv_output=structured_account_ledger_csv("bridge"),
        period_summary_text_output=STANDARD_PERIOD_SUMMARY_TEXT,
    )


def base_bridge_config(
    release_smoke_config_type,
    repo_root: pathlib.Path,
    temp_path: pathlib.Path,
    bridge_script: pathlib.Path,
    dummy,
    *,
    runtime_distribution_key: str,
    reported_work_root: pathlib.Path | None,
    book_key_output_permissions: str,
    pdf_path,
    pdf_argument_override: str | None,
    stderr_path: pathlib.Path,
    label: str,
):
    resolved_pdf_path = pdf_path
    if pdf_argument_override is not None:
        resolved_pdf_path = type(pdf_path)(
            relative_path=pdf_path.relative_path,
            local_path=pdf_path.local_path,
            argument=pdf_argument_override,
        )
    return release_smoke_config_type(
        label=label,
        repo_root=repo_root,
        command_prefix=["unused-direct-command"],
        command_bridge_prefix=[sys.executable, str(bridge_script)],
        command_cwd=None,
        reported_work_root=reported_work_root,
        command_env_drop=[],
        command_env_set={},
        runtime_distribution_key=runtime_distribution_key,
        expect_loaded_sqlite_details=True,
        expect_bundle_home_property=True,
        book_key_output_permissions=book_key_output_permissions,
        request_sale=dummy,
        request_adjustment=dummy,
        invalid_request=dummy,
        declare_cash=dummy,
        declare_revenue=dummy,
        book=dummy,
        book_key=dummy,
        replacement_book_key=dummy,
        prompt_failure_book=dummy,
        trial_balance_pdf=resolved_pdf_path,
        trial_balance_pdf_stderr_path=stderr_path,
        second_page_command_id="bridge-sale",
        actor_prefix="bridge",
        open_book_mode="book-key-file",
        entity_name="Acme Studio",
        business_activity_tags=["consulting-services"],
        functional_currency="EUR",
        fiscal_year_start="01-01",
    )


def smoke_path(local_root: pathlib.Path, relative_path: pathlib.Path):
    smoke_path_type = resolve_smoke_path_type()
    return smoke_path_type(
        relative_path=relative_path,
        local_path=local_root / relative_path,
        argument=str(local_root / relative_path),
    )


def resolve_smoke_path_type():
    from release_smoke_workflow.models import SmokePath

    return SmokePath


def pdf_export_stderr(reported_path: str) -> str:
    return (
        "Info\n"
        "====\n\n"
        "Code     : pdf-exported\n"
        f"Message  : Wrote the requested PDF report artifact to {reported_path}\n"
        "Argument : --pdf-out\n"
    )


def structured_account_ledger_csv(actor_prefix: str) -> str:
    header = [
        "recordKind",
        "accountCode",
        "accountName",
        "accountType",
        "accountRole",
        "normalBalance",
        "active",
        "effectiveDateFrom",
        "effectiveDateTo",
        "currencyCode",
        "openingDebitTotal",
        "openingCreditTotal",
        "openingNetAmount",
        "openingBalanceSide",
        "closingDebitTotal",
        "closingCreditTotal",
        "closingNetAmount",
        "closingBalanceSide",
        "effectiveDate",
        "recordedAt",
        "postingId",
        "postingKind",
        "postingOriginKind",
        "reversalState",
        "reversalTarget",
        "debitAmount",
        "creditAmount",
        "runningNetAmount",
        "runningBalanceSide",
        "counterpartAccountCode",
        "sourceDocumentId",
        "sourceDocumentType",
        "approvalId",
        "approvalDecision",
        "message",
    ]
    summary = row("summary")
    summary.update(
        currencyCode="EUR",
        openingDebitTotal="0.00",
        openingCreditTotal="0.00",
        openingNetAmount="0.00",
        openingBalanceSide="ZERO",
        closingDebitTotal="10.00",
        closingCreditTotal="4.00",
        closingNetAmount="6.00",
        closingBalanceSide="DEBIT",
    )
    opening_entry = movement_row(
        "2026-04-07",
        "2026-04-07T10:00:00Z",
        "019e2ae5-5f56-7025-8449-984160a327f3",
        "CASH_REVENUE",
        debit="10.00",
        credit="0.00",
        running_net="10.00",
    )
    adjustment_entry = movement_row(
        "2026-04-08",
        "2026-04-08T10:00:00Z",
        "019e2ae5-6557-7410-8611-f55876f12ca5",
        "CORRECTION_ADJUSTMENT",
        debit="0.00",
        credit="4.00",
        running_net="6.00",
    )
    rows = [
        summary,
        opening_entry,
        row(
            "counterpart-account",
            effectiveDate="2026-04-07",
            recordedAt="2026-04-07T10:00:00Z",
            postingId="019e2ae5-5f56-7025-8449-984160a327f3",
            postingKind="STANDARD",
            postingOriginKind="CASH_REVENUE",
            reversalState="direct",
            counterpartAccountCode="2000",
        ),
        row(
            "source-document",
            effectiveDate="2026-04-07",
            recordedAt="2026-04-07T10:00:00Z",
            postingId="019e2ae5-5f56-7025-8449-984160a327f3",
            postingKind="STANDARD",
            postingOriginKind="CASH_REVENUE",
            reversalState="direct",
            sourceDocumentId=f"{actor_prefix}-sale-document-1",
            sourceDocumentType="cash-receipt",
        ),
        adjustment_entry,
        row(
            "counterpart-account",
            effectiveDate="2026-04-08",
            recordedAt="2026-04-08T10:00:00Z",
            postingId="019e2ae5-6557-7410-8611-f55876f12ca5",
            postingKind="STANDARD",
            postingOriginKind="CORRECTION_ADJUSTMENT",
            reversalState="direct",
            counterpartAccountCode="2000",
        ),
        row(
            "source-document",
            effectiveDate="2026-04-08",
            recordedAt="2026-04-08T10:00:00Z",
            postingId="019e2ae5-6557-7410-8611-f55876f12ca5",
            postingKind="STANDARD",
            postingOriginKind="CORRECTION_ADJUSTMENT",
            reversalState="direct",
            sourceDocumentId=f"{actor_prefix}-adjustment-document-1",
            sourceDocumentType="cash-receipt",
        ),
    ]
    buffer = StringIO()
    writer = csv.DictWriter(buffer, fieldnames=header, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return buffer.getvalue()


def movement_row(
    effective_date: str,
    recorded_at: str,
    posting_id: str,
    posting_origin_kind: str,
    *,
    debit: str,
    credit: str,
    running_net: str,
) -> dict[str, str]:
    return row(
        "entry",
        currencyCode="EUR",
        effectiveDate=effective_date,
        recordedAt=recorded_at,
        postingId=posting_id,
        postingKind="STANDARD",
        postingOriginKind=posting_origin_kind,
        reversalState="direct",
        debitAmount=debit,
        creditAmount=credit,
        runningNetAmount=running_net,
        runningBalanceSide="DEBIT",
    )


def row(record_kind: str, **overrides: str) -> dict[str, str]:
    base = {
        "recordKind": record_kind,
        "accountCode": "1000",
        "accountName": "Cash",
        "accountType": "ASSET",
        "accountRole": "ORDINARY",
        "normalBalance": "DEBIT",
        "active": "true",
        "effectiveDateFrom": "2026-04-07",
        "effectiveDateTo": "2026-04-08",
        "currencyCode": "",
        "openingDebitTotal": "",
        "openingCreditTotal": "",
        "openingNetAmount": "",
        "openingBalanceSide": "",
        "closingDebitTotal": "",
        "closingCreditTotal": "",
        "closingNetAmount": "",
        "closingBalanceSide": "",
        "effectiveDate": "",
        "recordedAt": "",
        "postingId": "",
        "postingKind": "",
        "postingOriginKind": "",
        "reversalState": "",
        "reversalTarget": "",
        "debitAmount": "",
        "creditAmount": "",
        "runningNetAmount": "",
        "runningBalanceSide": "",
        "counterpartAccountCode": "",
        "sourceDocumentId": "",
        "sourceDocumentType": "",
        "approvalId": "",
        "approvalDecision": "",
        "message": "",
    }
    base.update(overrides)
    return base


STANDARD_LIST_POSTINGS_TEXT = (
    "Postings\n"
    "========\n\n"
    "Returned postings : 2\n\n"
    "2026-04-08 | Direct | posting-2\n"
    "Recorded at      : 2026-04-08 10:00:00 UTC\n"
    "Debit total      : 4.00\n\n"
    "2026-04-07 | Direct | posting-1\n"
    "Recorded at      : 2026-04-07 10:00:00 UTC\n"
    "Debit total      : 10.00\n"
)
STANDARD_ACCOUNT_BALANCE_TEXT = "Account Balance\nAccount : 1000\nNet     : 6.00\n"
STANDARD_TRIAL_BALANCE_TEXT = "Trial Balance\nAs of : 2026-04-08\n1000 | 6.00\n"
STANDARD_PERIOD_SUMMARY_TEXT = "Period Summary\nPosting count : 2\n"


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
