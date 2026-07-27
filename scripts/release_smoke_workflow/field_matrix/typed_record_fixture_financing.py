"""Financing typed-record fixture requests and accounts."""

from __future__ import annotations

from .typed_record_models import AccountDeclaration, TypedRecordRequest
from .typed_record_payloads import _money, _posting_request

_FINANCING_ARRANGEMENT_ID = "matrix-term-loan"

_FINANCING_DECLARATIONS = (
    AccountDeclaration(
        "term-loan-principal",
        "Term Loan Principal",
        "LIABILITY",
        financial_position_line_classification="NONCURRENT_LIABILITY",
    ),
    AccountDeclaration(
        "term-loan-interest-payable",
        "Term Loan Interest Payable",
        "LIABILITY",
        financial_position_line_classification="CURRENT_LIABILITY",
    ),
    AccountDeclaration(
        "interest-expense",
        "Interest Expense",
        "EXPENSE",
        profit_and_loss_line_classification="FINANCE_EXPENSE",
    ),
)


def _financing_requests(request_prefix: str) -> tuple[TypedRecordRequest, ...]:
    return (
        TypedRecordRequest(
            "recordFinancingBorrowing",
            _posting_request(
                request_prefix,
                "record-financing-borrowing",
                "FINANCING_BORROWING",
                "2026-01-02",
                "loan-agreement",
                {
                    "cashAccountCode": "cash",
                    "financingArrangementId": _FINANCING_ARRANGEMENT_ID,
                    "principalLiabilityAccountCode": "term-loan-principal",
                    "interestPayableAccountCode": "term-loan-interest-payable",
                    "principalAmount": _money("1000000"),
                },
            ),
        ),
        TypedRecordRequest(
            "recordFinancingPrincipalRepayment",
            _posting_request(
                request_prefix,
                "record-financing-principal-repayment",
                "FINANCING_PRINCIPAL_REPAYMENT",
                "2026-01-03",
                "loan-statement",
                {
                    "cashAccountCode": "cash",
                    "financingArrangementId": _FINANCING_ARRANGEMENT_ID,
                    "principalAmount": _money("100000"),
                },
            ),
        ),
        TypedRecordRequest(
            "recordFinancingInterestAccrual",
            _posting_request(
                request_prefix,
                "record-financing-interest-accrual",
                "FINANCING_INTEREST_ACCRUAL",
                "2026-01-04",
                "interest-calculation",
                {
                    "financingArrangementId": _FINANCING_ARRANGEMENT_ID,
                    "interestExpenseAccountCode": "interest-expense",
                    "interestAmount": _money("12000"),
                },
            ),
        ),
        TypedRecordRequest(
            "recordFinancingInterestPayment",
            _posting_request(
                request_prefix,
                "record-financing-interest-payment",
                "FINANCING_INTEREST_PAYMENT",
                "2026-01-05",
                "loan-statement",
                {
                    "cashAccountCode": "cash",
                    "financingArrangementId": _FINANCING_ARRANGEMENT_ID,
                    "interestAmount": _money("12000"),
                },
            ),
        ),
    )
