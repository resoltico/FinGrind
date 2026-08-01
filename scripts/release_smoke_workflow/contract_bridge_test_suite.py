from __future__ import annotations

import pathlib

from .account_ledger_csv_samples import structured_account_ledger_csv
from .assertions import assert_operator_queries_and_reports
from .bridge_contract_support import base_bridge_config, smoke_path, write_bridge_script
from .bridge_report_contract import assert_bridge_and_report_contracts
from .cli import run_cli_allow_failure, run_cli_with_split_streams
from .path_support import extract_pdf_artifact_path, normalize_reported_path
from .report_text_samples import (
    STANDARD_ACCOUNT_BALANCE_TEXT,
    STANDARD_LIST_POSTINGS_TEXT,
    STANDARD_PERIOD_SUMMARY_TEXT,
    STANDARD_TRIAL_BALANCE_TEXT,
    pdf_export_stdout,
)


def run_bridge_and_report_contracts(repo_root: pathlib.Path) -> None:
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
