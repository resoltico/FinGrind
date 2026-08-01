"""Accrual-lifecycle typed-record fixture requests."""

from __future__ import annotations

from .typed_record_models import TypedRecordRequest
from .typed_record_payloads import _money, _posting_request

_PREPAYMENT_CUTOFF_ID = "prepayment-2026-q1"
_DEFERRED_REVENUE_CUTOFF_ID = "deferred-revenue-2026-q1"
_ACCRUED_EXPENSE_CUTOFF_ID = "accrued-expense-2026-01"


def _accrual_requests(request_prefix: str) -> tuple[TypedRecordRequest, ...]:
    return (
        TypedRecordRequest(
            "recordPrepayment",
            _posting_request(
                request_prefix,
                "record-prepayment",
                "PREPAYMENT",
                "2026-01-02",
                "prepayment-invoice",
                {
                    "cashAccountCode": "cash",
                    "expenseAccountCode": "operating-expense",
                    "amount": _money("900"),
                    "accrualCutoffId": _PREPAYMENT_CUTOFF_ID,
                    "prepaymentAssetAccountCode": "prepaid-expense",
                    "recognitionInterval": {"startDate": "2026-01-02", "endDate": "2026-03-31"},
                },
            ),
        ),
        TypedRecordRequest(
            "recordDeferredRevenue",
            _posting_request(
                request_prefix,
                "record-deferred-revenue",
                "DEFERRED_REVENUE",
                "2026-01-03",
                "customer-contract",
                {
                    "cashAccountCode": "cash",
                    "revenueAccountCode": "service-revenue",
                    "amount": _money("900"),
                    "accrualCutoffId": _DEFERRED_REVENUE_CUTOFF_ID,
                    "deferredRevenueAccountCode": "deferred-revenue",
                    "recognitionInterval": {"startDate": "2026-01-03", "endDate": "2026-03-31"},
                },
            ),
        ),
        TypedRecordRequest(
            "recordAccruedExpense",
            _posting_request(
                request_prefix,
                "record-accrued-expense",
                "ACCRUED_EXPENSE",
                "2026-01-04",
                "accrual-schedule",
                {
                    "expenseAccountCode": "operating-expense",
                    "amount": _money("300"),
                    "accrualCutoffId": _ACCRUED_EXPENSE_CUTOFF_ID,
                    "accruedExpenseLiabilityAccountCode": "accrued-expense",
                },
            ),
        ),
        TypedRecordRequest(
            "recordAccrualCutoffRecognition",
            _posting_request(
                request_prefix,
                "record-accrual-cutoff-recognition",
                "ACCRUAL_CUTOFF_RECOGNITION",
                "2026-01-05",
                "prepayment-schedule",
                {
                    "amount": _money("300"),
                    "accrualCutoffId": _PREPAYMENT_CUTOFF_ID,
                },
            ),
        ),
        TypedRecordRequest(
            "recordAccruedExpenseSettlement",
            _posting_request(
                request_prefix,
                "record-accrued-expense-settlement",
                "ACCRUED_EXPENSE_SETTLEMENT",
                "2026-01-06",
                "cash-disbursement",
                {
                    "cashAccountCode": "cash",
                    "amount": _money("300"),
                    "accrualCutoffId": _ACCRUED_EXPENSE_CUTOFF_ID,
                },
            ),
        ),
    )
