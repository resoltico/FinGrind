from __future__ import annotations

import csv
from io import StringIO

from .account_ledger_assertions import ACCOUNT_LEDGER_CSV_HEADER

SALE_POSTING_ID = "019e2ae5-5f56-7025-8449-984160a327f3"
EXPENSE_POSTING_ID = "019e2ae5-6557-7410-8611-f55876f12ca5"


def structured_account_ledger_csv() -> str:
    rows = [
        {
            "family": "account-ledger",
            "accountCode": "cash",
            "postingId": SALE_POSTING_ID,
            "effectiveDate": "2026-04-07",
            "movementCurrencyCode": "EUR",
            "debitTotalCurrencyCode": "EUR",
            "debitTotalMinorUnits": "1000",
            "creditTotalCurrencyCode": "EUR",
            "creditTotalMinorUnits": "0",
            "netAmountCurrencyCode": "EUR",
            "netAmountMinorUnits": "1000",
            "balanceSide": "DEBIT",
            "runningNetAmountCurrencyCode": "EUR",
            "runningNetAmountMinorUnits": "1000",
            "runningBalanceSide": "DEBIT",
        },
        {
            "family": "account-ledger",
            "accountCode": "cash",
            "postingId": EXPENSE_POSTING_ID,
            "effectiveDate": "2026-04-08",
            "movementCurrencyCode": "EUR",
            "debitTotalCurrencyCode": "EUR",
            "debitTotalMinorUnits": "0",
            "creditTotalCurrencyCode": "EUR",
            "creditTotalMinorUnits": "400",
            "netAmountCurrencyCode": "EUR",
            "netAmountMinorUnits": "400",
            "balanceSide": "CREDIT",
            "runningNetAmountCurrencyCode": "EUR",
            "runningNetAmountMinorUnits": "600",
            "runningBalanceSide": "DEBIT",
        },
    ]
    buffer = StringIO()
    writer = csv.DictWriter(buffer, fieldnames=ACCOUNT_LEDGER_CSV_HEADER, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return buffer.getvalue()
