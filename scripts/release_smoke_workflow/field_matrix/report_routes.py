"""Report argument routes and response-content ownership metadata."""

from __future__ import annotations

from collections.abc import Callable, Mapping
from dataclasses import dataclass

from .report_contexts import ReportBookContext
from .tax_report_setup import TaxReportFact

_TAX_PERIOD_START = "2026-04-01"
_TAX_PERIOD_END = "2026-04-30"
_CASH_FLOW_STATEMENT_PERIOD_START = "2026-01-03"
_CSV_PDF_REFUSAL_CODE = "unsupported-output-selection"
_CSV_PDF_REFUSAL_MESSAGE = (
    "Unsupported output mode for --output: csv. When --pdf-out is selected, accepted stdout "
    "modes are json or text."
)

ReportArguments = Callable[[ReportBookContext, TaxReportFact], tuple[str, ...]]

_REPORT_CONTENT_ARRAYS: Mapping[str, str] = {
    "account-balance": "balances",
    "trial-balance": "rows",
    "account-ledger": "rows",
    "period-summary": "accountActivity",
    "financial-position": "sections",
    "inventory-valuation": "rows",
    "accrual-cutoff-schedule": "rows",
    "fixed-asset-register": "rows",
    "financing-register": "rows",
    "realized-foreign-exchange-register": "rows",
    "latvian-payroll-register": "rows",
    "income-statement": "sections",
    "cash-flow-statement": "sections",
    "changes-in-equity": "rows",
}

# These values are the report payload carrying business facts, as distinct from
# ``resolvedQuery`` request metadata.  The field matrix must find each known
# scenario fact here before it credits the report as semantically exercised.
_REPORT_SUBSTANTIVE_FACT_CONTAINERS: Mapping[str, tuple[str, ...]] = {
    "tax-obligation": ("rows", "totals"),
    "account-balance": ("account", "balances"),
    "trial-balance": ("rows",),
    "account-ledger": ("account", "rows"),
    "period-summary": ("accountActivity",),
    "financial-position": ("sections", "comparativeSections"),
    "inventory-valuation": ("rows",),
    "accrual-cutoff-schedule": ("rows",),
    "fixed-asset-register": ("rows",),
    "financing-register": ("rows",),
    "realized-foreign-exchange-register": ("rows",),
    "latvian-payroll-register": ("rows",),
    "income-statement": ("sections", "comparativeSections"),
    "cash-flow-statement": ("sections", "comparative"),
    "changes-in-equity": ("rows", "comparative"),
}


@dataclass(frozen=True)
class _ReportRoute:
    """One report's retained fact world and resolved command arguments."""

    context_name: str
    arguments: ReportArguments


def _book_access(context: ReportBookContext, *arguments: str) -> tuple[str, ...]:
    return (
        "--book-file",
        context.config.book.argument,
        "--book-key-file",
        context.config.book_key.argument,
        *arguments,
    )


def _tax_obligation_arguments(
    context: ReportBookContext,
    tax_fact: TaxReportFact,
) -> tuple[str, ...]:
    return _book_access(
        context,
        "--tax-registration-id",
        tax_fact.registration_id,
        "--period-start",
        context.period_start,
        "--period-end",
        context.period_end,
    )


def _cash_flow_statement_arguments(
    context: ReportBookContext,
    _tax_fact: TaxReportFact,
) -> tuple[str, ...]:
    """Retain the commercial fixture's opening cash before measuring its period movement."""
    return _book_access(
        context,
        "--period-start",
        _CASH_FLOW_STATEMENT_PERIOD_START,
        "--period-end",
        context.period_end,
    )


_REPORT_ROUTES: Mapping[str, _ReportRoute] = {
    "tax-obligation": _ReportRoute("tax", _tax_obligation_arguments),
    "account-balance": _ReportRoute(
        "commercial",
        lambda context, _tax_fact: _book_access(
            context,
            "--account-code",
            context.account_code,
            "--effective-date-from",
            context.period_start,
            "--effective-date-to",
            context.period_end,
        ),
    ),
    "trial-balance": _ReportRoute(
        "commercial",
        lambda context, _tax_fact: _book_access(
            context,
            "--effective-date-as-of",
            context.as_of,
        ),
    ),
    "account-ledger": _ReportRoute(
        "commercial",
        lambda context, _tax_fact: _book_access(
            context,
            "--account-code",
            context.account_code,
            "--effective-date-from",
            context.period_start,
            "--effective-date-to",
            context.period_end,
        ),
    ),
    "period-summary": _ReportRoute(
        "commercial",
        lambda context, _tax_fact: _book_access(
            context,
            "--period-start",
            context.period_start,
            "--period-end",
            context.period_end,
        ),
    ),
    "financial-position": _ReportRoute(
        "commercial",
        lambda context, _tax_fact: _book_access(
            context,
            "--effective-date-as-of",
            context.as_of,
        ),
    ),
    "inventory-valuation": _ReportRoute(
        "inventory",
        lambda context, _tax_fact: _book_access(context, "--as-of", context.as_of),
    ),
    "accrual-cutoff-schedule": _ReportRoute(
        "accrual",
        lambda context, _tax_fact: _book_access(context, "--as-of", context.as_of),
    ),
    "fixed-asset-register": _ReportRoute(
        "fixed_asset",
        lambda context, _tax_fact: _book_access(context, "--as-of", context.as_of),
    ),
    "financing-register": _ReportRoute(
        "financing", lambda context, _tax_fact: _book_access(context)
    ),
    "realized-foreign-exchange-register": _ReportRoute(
        "foreign_exchange", lambda context, _tax_fact: _book_access(context)
    ),
    "latvian-payroll-register": _ReportRoute(
        "payroll", lambda context, _tax_fact: _book_access(context)
    ),
    "income-statement": _ReportRoute(
        "commercial",
        lambda context, _tax_fact: _book_access(
            context,
            "--period-start",
            context.period_start,
            "--period-end",
            context.period_end,
        ),
    ),
    "cash-flow-statement": _ReportRoute("commercial", _cash_flow_statement_arguments),
    "changes-in-equity": _ReportRoute(
        "commercial",
        lambda context, _tax_fact: _book_access(
            context,
            "--period-start",
            context.period_start,
            "--period-end",
            context.period_end,
        ),
    ),
}
