from __future__ import annotations

from .account_ledger_csv_contract import ACCOUNT_LEDGER_CSV_HEADER
from .account_ledger_row_assertions import (
    assert_counterpart_rows,
    assert_entry_row,
    assert_source_document_rows,
    assert_summary_row,
)
from .csv_support import parse_csv_rows
from .models import ReleaseSmokeConfig
from .support import require


def assert_account_ledger_csv(config: ReleaseSmokeConfig, account_ledger_csv_output: str) -> None:
    header, rows = parse_csv_rows(
        account_ledger_csv_output,
        f"{config.label} account-ledger CSV output",
    )
    require(
        header == ACCOUNT_LEDGER_CSV_HEADER,
        f"{config.label} account-ledger CSV output did not render the expected header",
    )
    row_groups = _group_rows(config, rows)
    assert_summary_row(config, row_groups["summary"][0])
    opening_entry = next(
        row for row in row_groups["entry"] if row["postingOriginKind"] == "CASH_REVENUE"
    )
    expense_entry = next(
        row for row in row_groups["entry"] if row["postingOriginKind"] == "CASH_EXPENSE"
    )
    assert_entry_row(
        config,
        opening_entry,
        effective_date="2026-04-07",
        posting_origin_kind="CASH_REVENUE",
        debit_amount="10.00",
        credit_amount="0.00",
        running_net_amount="10.00",
        row_name="opening ledger movement row",
    )
    assert_entry_row(
        config,
        expense_entry,
        effective_date="2026-04-08",
        posting_origin_kind="CASH_EXPENSE",
        debit_amount="0.00",
        credit_amount="4.00",
        running_net_amount="6.00",
        row_name="running-balance expense row",
    )
    assert_counterpart_rows(config, row_groups["counterpart-account"], opening_entry, expense_entry)
    assert_source_document_rows(
        config,
        row_groups["source-document"],
        opening_entry,
        expense_entry,
    )


def _group_rows(
    config: ReleaseSmokeConfig, rows: list[dict[str, str]]
) -> dict[str, list[dict[str, str]]]:
    require(
        len(rows) == 7,
        f"{config.label} account-ledger CSV output did not render the expected normalized row count",
    )
    grouped = {
        "summary": [row for row in rows if row["recordKind"] == "summary"],
        "entry": [row for row in rows if row["recordKind"] == "entry"],
        "counterpart-account": [row for row in rows if row["recordKind"] == "counterpart-account"],
        "source-document": [row for row in rows if row["recordKind"] == "source-document"],
        "approval": [row for row in rows if row["recordKind"] == "approval"],
    }
    require(
        len(grouped["summary"]) == 1
        and len(grouped["entry"]) == 2
        and len(grouped["counterpart-account"]) == 2
        and len(grouped["source-document"]) == 2
        and len(grouped["approval"]) == 0,
        f"{config.label} account-ledger CSV output did not render the expected row kinds",
    )
    return grouped
