from __future__ import annotations

from .account_ledger_csv_contract import ACCOUNT_LEDGER_CSV_HEADER
from .account_ledger_row_assertions import (
    assert_counterpart_row,
    assert_entry_row,
    assert_source_document_row,
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
    require(
        rows
        and all(
            row["exportFamily"] == "account-ledger"
            and row["recordKind"] == "account-ledger"
            and row["accountCode"] == config.starter_cash_account_code
            and row["accountName"] == config.starter_cash_account_name
            and row["accountType"] == "ASSET"
            and row["normalBalance"] == "DEBIT"
            and row["active"] == "true"
            for row in rows
        ),
        f"{config.label} account-ledger CSV output did not stay anchored to the shared report family",
    )
    summary_rows = [row for row in rows if row["relationKind"] == "ledger-summary"]
    require(
        len(summary_rows) == 1,
        f"{config.label} account-ledger CSV output did not render exactly one summary row",
    )
    summary = summary_rows[0]
    require(
        summary["rowId"] == f"ledger-summary:{config.starter_cash_account_code}:EUR"
        and summary["parentRowId"] == ""
        and summary["effectiveDateFrom"] == "2026-04-07"
        and summary["effectiveDateTo"] == "2026-04-08"
        and summary["currencyCode"] == config.functional_currency
        and summary["openingDebitTotal"] == "0.00"
        and summary["openingCreditTotal"] == "0.00"
        and summary["openingNetAmount"] == "0.00"
        and summary["openingBalanceSide"] == "ZERO"
        and summary["closingDebitTotal"] == "10.00"
        and summary["closingCreditTotal"] == "4.00"
        and summary["closingNetAmount"] == "6.00"
        and summary["closingBalanceSide"] == "DEBIT",
        f"{config.label} account-ledger CSV summary row did not render the expected horizon and balances",
    )
    entry_rows = [row for row in rows if row["relationKind"] == "entry"]
    require(
        len(entry_rows) == 2,
        f"{config.label} account-ledger CSV output did not render exactly two ledger entry rows",
    )
    sale_entry = next(row for row in entry_rows if row["postingOriginKind"] == "SALE_SETTLED")
    expense_entry = next(row for row in entry_rows if row["postingOriginKind"] == "EXPENSE_SETTLED")
    assert_entry_row(
        config,
        sale_entry,
        effective_date="2026-04-07",
        debit_amount="10.00",
        credit_amount="0.00",
        running_net_amount="10.00",
        running_balance_side="DEBIT",
        row_name="sale ledger entry row",
    )
    assert_entry_row(
        config,
        expense_entry,
        effective_date="2026-04-08",
        debit_amount="0.00",
        credit_amount="4.00",
        running_net_amount="6.00",
        running_balance_side="DEBIT",
        row_name="expense ledger entry row",
    )
    assert_counterpart_row(
        config,
        rows,
        sale_entry,
        expected_counterpart=config.starter_revenue_account_code,
        row_name="sale counterpart row",
    )
    assert_counterpart_row(
        config,
        rows,
        expense_entry,
        expected_counterpart=config.expense_supplement_account_code,
        row_name="expense counterpart row",
    )
    assert_source_document_row(
        config,
        rows,
        sale_entry,
        expected_source_document_id=f"{config.actor_prefix}-sale-document-1",
        expected_source_document_type="cash-receipt",
        row_name="sale source-document row",
    )
    assert_source_document_row(
        config,
        rows,
        expense_entry,
        expected_source_document_id=f"{config.actor_prefix}-expense-document-1",
        expected_source_document_type="expense-receipt",
        row_name="expense source-document row",
    )
