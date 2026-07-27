"""Standard-commercial and opening-position typed-record fixture requests."""

from __future__ import annotations

from .typed_record_fixture_support import _record_request
from .typed_record_models import TypedRecordRequest
from .typed_record_payloads import _money

_BOOK_START_DATE = "2026-01-01"


def _standard_commercial_requests(request_prefix: str) -> tuple[TypedRecordRequest, ...]:
    return (
        _record_request(
            "recordOwnerContribution",
            request_prefix,
            "record-owner-contribution",
            "OWNER_CONTRIBUTION",
            "2026-01-02",
            "owner-contribution",
            {
                "cashAccountCode": "cash",
                "equityAccountCode": "owner-capital",
                "amount": _money("10000"),
            },
        ),
        _record_request(
            "recordSaleSettled",
            request_prefix,
            "record-sale-settled",
            "SALE_SETTLED",
            "2026-01-03",
            "cash-receipt",
            {
                "cashAccountCode": "cash",
                "revenueAccountCode": "service-revenue",
                "amount": _money("1000"),
            },
        ),
        _record_request(
            "recordSaleOnCredit",
            request_prefix,
            "record-sale-on-credit",
            "SALE_ON_CREDIT",
            "2026-01-04",
            "invoice",
            {
                "receivableAccountCode": "accounts-receivable",
                "revenueAccountCode": "service-revenue",
                "amount": _money("1200"),
            },
        ),
        _record_request(
            "recordReceipt",
            request_prefix,
            "record-receipt",
            "RECEIPT",
            "2026-01-05",
            "cash-receipt",
            {
                "cashAccountCode": "cash",
                "receivableAccountCode": "accounts-receivable",
                "amount": _money("1200"),
            },
        ),
        _record_request(
            "recordExpenseSettled",
            request_prefix,
            "record-expense-settled",
            "EXPENSE_SETTLED",
            "2026-01-06",
            "expense-receipt",
            {
                "cashAccountCode": "cash",
                "expenseAccountCode": "operating-expense",
                "amount": _money("500"),
            },
        ),
        _record_request(
            "recordExpenseOnCredit",
            request_prefix,
            "record-expense-on-credit",
            "EXPENSE_ON_CREDIT",
            "2026-01-07",
            "bill",
            {
                "payableAccountCode": "accounts-payable",
                "expenseAccountCode": "operating-expense",
                "amount": _money("600"),
            },
        ),
        _record_request(
            "recordPayment",
            request_prefix,
            "record-payment",
            "PAYMENT",
            "2026-01-08",
            "cash-disbursement",
            {
                "cashAccountCode": "cash",
                "payableAccountCode": "accounts-payable",
                "amount": _money("600"),
            },
        ),
        _record_request(
            "recordOwnerWithdrawal",
            request_prefix,
            "record-owner-withdrawal",
            "OWNER_WITHDRAWAL",
            "2026-01-09",
            "owner-withdrawal",
            {
                "cashAccountCode": "cash",
                "equityAccountCode": "owner-draws",
                "amount": _money("300"),
            },
        ),
    )


def _opening_position_requests(request_prefix: str) -> tuple[TypedRecordRequest, ...]:
    return (
        _record_request(
            "recordOpeningPosition",
            request_prefix,
            "record-opening-position",
            "OPENING_POSITION",
            _BOOK_START_DATE,
            "opening-balance-support",
            {
                "openingBalances": [
                    {"accountCode": "cash", "side": "DEBIT", "amount": _money("10000")},
                    {
                        "accountCode": "owner-capital",
                        "side": "CREDIT",
                        "amount": _money("10000"),
                    },
                ]
            },
        ),
    )
