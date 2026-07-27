"""Static operation-to-scenario routing for the live capability coverage matrix.

This is intentionally not an output-mode or artifact catalog.  The launcher owns
those facts through `capabilities --detail full`; this table only says which
release-smoke scenario must make each public operation executable.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from enum import Enum


class ScenarioDomain(str, Enum):
    """Cohesive fresh-world scenario owners used by the matrix."""

    DISCOVERY = "discovery"
    BOOK_LIFECYCLE = "book-lifecycle"
    KEY_MANAGEMENT = "key-management"
    BOOK_MAINTENANCE = "book-maintenance"
    ATTESTATION_REGISTRY = "attestation-registry"
    ACCOUNT_REGISTRY = "account-registry"
    TAX_ADMINISTRATION = "tax-administration"
    PERIOD_CLOSE = "period-close"
    GENERIC_QUERY = "generic-query"
    ATTESTATION_QUERY = "attestation-query"
    REPORT = "report"
    PLAN = "plan"
    POSTING = "posting"
    TYPED_RECORD = "typed-record"


@dataclass(frozen=True)
class ScenarioBinding:
    """The scenario that owns one operation and whether it must prove a new append."""

    domain: ScenarioDomain
    requires_new_attestation_append: bool = False


def _bindings(
    domain: ScenarioDomain,
    *operation_ids: str,
    requires_new_attestation_append: bool = False,
) -> dict[str, ScenarioBinding]:
    return {
        operation_id: ScenarioBinding(domain, requires_new_attestation_append)
        for operation_id in operation_ids
    }


SCENARIO_MATRIX: Mapping[str, ScenarioBinding] = {
    **_bindings(
        ScenarioDomain.DISCOVERY,
        "help",
        "version",
        "capabilities",
        "environment",
        "print-request-template",
        "print-plan-template",
    ),
    **_bindings(ScenarioDomain.BOOK_LIFECYCLE, "generate-book-key-file"),
    **_bindings(ScenarioDomain.KEY_MANAGEMENT, "generate-attestation-key-file"),
    **_bindings(
        ScenarioDomain.BOOK_LIFECYCLE,
        "open-book",
        requires_new_attestation_append=True,
    ),
    **_bindings(
        ScenarioDomain.BOOK_MAINTENANCE,
        "rekey-book",
        "backup-book",
        requires_new_attestation_append=True,
    ),
    **_bindings(
        ScenarioDomain.BOOK_MAINTENANCE,
        "restore-book",
        requires_new_attestation_append=True,
    ),
    **_bindings(
        ScenarioDomain.ATTESTATION_REGISTRY,
        "enroll-key",
        "rollover-key",
        "revoke-key",
        "alter-policy",
        requires_new_attestation_append=True,
    ),
    **_bindings(
        ScenarioDomain.ACCOUNT_REGISTRY,
        "declare-account",
        "amend-account",
        "retire-account",
        requires_new_attestation_append=True,
    ),
    **_bindings(
        ScenarioDomain.TAX_ADMINISTRATION,
        "declare-tax-registration",
        requires_new_attestation_append=True,
    ),
    **_bindings(
        ScenarioDomain.PERIOD_CLOSE,
        "interim-result-sweep",
        "fiscal-year-close",
        requires_new_attestation_append=True,
    ),
    **_bindings(ScenarioDomain.GENERIC_QUERY, "inspect-attestation-key-file"),
    **_bindings(
        ScenarioDomain.ATTESTATION_QUERY,
        "inspect-book",
        "verify-book",
        "attestation-review",
        "export-attestation-receipt",
        "verify-receipt",
    ),
    **_bindings(
        ScenarioDomain.GENERIC_QUERY,
        "list-accounts",
        "list-tax-registrations",
        "get-posting",
        "list-postings",
    ),
    **_bindings(
        ScenarioDomain.REPORT,
        "tax-obligation",
        "account-balance",
        "trial-balance",
        "account-ledger",
        "period-summary",
        "financial-position",
        "inventory-valuation",
        "accrual-cutoff-schedule",
        "fixed-asset-register",
        "financing-register",
        "realized-foreign-exchange-register",
        "latvian-payroll-register",
        "income-statement",
        "cash-flow-statement",
        "changes-in-equity",
    ),
    **_bindings(
        ScenarioDomain.PLAN,
        "execute-plan",
        requires_new_attestation_append=True,
    ),
    **_bindings(ScenarioDomain.POSTING, "preflight-entry"),
    **_bindings(
        ScenarioDomain.TYPED_RECORD,
        "record-sale-settled",
        "record-sale-on-credit",
        "record-purchase-settled",
        "record-purchase-on-credit",
        "record-inventory-capitalization-settled",
        "record-inventory-capitalization-on-credit",
        "record-inventory-write-down",
        "record-inventory-shrinkage",
        "record-inventory-count-increase",
        "record-prepayment",
        "record-deferred-revenue",
        "record-accrued-expense",
        "record-accrual-cutoff-recognition",
        "record-accrued-expense-settlement",
        "record-latvian-monthly-payroll",
        "record-latvian-payroll-net-wage-settlement",
        "record-latvian-payroll-state-remittance",
        "record-fixed-asset-capitalization",
        "record-fixed-asset-depreciation",
        "record-fixed-asset-disposal",
        "record-financing-borrowing",
        "record-financing-principal-repayment",
        "record-financing-interest-accrual",
        "record-financing-interest-payment",
        "record-foreign-currency-obligation",
        "record-realized-foreign-exchange-settlement",
        "record-expense-settled",
        "record-expense-on-credit",
        "record-receipt",
        "record-payment",
        "record-owner-contribution",
        "record-owner-withdrawal",
        "record-opening-position",
        "record-reversal",
        requires_new_attestation_append=True,
    ),
    **_bindings(
        ScenarioDomain.POSTING,
        "post-entry",
        requires_new_attestation_append=True,
    ),
}
