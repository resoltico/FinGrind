from __future__ import annotations

from typing import Any

from .evidence_fixtures import posting_evidence, posting_provenance

PLAN_TAX_PAYABLE_ACCOUNT_CODE = "release-smoke-vat-payable"
PLAN_TAX_RECOVERABLE_ACCOUNT_CODE = "release-smoke-vat-recoverable"
PLAN_TAX_REGISTRATION_ID = "release-smoke-vat"
PLAN_REACTIVATE_RENAME_ACCOUNT_CODE = "release-smoke-plan-reactivate-rename"
PLAN_REACTIVATE_RENAME_INITIAL_NAME = "Release Smoke Plan Reactivation Target"
PLAN_REACTIVATE_RENAME_FINAL_NAME = "Release Smoke Plan Renamed Target"


def sale_request(
    *,
    request_prefix: str,
    effective_date: str,
    cash_account_code: str,
    revenue_account_code: str,
    minor_units: str,
    evidence_suffix: str,
    command_suffix: str,
    idempotency_suffix: str,
    causation_suffix: str,
) -> dict[str, Any]:
    return {
        "entryKind": "SALE_SETTLED",
        "effectiveDate": effective_date,
        "cashAccountCode": cash_account_code,
        "revenueAccountCode": revenue_account_code,
        "amount": {
            "currencyCode": "EUR",
            "minorUnits": minor_units,
        },
        "evidence": posting_evidence(request_prefix, evidence_suffix, effective_date),
        "provenance": posting_provenance(
            request_prefix, command_suffix, idempotency_suffix, causation_suffix
        ),
    }


def expense_request(
    *,
    request_prefix: str,
    effective_date: str,
    expense_account_code: str,
    cash_account_code: str,
    minor_units: str,
    evidence_suffix: str,
    command_suffix: str,
    idempotency_suffix: str,
    causation_suffix: str,
) -> dict[str, Any]:
    return {
        "entryKind": "EXPENSE_SETTLED",
        "effectiveDate": effective_date,
        "expenseAccountCode": expense_account_code,
        "cashAccountCode": cash_account_code,
        "amount": {
            "currencyCode": "EUR",
            "minorUnits": minor_units,
        },
        "evidence": posting_evidence(request_prefix, evidence_suffix, effective_date),
        "provenance": posting_provenance(
            request_prefix, command_suffix, idempotency_suffix, causation_suffix
        ),
    }


def taxed_sale_request(
    *,
    request_prefix: str,
    effective_date: str,
    cash_account_code: str,
    revenue_account_code: str,
    minor_units: str,
    tax_registration_id: str,
    tax_code: str,
    evidence_suffix: str,
    command_suffix: str,
    idempotency_suffix: str,
    causation_suffix: str,
) -> dict[str, Any]:
    """Build a sale request whose tax selection resolves against a declared registration."""
    payload = sale_request(
        request_prefix=request_prefix,
        effective_date=effective_date,
        cash_account_code=cash_account_code,
        revenue_account_code=revenue_account_code,
        minor_units=minor_units,
        evidence_suffix=evidence_suffix,
        command_suffix=command_suffix,
        idempotency_suffix=idempotency_suffix,
        causation_suffix=causation_suffix,
    )
    payload["tax"] = {
        "taxRegistrationId": tax_registration_id,
        "taxCode": tax_code,
    }
    return payload


def raw_transfer_request(
    *,
    request_prefix: str,
    effective_date: str,
    source_account_code: str,
    destination_account_code: str,
    minor_units: str,
    evidence_suffix: str,
    command_suffix: str,
    idempotency_suffix: str,
    causation_suffix: str,
) -> dict[str, Any]:
    return {
        "entryKind": "DIRECT_JOURNAL",
        "effectiveDate": effective_date,
        "lines": [
            journal_line(destination_account_code, "DEBIT", minor_units),
            journal_line(source_account_code, "CREDIT", minor_units),
        ],
        "evidence": posting_evidence(request_prefix, evidence_suffix, effective_date),
        "provenance": posting_provenance(
            request_prefix, command_suffix, idempotency_suffix, causation_suffix
        ),
    }


def journal_line(account_code: str, side: str, minor_units: str) -> dict[str, Any]:
    return {
        "accountCode": account_code,
        "side": side,
        "amount": {
            "currencyCode": "EUR",
            "minorUnits": minor_units,
        },
    }


def declare_account_request(
    *,
    account_code: str,
    account_name: str,
    account_type: str,
    account_node_kind: str,
    financial_position_line_classification: str | None = None,
    cash_flow_asset_classification: str | None = None,
    profit_and_loss_line_classification: str | None = None,
    nonsense_one: str | None = None,
    nonsense_two: str | None = None,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "accountCode": account_code,
        "accountName": account_name,
        "accountType": account_type,
        "accountNodeKind": account_node_kind,
    }
    if financial_position_line_classification is not None:
        payload["financialPositionLineClassification"] = financial_position_line_classification
    if cash_flow_asset_classification is not None:
        payload["cashFlowAssetClassification"] = cash_flow_asset_classification
    if profit_and_loss_line_classification is not None:
        payload["profitAndLossLineClassification"] = profit_and_loss_line_classification
    if nonsense_one is not None:
        payload["nonsenseOne"] = nonsense_one
    if nonsense_two is not None:
        payload["nonsenseTwo"] = nonsense_two
    return payload
