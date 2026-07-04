from __future__ import annotations

from .models import ReleaseSmokeConfig
from .support import require, require_match

POSTING_ID_PATTERN = r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
RECORDED_AT_PATTERN = r"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]+)?Z$"


def assert_entry_row(
    config: ReleaseSmokeConfig,
    entry: dict[str, str],
    *,
    effective_date: str,
    debit_amount: str,
    credit_amount: str,
    running_net_amount: str,
    running_balance_side: str,
    row_name: str,
) -> None:
    require(
        entry["rowId"] == "ledger-entry:" + entry["postingId"]
        and entry["parentRowId"] == ""
        and entry["effectiveDateFrom"] == "2026-04-07"
        and entry["effectiveDateTo"] == "2026-04-08"
        and entry["currencyCode"] == config.functional_currency
        and entry["postingKind"] == "STANDARD"
        and entry["reversalState"] == "direct"
        and entry["reversalTarget"] == "",
        f"{config.label} account-ledger CSV output did not render stable row identity for the {row_name}",
    )
    require(
        entry["effectiveDate"] == effective_date
        and entry["debitAmount"] == debit_amount
        and entry["creditAmount"] == credit_amount
        and entry["runningNetAmount"] == running_net_amount
        and entry["runningBalanceSide"] == running_balance_side,
        f"{config.label} account-ledger CSV output did not render the expected movement values for the {row_name}",
    )
    require_match(
        entry["postingId"],
        POSTING_ID_PATTERN,
        f"{config.label} account-ledger CSV output did not render a canonical posting identifier for the {row_name}",
    )
    require_match(
        entry["recordedAt"],
        RECORDED_AT_PATTERN,
        f"{config.label} account-ledger CSV output did not render one recorded-at timestamp for the {row_name}",
    )


def assert_counterpart_row(
    config: ReleaseSmokeConfig,
    rows: list[dict[str, str]],
    entry: dict[str, str],
    *,
    expected_counterpart: str,
    row_name: str,
) -> None:
    counterpart_rows = [
        row
        for row in rows
        if row["relationKind"] == "counterpart-account" and row["parentRowId"] == entry["rowId"]
    ]
    require(
        len(counterpart_rows) == 1,
        f"{config.label} account-ledger CSV output did not render exactly one counterpart row for the {row_name}",
    )
    counterpart_row = counterpart_rows[0]
    require(
        counterpart_row["rowId"]
        == f"ledger-counterpart:{entry['postingId']}:{expected_counterpart}"
        and counterpart_row["counterpartAccountCode"] == expected_counterpart
        and counterpart_row["effectiveDate"] == entry["effectiveDate"]
        and counterpart_row["recordedAt"] == entry["recordedAt"]
        and counterpart_row["postingId"] == entry["postingId"]
        and counterpart_row["postingOriginKind"] == entry["postingOriginKind"],
        f"{config.label} account-ledger CSV output did not render the expected counterpart linkage for the {row_name}",
    )


def assert_source_document_row(
    config: ReleaseSmokeConfig,
    rows: list[dict[str, str]],
    entry: dict[str, str],
    *,
    expected_source_document_id: str,
    expected_source_document_type: str,
    row_name: str,
) -> None:
    source_document_rows = [
        row
        for row in rows
        if row["relationKind"] == "source-document" and row["parentRowId"] == entry["rowId"]
    ]
    require(
        len(source_document_rows) == 1,
        f"{config.label} account-ledger CSV output did not render exactly one source-document row for the {row_name}",
    )
    source_document_row = source_document_rows[0]
    require(
        source_document_row["rowId"]
        == f"ledger-source-document:{entry['postingId']}:{expected_source_document_id}"
        and source_document_row["sourceDocumentId"] == expected_source_document_id
        and source_document_row["sourceDocumentType"] == expected_source_document_type
        and source_document_row["effectiveDate"] == entry["effectiveDate"]
        and source_document_row["recordedAt"] == entry["recordedAt"]
        and source_document_row["postingId"] == entry["postingId"]
        and source_document_row["postingOriginKind"] == entry["postingOriginKind"],
        f"{config.label} account-ledger CSV output did not render the expected source-document linkage for the {row_name}",
    )
