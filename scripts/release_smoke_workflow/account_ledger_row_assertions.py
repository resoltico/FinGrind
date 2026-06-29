from __future__ import annotations

from .artifact_contracts import expected_source_document
from .models import ReleaseSmokeConfig
from .support import require, require_match

POSTING_ID_PATTERN = r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
RECORDED_AT_PATTERN = r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$"


def assert_summary_row(config: ReleaseSmokeConfig, row: dict[str, str]) -> None:
    require_match(
        row["accountCode"],
        r"^" + config.starter_cash_account_code + r"$",
        f"{config.label} account-ledger CSV summary row did not stay anchored to the requested account",
    )
    require(
        row["exportFamily"] == "account-ledger"
        and row["rowId"] == "ledger-summary:" + config.starter_cash_account_code + ":EUR"
        and row["parentRowId"] == ""
        and row["relationKind"] == "ledger-summary"
        and row["accountName"] == config.starter_cash_account_name
        and row["accountType"] == "ASSET"
        and row["normalBalance"] == "DEBIT"
        and row["active"] == "true"
        and row["effectiveDateFrom"] == "2026-04-07"
        and row["effectiveDateTo"] == "2026-04-08"
        and row["currencyCode"] == "EUR"
        and row["openingDebitTotal"] == "0.00"
        and row["openingCreditTotal"] == "0.00"
        and row["openingNetAmount"] == "0.00"
        and row["openingBalanceSide"] == "ZERO"
        and row["closingDebitTotal"] == "10.00"
        and row["closingCreditTotal"] == "4.00"
        and row["closingNetAmount"] == "6.00"
        and row["closingBalanceSide"] == "DEBIT"
        and row["effectiveDate"] == ""
        and row["recordedAt"] == ""
        and row["postingId"] == ""
        and row["counterpartAccountCode"] == ""
        and row["sourceDocumentId"] == ""
        and row["approvalId"] == "",
        f"{config.label} account-ledger CSV summary row did not render the expected opening and closing balances",
    )


def assert_entry_row(
    config: ReleaseSmokeConfig,
    row: dict[str, str],
    *,
    effective_date: str,
    posting_origin_kind: str,
    debit_amount: str,
    credit_amount: str,
    running_net_amount: str,
    row_name: str,
) -> None:
    require(
        row["exportFamily"] == "account-ledger"
        and row["rowId"] == "ledger-entry:" + row["postingId"]
        and row["parentRowId"] == ""
        and row["relationKind"] == "entry"
        and row["accountCode"] == config.starter_cash_account_code
        and row["accountName"] == config.starter_cash_account_name
        and row["accountType"] == "ASSET"
        and row["normalBalance"] == "DEBIT"
        and row["active"] == "true"
        and row["effectiveDateFrom"] == "2026-04-07"
        and row["effectiveDateTo"] == "2026-04-08"
        and row["currencyCode"] == "EUR"
        and row["effectiveDate"] == effective_date
        and row["recordedAt"] != ""
        and row["postingId"] != ""
        and row["postingKind"] == "STANDARD"
        and row["postingOriginKind"] == posting_origin_kind
        and row["reversalState"] == "direct"
        and row["reversalTarget"] == ""
        and row["debitAmount"] == debit_amount
        and row["creditAmount"] == credit_amount
        and row["runningNetAmount"] == running_net_amount
        and row["runningBalanceSide"] == "DEBIT"
        and row["counterpartAccountCode"] == ""
        and row["sourceDocumentId"] == ""
        and row["approvalId"] == "",
        f"{config.label} account-ledger CSV output did not render the {row_name}",
    )
    require_match(
        row["postingId"],
        POSTING_ID_PATTERN,
        f"{config.label} account-ledger CSV output did not render a canonical posting identifier for the {row_name}",
    )
    require_match(
        row["recordedAt"],
        RECORDED_AT_PATTERN,
        f"{config.label} account-ledger CSV output did not render a canonical recordedAt timestamp for the {row_name}",
    )


def assert_counterpart_rows(
    config: ReleaseSmokeConfig,
    counterpart_rows: list[dict[str, str]],
    opening_entry: dict[str, str],
    expense_entry: dict[str, str],
) -> None:
    opening_counterpart = next(
        row for row in counterpart_rows if row["postingId"] == opening_entry["postingId"]
    )
    expense_counterpart = next(
        row for row in counterpart_rows if row["postingId"] == expense_entry["postingId"]
    )
    require(
        opening_counterpart["exportFamily"] == "account-ledger"
        and opening_counterpart["rowId"]
        == "ledger-counterpart:"
        + opening_entry["postingId"]
        + ":"
        + config.starter_revenue_account_code
        and opening_counterpart["parentRowId"] == "ledger-entry:" + opening_entry["postingId"]
        and opening_counterpart["relationKind"] == "counterpart-account"
        and opening_counterpart["counterpartAccountCode"] == config.starter_revenue_account_code
        and opening_counterpart["effectiveDate"] == "2026-04-07"
        and expense_counterpart["exportFamily"] == "account-ledger"
        and expense_counterpart["rowId"]
        == "ledger-counterpart:"
        + expense_entry["postingId"]
        + ":"
        + config.expense_supplement_account_code
        and expense_counterpart["parentRowId"] == "ledger-entry:" + expense_entry["postingId"]
        and expense_counterpart["relationKind"] == "counterpart-account"
        and expense_counterpart["counterpartAccountCode"] == config.expense_supplement_account_code
        and expense_counterpart["effectiveDate"] == "2026-04-08",
        f"{config.label} account-ledger CSV output did not render normalized counterpart-account rows",
    )


def assert_source_document_rows(
    config: ReleaseSmokeConfig,
    source_document_rows: list[dict[str, str]],
    opening_entry: dict[str, str],
    expense_entry: dict[str, str],
) -> None:
    sale_document = expected_source_document(config.actor_prefix, "sale", "2026-04-07")
    expense_document = expected_source_document(config.actor_prefix, "expense", "2026-04-08")
    opening_source_document = next(
        row for row in source_document_rows if row["postingId"] == opening_entry["postingId"]
    )
    expense_source_document = next(
        row for row in source_document_rows if row["postingId"] == expense_entry["postingId"]
    )
    require(
        opening_source_document["exportFamily"] == "account-ledger"
        and opening_source_document["rowId"]
        == "ledger-source-document:"
        + opening_entry["postingId"]
        + ":"
        + sale_document["sourceDocumentId"]
        and opening_source_document["parentRowId"] == "ledger-entry:" + opening_entry["postingId"]
        and opening_source_document["relationKind"] == "source-document"
        and opening_source_document["sourceDocumentId"] == sale_document["sourceDocumentId"]
        and opening_source_document["sourceDocumentType"] == sale_document["sourceDocumentType"]
        and expense_source_document["exportFamily"] == "account-ledger"
        and expense_source_document["rowId"]
        == "ledger-source-document:"
        + expense_entry["postingId"]
        + ":"
        + expense_document["sourceDocumentId"]
        and expense_source_document["parentRowId"] == "ledger-entry:" + expense_entry["postingId"]
        and expense_source_document["relationKind"] == "source-document"
        and expense_source_document["sourceDocumentId"] == expense_document["sourceDocumentId"]
        and expense_source_document["sourceDocumentType"] == expense_document["sourceDocumentType"],
        f"{config.label} account-ledger CSV output did not render normalized source-document rows",
    )
