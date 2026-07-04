from __future__ import annotations

import csv
from io import StringIO

from .account_ledger_csv_contract import ACCOUNT_LEDGER_CSV_HEADER, account_ledger_csv_row

SALE_POSTING_ID = "019e2ae5-5f56-7025-8449-984160a327f3"
EXPENSE_POSTING_ID = "019e2ae5-6557-7410-8611-f55876f12ca5"


def structured_account_ledger_csv(actor_prefix: str) -> str:
    rows = [
        account_ledger_csv_row(
            row_id="ledger-summary:cash:EUR",
            relation_kind="ledger-summary",
            effective_date_from="2026-04-07",
            effective_date_to="2026-04-08",
            currency_code="EUR",
            opening_debit_total="0.00",
            opening_credit_total="0.00",
            opening_net_amount="0.00",
            opening_balance_side="ZERO",
            closing_debit_total="10.00",
            closing_credit_total="4.00",
            closing_net_amount="6.00",
            closing_balance_side="DEBIT",
        ),
        *_posting_rows(
            posting_id=SALE_POSTING_ID,
            effective_date="2026-04-07",
            recorded_at="2026-07-02T11:36:35.587884Z",
            posting_origin_kind="SALE_SETTLED",
            debit_amount="10.00",
            credit_amount="0.00",
            running_net_amount="10.00",
            running_balance_side="DEBIT",
            counterpart_account_code="service-revenue",
            source_document_id=f"{actor_prefix}-sale-document-1",
            source_document_type="cash-receipt",
        ),
        *_posting_rows(
            posting_id=EXPENSE_POSTING_ID,
            effective_date="2026-04-08",
            recorded_at="2026-07-02T11:36:36.842864Z",
            posting_origin_kind="EXPENSE_SETTLED",
            debit_amount="0.00",
            credit_amount="4.00",
            running_net_amount="6.00",
            running_balance_side="DEBIT",
            counterpart_account_code="misc-expense",
            source_document_id=f"{actor_prefix}-expense-document-1",
            source_document_type="expense-receipt",
        ),
    ]
    buffer = StringIO()
    writer = csv.DictWriter(buffer, fieldnames=ACCOUNT_LEDGER_CSV_HEADER, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return buffer.getvalue()


def _posting_rows(
    *,
    posting_id: str,
    effective_date: str,
    recorded_at: str,
    posting_origin_kind: str,
    debit_amount: str,
    credit_amount: str,
    running_net_amount: str,
    running_balance_side: str,
    counterpart_account_code: str,
    source_document_id: str,
    source_document_type: str,
) -> list[dict[str, str]]:
    entry_row_id = "ledger-entry:" + posting_id
    return [
        account_ledger_csv_row(
            row_id=entry_row_id,
            relation_kind="entry",
            effective_date_from="2026-04-07",
            effective_date_to="2026-04-08",
            currency_code="EUR",
            effective_date=effective_date,
            recorded_at=recorded_at,
            posting_id=posting_id,
            posting_kind="STANDARD",
            posting_origin_kind=posting_origin_kind,
            reversal_state="direct",
            debit_amount=debit_amount,
            credit_amount=credit_amount,
            running_net_amount=running_net_amount,
            running_balance_side=running_balance_side,
        ),
        account_ledger_csv_row(
            row_id=f"ledger-counterpart:{posting_id}:{counterpart_account_code}",
            parent_row_id=entry_row_id,
            relation_kind="counterpart-account",
            effective_date_from="2026-04-07",
            effective_date_to="2026-04-08",
            effective_date=effective_date,
            recorded_at=recorded_at,
            posting_id=posting_id,
            posting_kind="STANDARD",
            posting_origin_kind=posting_origin_kind,
            reversal_state="direct",
            counterpart_account_code=counterpart_account_code,
        ),
        account_ledger_csv_row(
            row_id=f"ledger-source-document:{posting_id}:{source_document_id}",
            parent_row_id=entry_row_id,
            relation_kind="source-document",
            effective_date_from="2026-04-07",
            effective_date_to="2026-04-08",
            effective_date=effective_date,
            recorded_at=recorded_at,
            posting_id=posting_id,
            posting_kind="STANDARD",
            posting_origin_kind=posting_origin_kind,
            reversal_state="direct",
            source_document_id=source_document_id,
            source_document_type=source_document_type,
        ),
    ]
