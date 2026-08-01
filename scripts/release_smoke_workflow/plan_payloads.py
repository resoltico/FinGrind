from __future__ import annotations

from typing import Any

from .fixture_payloads import (
    PLAN_REACTIVATE_RENAME_ACCOUNT_CODE,
    PLAN_REACTIVATE_RENAME_FINAL_NAME,
    PLAN_REACTIVATE_RENAME_INITIAL_NAME,
    PLAN_TAX_PAYABLE_ACCOUNT_CODE,
    PLAN_TAX_RECOVERABLE_ACCOUNT_CODE,
    PLAN_TAX_REGISTRATION_ID,
    declare_account_request,
    raw_transfer_request,
)


def administrative_ledger_plan_id(request_prefix: str) -> str:
    return request_prefix + "-attested-administrative-plan"


def read_only_ledger_plan_id(request_prefix: str) -> str:
    return request_prefix + "-read-only-account-plan"


def reactivate_rename_ledger_plan_id(request_prefix: str) -> str:
    return request_prefix + "-reactivate-rename-account-plan"


def posting_ledger_plan_id(request_prefix: str) -> str:
    return request_prefix + "-attested-posting-plan"


def administrative_ledger_plan_request(request_prefix: str) -> dict[str, Any]:
    return {
        "planId": administrative_ledger_plan_id(request_prefix),
        "steps": [
            {
                "stepId": "declare-vat-payable",
                "kind": "declare-account",
                "declareAccount": declare_account_request(
                    account_code=PLAN_TAX_PAYABLE_ACCOUNT_CODE,
                    account_name="Release Smoke VAT Payable",
                    account_type="LIABILITY",
                    account_node_kind="POSTABLE",
                    financial_position_line_classification="CURRENT_LIABILITY",
                ),
            },
            {
                "stepId": "declare-vat-recoverable",
                "kind": "declare-account",
                "declareAccount": declare_account_request(
                    account_code=PLAN_TAX_RECOVERABLE_ACCOUNT_CODE,
                    account_name="Release Smoke VAT Recoverable",
                    account_type="ASSET",
                    account_node_kind="POSTABLE",
                    financial_position_line_classification="CURRENT_ASSET",
                    cash_flow_asset_classification="NON_CASH",
                ),
            },
            {
                "stepId": "declare-vat-registration",
                "kind": "declare-tax-registration",
                "declareTaxRegistration": {
                    "taxRegistrationId": PLAN_TAX_REGISTRATION_ID,
                    "taxRegistrationName": "Release Smoke Latvia VAT",
                    "jurisdiction": "LV",
                    "registrationNumber": "LV40001234567",
                    "payableAccountCode": PLAN_TAX_PAYABLE_ACCOUNT_CODE,
                    "recoverableAccountCode": PLAN_TAX_RECOVERABLE_ACCOUNT_CODE,
                    "obligationFrequency": "MONTHLY",
                    "dueDaysAfterPeriodEnd": 20,
                    "taxCodes": [
                        {
                            "taxCode": "release-smoke-vat-sale",
                            "taxCodeName": "Release Smoke VAT Sale",
                            "ratePartsPerMillion": 210000,
                            "inclusionMode": "EXCLUSIVE",
                            "applicationKind": "OUTPUT_SALE",
                        },
                        {
                            "taxCode": "release-smoke-vat-expense",
                            "taxCodeName": "Release Smoke VAT Expense",
                            "ratePartsPerMillion": 210000,
                            "inclusionMode": "INCLUSIVE",
                            "applicationKind": "INPUT_EXPENSE_RECOVERABLE",
                        },
                    ],
                },
            },
        ],
    }


def read_only_ledger_plan_request(request_prefix: str) -> dict[str, Any]:
    return {
        "planId": read_only_ledger_plan_id(request_prefix),
        "steps": [
            {
                "stepId": "list-accounts-after-administration",
                "kind": "list-accounts",
                "query": {"limit": 50},
            }
        ],
    }


def reactivate_rename_ledger_plan_request(request_prefix: str) -> dict[str, Any]:
    return {
        "planId": reactivate_rename_ledger_plan_id(request_prefix),
        "steps": [
            {
                "stepId": "reactivate-plan-target",
                "kind": "declare-account",
                "declareAccount": declare_account_request(
                    account_code=PLAN_REACTIVATE_RENAME_ACCOUNT_CODE,
                    account_name=PLAN_REACTIVATE_RENAME_INITIAL_NAME,
                    account_type="ASSET",
                    account_node_kind="POSTABLE",
                    financial_position_line_classification="CURRENT_ASSET",
                    cash_flow_asset_classification="NON_CASH",
                ),
            },
            {
                "stepId": "rename-plan-target",
                "kind": "declare-account",
                "declareAccount": declare_account_request(
                    account_code=PLAN_REACTIVATE_RENAME_ACCOUNT_CODE,
                    account_name=PLAN_REACTIVATE_RENAME_FINAL_NAME,
                    account_type="ASSET",
                    account_node_kind="POSTABLE",
                    financial_position_line_classification="CURRENT_ASSET",
                    cash_flow_asset_classification="NON_CASH",
                ),
            },
        ],
    }


def posting_ledger_plan_request(
    request_prefix: str,
    cash_account_code: str,
    bank_account_code: str,
) -> dict[str, Any]:
    """Build one aggregate plan whose sole durable child is a direct journal posting."""
    return {
        "planId": posting_ledger_plan_id(request_prefix),
        "steps": [
            {
                "stepId": "post-plan-bank-transfer",
                "kind": "post-entry",
                "posting": raw_transfer_request(
                    request_prefix=request_prefix,
                    effective_date="2026-04-09",
                    source_account_code=cash_account_code,
                    destination_account_code=bank_account_code,
                    minor_units="125",
                    evidence_suffix="plan-transfer",
                    command_suffix="plan-transfer",
                    idempotency_suffix="idem-plan-transfer",
                    causation_suffix="cause-plan-transfer",
                ),
            }
        ],
    }
