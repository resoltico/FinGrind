from __future__ import annotations

import csv
from io import StringIO

from .account_ledger_csv_contract import (
    ACCOUNT_LEDGER_CSV_HEADER,
    account_ledger_base_row,
    account_ledger_movement_row,
)


def structured_account_ledger_csv(actor_prefix: str) -> str:
    opening_posting_id = "019e2ae5-5f56-7025-8449-984160a327f3"
    adjustment_posting_id = "019e2ae5-6557-7410-8611-f55876f12ca5"
    sale_document_id = f"{actor_prefix}-sale-document-1"
    adjustment_document_id = f"{actor_prefix}-adjustment-document-1"

    summary = account_ledger_base_row(
        "summary",
        exportFamily="posting-relationships",
        rowId="ledger-summary:1000:EUR",
        relationKind="ledger-summary",
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
    opening_entry = account_ledger_movement_row(
        "2026-04-07",
        "2026-04-07T10:00:00Z",
        opening_posting_id,
        "CASH_REVENUE",
        debit="10.00",
        credit="0.00",
        running_net="10.00",
    )
    adjustment_entry = account_ledger_movement_row(
        "2026-04-08",
        "2026-04-08T10:00:00Z",
        adjustment_posting_id,
        "CORRECTION_ADJUSTMENT",
        debit="0.00",
        credit="4.00",
        running_net="6.00",
    )
    rows = [
        summary,
        opening_entry,
        account_ledger_base_row(
            "counterpart-account",
            exportFamily="posting-relationships",
            rowId=f"ledger-counterpart:{opening_posting_id}:2000",
            parentRowId=f"ledger-entry:{opening_posting_id}",
            relationKind="counterpart-account",
            effectiveDate="2026-04-07",
            recordedAt="2026-04-07T10:00:00Z",
            postingId=opening_posting_id,
            postingKind="STANDARD",
            postingOriginKind="CASH_REVENUE",
            reversalState="direct",
            counterpartAccountCode="2000",
        ),
        account_ledger_base_row(
            "source-document",
            exportFamily="posting-relationships",
            rowId=f"ledger-source-document:{opening_posting_id}:{sale_document_id}",
            parentRowId=f"ledger-entry:{opening_posting_id}",
            relationKind="source-document",
            effectiveDate="2026-04-07",
            recordedAt="2026-04-07T10:00:00Z",
            postingId=opening_posting_id,
            postingKind="STANDARD",
            postingOriginKind="CASH_REVENUE",
            reversalState="direct",
            sourceDocumentId=sale_document_id,
            sourceDocumentType="cash-receipt",
        ),
        adjustment_entry,
        account_ledger_base_row(
            "counterpart-account",
            exportFamily="posting-relationships",
            rowId=f"ledger-counterpart:{adjustment_posting_id}:2000",
            parentRowId=f"ledger-entry:{adjustment_posting_id}",
            relationKind="counterpart-account",
            effectiveDate="2026-04-08",
            recordedAt="2026-04-08T10:00:00Z",
            postingId=adjustment_posting_id,
            postingKind="STANDARD",
            postingOriginKind="CORRECTION_ADJUSTMENT",
            reversalState="direct",
            counterpartAccountCode="2000",
        ),
        account_ledger_base_row(
            "source-document",
            exportFamily="posting-relationships",
            rowId=f"ledger-source-document:{adjustment_posting_id}:{adjustment_document_id}",
            parentRowId=f"ledger-entry:{adjustment_posting_id}",
            relationKind="source-document",
            effectiveDate="2026-04-08",
            recordedAt="2026-04-08T10:00:00Z",
            postingId=adjustment_posting_id,
            postingKind="STANDARD",
            postingOriginKind="CORRECTION_ADJUSTMENT",
            reversalState="direct",
            sourceDocumentId=adjustment_document_id,
            sourceDocumentType="cash-receipt",
        ),
    ]
    buffer = StringIO()
    writer = csv.DictWriter(buffer, fieldnames=ACCOUNT_LEDGER_CSV_HEADER, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return buffer.getvalue()
