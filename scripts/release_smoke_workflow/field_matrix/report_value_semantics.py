"""Substantive JSON-value proof for report scenarios."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ..support import require
from .report_contexts import ReportBookContext
from .tax_report_setup import TaxReportFact


def _assert_tax_obligation_semantics(
    context: ReportBookContext,
    payload: Mapping[str, Any],
    tax_fact: TaxReportFact,
) -> None:
    resolved_query = _required_mapping(
        payload,
        "resolvedQuery",
        f"{context.config.label} field-matrix tax-obligation",
    )
    require(
        resolved_query.get("taxRegistrationId") == tax_fact.registration_id
        and resolved_query.get("periodStart") == context.period_start
        and resolved_query.get("periodEnd") == context.period_end,
        f"{context.config.label} field-matrix tax-obligation did not retain its declared monthly scope",
    )
    rows = payload.get("rows")
    require(
        isinstance(rows, list)
        and any(
            isinstance(row, dict)
            and row.get("taxCode") == tax_fact.tax_code
            and isinstance(row.get("postings"), int)
            and row["postings"] > 0
            for row in rows
        ),
        f"{context.config.label} field-matrix tax-obligation did not expose its taxed sale row",
    )
    totals = _required_mapping(
        payload,
        "totals",
        f"{context.config.label} field-matrix tax-obligation",
    )
    output_tax = _required_mapping(
        totals,
        "outputTax",
        f"{context.config.label} field-matrix tax-obligation",
    )
    require(
        output_tax.get("minorUnits") == tax_fact.output_tax_minor_units,
        f"{context.config.label} field-matrix tax-obligation did not retain its output-tax total",
    )


def _assert_cash_flow_statement_semantics(
    context: ReportBookContext,
    payload: Mapping[str, Any],
) -> None:
    """Prove the cash-flow payload retains and reconciles its owned cash totals.

    Cash-flow rows intentionally identify non-cash counterpart accounts. The cash accounts are
    instead represented by the three aggregate cash-total collections, which must reconcile with
    the section totals and the statement's per-currency articulation rule.
    """
    purpose = f"{context.config.label} field-matrix cash-flow-statement[json]"
    opening = _cash_flow_signed_totals(
        payload.get("openingCashTotals"), "openingCashTotals", purpose
    )
    movement = _cash_flow_signed_totals(payload.get("movementTotals"), "movementTotals", purpose)
    closing = _cash_flow_signed_totals(
        payload.get("closingCashTotals"), "closingCashTotals", purpose
    )

    sections = payload.get("sections")
    require(
        isinstance(sections, list),
        f"{purpose} did not expose sections as an array for cash-total reconciliation",
    )
    if not isinstance(sections, list):
        raise TypeError("require must reject non-array cash-flow sections")
    section_totals: list[object] = []
    for index, section in enumerate(sections):
        require(
            isinstance(section, dict),
            f"{purpose} did not expose sections[{index}] as an object",
        )
        if not isinstance(section, dict):
            raise TypeError("require must reject non-object cash-flow sections")
        totals = section.get("totals")
        require(
            isinstance(totals, list),
            f"{purpose} did not expose sections[{index}].totals as an array",
        )
        if not isinstance(totals, list):
            raise TypeError("require must reject non-array cash-flow section totals")
        section_totals.extend(totals)
    sections_by_currency = _cash_flow_signed_totals(
        section_totals, "section totals", purpose, allow_repeated_currency=True
    )
    require(
        sections_by_currency == movement,
        f"{purpose} did not reconcile section totals to movementTotals",
    )

    for currency_code in sorted(set(opening) | set(movement) | set(closing)):
        require(
            opening.get(currency_code, 0) + movement.get(currency_code, 0)
            == closing.get(currency_code, 0),
            f"{purpose} did not articulate opening cash plus movement to closing cash for "
            f"{currency_code}",
        )


def _cash_flow_signed_totals(
    balances: object,
    field: str,
    purpose: str,
    *,
    allow_repeated_currency: bool = False,
) -> dict[str, int]:
    """Validate one nonempty balance collection and return signed totals by currency."""
    require(
        isinstance(balances, list) and bool(balances),
        f"{purpose} did not expose substantive {field}",
    )
    if not isinstance(balances, list) or not balances:
        raise AssertionError("require must reject an empty cash-flow balance collection")
    totals_by_currency: dict[str, int] = {}
    for index, balance in enumerate(balances):
        require(
            isinstance(balance, dict),
            f"{purpose} did not expose {field}[{index}] as a balance object",
        )
        if not isinstance(balance, dict):
            raise TypeError("require must reject a non-object cash-flow balance")
        currency_code = balance.get("currencyCode")
        require(
            isinstance(currency_code, str) and bool(currency_code),
            f"{purpose} did not expose {field}[{index}].currencyCode",
        )
        if not isinstance(currency_code, str) or not currency_code:
            raise AssertionError("require must reject a missing cash-flow balance currency")
        debit_total = _cash_flow_balance_minor_units(
            balance, "debitTotal", field, index, currency_code, purpose
        )
        credit_total = _cash_flow_balance_minor_units(
            balance, "creditTotal", field, index, currency_code, purpose
        )
        net_amount = _cash_flow_balance_minor_units(
            balance, "netAmount", field, index, currency_code, purpose
        )
        signed_total = debit_total - credit_total
        expected_side = "DEBIT" if signed_total > 0 else "CREDIT" if signed_total < 0 else "ZERO"
        require(
            net_amount == abs(signed_total) and balance.get("balanceSide") == expected_side,
            f"{purpose} did not expose a coherent {field}[{index}] balance",
        )
        if not allow_repeated_currency:
            require(
                currency_code not in totals_by_currency,
                f"{purpose} repeated currency {currency_code!r} in {field}",
            )
        totals_by_currency[currency_code] = totals_by_currency.get(currency_code, 0) + signed_total
    return totals_by_currency


def _cash_flow_balance_minor_units(
    balance: Mapping[str, Any],
    money_field: str,
    collection_field: str,
    index: int,
    currency_code: str,
    purpose: str,
) -> int:
    money = _required_mapping(balance, money_field, f"{purpose} {collection_field}[{index}]")
    require(
        money.get("currencyCode") == currency_code,
        f"{purpose} did not retain {collection_field}[{index}].{money_field} currency",
    )
    minor_units = money.get("minorUnits")
    require(
        isinstance(minor_units, str)
        and minor_units.isascii()
        and minor_units.isdecimal()
        and str(int(minor_units)) == minor_units,
        f"{purpose} did not expose canonical non-negative {collection_field}[{index}]."
        f"{money_field}.minorUnits",
    )
    if not isinstance(minor_units, str):
        raise TypeError("require must reject non-text cash-flow minor units")
    return int(minor_units)


def _contains_exact_string(value: object, expected: str) -> bool:
    if isinstance(value, str):
        return value == expected
    if isinstance(value, dict):
        return any(_contains_exact_string(item, expected) for item in value.values())
    if isinstance(value, list):
        return any(_contains_exact_string(item, expected) for item in value)
    return False


def _required_mapping(
    container: Mapping[str, Any],
    key: str,
    purpose: str,
) -> dict[str, Any]:
    value = container.get(key)
    require(isinstance(value, dict), f"{purpose} did not expose {key} as an object")
    if not isinstance(value, dict):
        raise TypeError("require must reject a non-object field")
    return value
