"""Canonical request documents and deterministic identities for administrative flows."""

from __future__ import annotations

from collections.abc import Mapping
from uuid import NAMESPACE_URL, uuid5

from .administrative_models import AdministrativeWorld, JsonObject


def _account_request(
    account_code: str,
    account_name: str,
    account_type: str,
    *,
    financial_position: str | None = None,
    cash_flow: str | None = None,
) -> JsonObject:
    request: JsonObject = {
        "accountCode": account_code,
        "accountName": account_name,
        "accountType": account_type,
        "accountNodeKind": "POSTABLE",
    }
    if financial_position is not None:
        request["financialPositionLineClassification"] = financial_position
    if cash_flow is not None:
        request["cashFlowAssetClassification"] = cash_flow
    return request


def _tax_registration_request(
    registration_id: str,
    payable_account_code: str,
    recoverable_account_code: str,
) -> JsonObject:
    return {
        "taxRegistrationId": registration_id,
        "taxRegistrationName": "Administrative Matrix Latvia VAT",
        "jurisdiction": "LV",
        "registrationNumber": "LV40001234567",
        "payableAccountCode": payable_account_code,
        "recoverableAccountCode": recoverable_account_code,
        "obligationFrequency": "MONTHLY",
        "dueDaysAfterPeriodEnd": 20,
        "taxCodes": [
            {
                "taxCode": registration_id + "-sale",
                "taxCodeName": "Administrative Matrix Output VAT",
                "ratePartsPerMillion": 210000,
                "inclusionMode": "EXCLUSIVE",
                "applicationKind": "OUTPUT_SALE",
            },
            {
                "taxCode": registration_id + "-expense",
                "taxCodeName": "Administrative Matrix Input VAT",
                "ratePartsPerMillion": 210000,
                "inclusionMode": "INCLUSIVE",
                "applicationKind": "INPUT_EXPENSE_RECOVERABLE",
            },
        ],
    }


def _enroll_key_request(principal_id: str, credential_spki: str) -> JsonObject:
    return {
        "principalId": principal_id,
        "credentialSpki": credential_spki,
        "credentialPurpose": "operator",
    }


def _direct_journal_request(
    world: AdministrativeWorld,
    request_label: str,
    effective_date: str,
    debit_account_code: str,
    credit_account_code: str,
) -> JsonObject:
    return {
        "entryKind": "DIRECT_JOURNAL",
        "effectiveDate": effective_date,
        "lines": [
            {"accountCode": debit_account_code, "side": "DEBIT", "amount": _money("100")},
            {
                "accountCode": credit_account_code,
                "side": "CREDIT",
                "amount": _money("100"),
            },
        ],
        "evidence": _evidence(world, request_label, effective_date, "journal-support"),
        "provenance": _provenance(world, request_label),
    }


def _administrative_plan_request(world: AdministrativeWorld, output_mode: str) -> JsonObject:
    return {
        "planId": world.config.request_prefix + "-plan-" + output_mode,
        "steps": [
            {
                "stepId": "declare-plan-account",
                "kind": "declare-account",
                "declareAccount": _account_request(
                    "admin-plan-account-" + output_mode,
                    "Administrative Plan Account",
                    "ASSET",
                    financial_position="CURRENT_ASSET",
                    cash_flow="NON_CASH",
                ),
            }
        ],
    }


def _typed_cash_request(
    world: AdministrativeWorld,
    request_label: str,
    entry_kind: str,
    effective_date: str,
    source_document_type: str,
    details: Mapping[str, object],
) -> JsonObject:
    request: JsonObject = {
        "entryKind": entry_kind,
        "effectiveDate": effective_date,
        "evidence": _evidence(world, request_label, effective_date, source_document_type),
        "provenance": _provenance(world, request_label),
    }
    request.update(details)
    return request


def _evidence(
    world: AdministrativeWorld,
    request_label: str,
    effective_date: str,
    source_document_type: str,
) -> JsonObject:
    return {
        "sourceDocuments": [
            {
                "sourceDocumentId": world.config.request_prefix + "-" + request_label,
                "sourceDocumentType": source_document_type,
                "documentDate": effective_date,
            }
        ],
        "approvals": [],
    }


def _provenance(world: AdministrativeWorld, request_label: str) -> JsonObject:
    return {
        "commandId": _stable_uuid(world, request_label + "-command"),
        "idempotencyKey": world.config.request_prefix + "-" + request_label + "-idempotency",
        "causationId": _stable_uuid(world, request_label + "-causation"),
    }


def _money(minor_units: str) -> JsonObject:
    return {"currencyCode": "EUR", "minorUnits": minor_units}


def _principal_id(world: AdministrativeWorld, label: str) -> str:
    return _stable_uuid(world, "principal-" + label)


def _stable_uuid(world: AdministrativeWorld, label: str) -> str:
    return str(
        uuid5(
            NAMESPACE_URL,
            "fingrind-administrative-field-matrix:" + world.config.request_prefix + ":" + label,
        )
    )
