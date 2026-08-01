"""Synthetic cash-flow arithmetic contract for report semantic validation."""

from __future__ import annotations

from types import SimpleNamespace

from .capabilities import OperationCapability
from .field_matrix_report_content_contract import (
    require_report_semantic_rejection,
    synthetic_report_outputs,
)
from .report_contexts import ReportBookContext
from .report_output_semantics import _assert_report_semantics
from .tax_report_setup import TaxReportFact


def assert_cash_flow_totals_are_substantive_and_articulate() -> None:
    """Protect cash-flow aggregate facts that a counterpart row token cannot express."""
    context = ReportBookContext(
        config=SimpleNamespace(label="synthetic cash-flow statement"),
        period_start="2026-01-03",
        period_end="2026-01-09",
        as_of="2026-01-09",
        account_code="cash",
        expected_report_tokens=(("cash-flow-statement", "service-revenue"),),
    )
    operation = OperationCapability(
        "cash-flow-statement",
        "Cash Receipts And Payments",
        "query",
        ("json", "text", "csv"),
        (),
    )
    tax_fact = TaxReportFact("unused-registration", "unused-code", "1")
    payload = {
        "family": "cash-flow-statement",
        "openingCashTotals": [cash_flow_balance("10000", "0", "DEBIT")],
        "sections": [
            {
                "sectionKind": "OPERATING",
                "rows": [{"lineCode": "service-revenue"}],
                "totals": [cash_flow_balance("1200", "0", "DEBIT")],
            },
            {
                "sectionKind": "FINANCING",
                "rows": [{"lineCode": "owner-draws"}],
                "totals": [cash_flow_balance("0", "400", "CREDIT")],
            },
        ],
        "movementTotals": [cash_flow_balance("1200", "400", "DEBIT")],
        "closingCashTotals": [cash_flow_balance("10800", "0", "DEBIT")],
    }
    _assert_report_semantics(
        context,
        operation,
        synthetic_report_outputs(
            payload,
            "service-revenue",
            "service-revenue",
            text_title="Cash Receipts And Payments",
            csv_family="cash-flow-statement",
        ),
        tax_fact,
    )
    for key, value, expected in (
        ("openingCashTotals", [], "substantive openingCashTotals"),
        (
            "movementTotals",
            [cash_flow_balance("1200", "400", "CREDIT")],
            "coherent movementTotals[0] balance",
        ),
        (
            "closingCashTotals",
            [cash_flow_balance("10801", "0", "DEBIT")],
            "articulate opening cash plus movement to closing cash",
        ),
    ):
        malformed = dict(payload)
        malformed[key] = value
        require_report_semantic_rejection(
            context,
            operation,
            malformed,
            "service-revenue",
            "service-revenue",
            tax_fact,
            expected,
        )
    inconsistent_sections = dict(payload)
    inconsistent_sections["sections"] = [
        {
            "sectionKind": "OPERATING",
            "rows": [{"lineCode": "service-revenue"}],
            "totals": [cash_flow_balance("1000", "0", "DEBIT")],
        }
    ]
    require_report_semantic_rejection(
        context,
        operation,
        inconsistent_sections,
        "service-revenue",
        "service-revenue",
        tax_fact,
        "reconcile section totals to movementTotals",
    )


def cash_flow_balance(debit_total: str, credit_total: str, balance_side: str) -> dict[str, object]:
    """Build one balanced MoneyTotal-shaped fixture from the source debit and credit amounts."""
    net_minor_units = abs(int(debit_total) - int(credit_total))
    return {
        "currencyCode": "EUR",
        "debitTotal": {"currencyCode": "EUR", "minorUnits": debit_total},
        "creditTotal": {"currencyCode": "EUR", "minorUnits": credit_total},
        "netAmount": {"currencyCode": "EUR", "minorUnits": str(net_minor_units)},
        "balanceSide": balance_side,
    }
