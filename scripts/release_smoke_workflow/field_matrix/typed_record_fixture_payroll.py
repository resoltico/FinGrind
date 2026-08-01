"""Latvian-payroll typed-record fixture requests and accounts."""

from __future__ import annotations

from .typed_record_models import AccountDeclaration, TypedRecordRequest
from .typed_record_payloads import _money, _posting_request

_PAYROLL_RUN_ID = "matrix-payroll-2026-01"

_PAYROLL_DECLARATIONS = (
    AccountDeclaration(
        "wage-expense",
        "Wage Expense",
        "EXPENSE",
        profit_and_loss_line_classification="OPERATING_EXPENSE",
    ),
    AccountDeclaration(
        "employer-social-expense",
        "Employer Social Contribution Expense",
        "EXPENSE",
        profit_and_loss_line_classification="OPERATING_EXPENSE",
    ),
    AccountDeclaration(
        "net-wages-payable",
        "Net Wages Payable",
        "LIABILITY",
        financial_position_line_classification="CURRENT_LIABILITY",
    ),
    AccountDeclaration(
        "employee-social-payable",
        "Employee Social Contribution Payable",
        "LIABILITY",
        financial_position_line_classification="CURRENT_LIABILITY",
    ),
    AccountDeclaration(
        "employer-social-payable",
        "Employer Social Contribution Payable",
        "LIABILITY",
        financial_position_line_classification="CURRENT_LIABILITY",
    ),
    AccountDeclaration(
        "personal-income-tax-payable",
        "Personal Income Tax Payable",
        "LIABILITY",
        financial_position_line_classification="CURRENT_LIABILITY",
    ),
)


def _payroll_requests(request_prefix: str) -> tuple[TypedRecordRequest, ...]:
    return (
        TypedRecordRequest(
            "recordLatvianMonthlyPayroll",
            _posting_request(
                request_prefix,
                "record-latvian-monthly-payroll",
                "LATVIAN_MONTHLY_PAYROLL",
                "2026-01-31",
                "payroll-register",
                {
                    "payrollRunId": _PAYROLL_RUN_ID,
                    "employeeReference": "matrix-employee-001",
                    "payrollMonth": "2026-01",
                    "taxBookHeldAtEmployer": True,
                    "dependantCount": 0,
                    "wageExpenseAccountCode": "wage-expense",
                    "employerSocialContributionExpenseAccountCode": "employer-social-expense",
                    "netWagesPayableAccountCode": "net-wages-payable",
                    "employeeSocialContributionPayableAccountCode": "employee-social-payable",
                    "employerSocialContributionPayableAccountCode": "employer-social-payable",
                    "personalIncomeTaxPayableAccountCode": "personal-income-tax-payable",
                    "grossWages": _money("200000"),
                },
            ),
        ),
        TypedRecordRequest(
            "recordLatvianPayrollNetWageSettlement",
            _posting_request(
                request_prefix,
                "record-latvian-payroll-net-wage-settlement",
                "LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT",
                "2026-02-23",
                "bank-payment-order",
                {"cashAccountCode": "cash", "payrollRunId": _PAYROLL_RUN_ID},
            ),
        ),
        TypedRecordRequest(
            "recordLatvianPayrollStateRemittance",
            _posting_request(
                request_prefix,
                "record-latvian-payroll-state-remittance",
                "LATVIAN_PAYROLL_STATE_REMITTANCE",
                "2026-02-24",
                "social-insurance-report",
                {"cashAccountCode": "cash", "payrollRunId": _PAYROLL_RUN_ID},
            ),
        ),
    )
