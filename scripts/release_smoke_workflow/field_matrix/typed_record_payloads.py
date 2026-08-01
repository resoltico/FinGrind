"""Canonical request-payload construction for typed-record matrix fixtures."""

from __future__ import annotations

from collections.abc import Mapping
from uuid import NAMESPACE_URL, uuid5

from .typed_record_models import JsonObject


def _money(minor_units: str, currency_code: str = "EUR") -> JsonObject:
    return {"currencyCode": currency_code, "minorUnits": minor_units}


def _posting_request(
    request_prefix: str,
    operation_id: str,
    entry_kind: str,
    effective_date: str,
    source_document_type: str,
    extra: Mapping[str, object],
) -> JsonObject:
    payload: JsonObject = {
        "entryKind": entry_kind,
        "effectiveDate": effective_date,
        "evidence": {
            "sourceDocuments": [
                {
                    "sourceDocumentId": f"{request_prefix}-{operation_id}-support",
                    "sourceDocumentType": source_document_type,
                    "documentDate": effective_date,
                }
            ],
            "approvals": [],
        },
        "provenance": {
            "commandId": str(
                uuid5(
                    NAMESPACE_URL,
                    f"fingrind-release-smoke:{request_prefix}:{operation_id}:command",
                )
            ),
            "idempotencyKey": f"{request_prefix}-{operation_id}-idempotency",
            "causationId": str(
                uuid5(
                    NAMESPACE_URL,
                    f"fingrind-release-smoke:{request_prefix}:{operation_id}:causation",
                )
            ),
        },
    }
    payload.update(extra)
    return payload


def _direct_journal_request(
    request_prefix: str,
    identifier: str,
    effective_date: str,
    debit_account_code: str,
    credit_account_code: str,
    minor_units: str,
    source_document_type: str,
) -> JsonObject:
    return _posting_request(
        request_prefix,
        identifier,
        "DIRECT_JOURNAL",
        effective_date,
        source_document_type,
        {
            "lines": [
                {"accountCode": debit_account_code, "side": "DEBIT", "amount": _money(minor_units)},
                {
                    "accountCode": credit_account_code,
                    "side": "CREDIT",
                    "amount": _money(minor_units),
                },
            ]
        },
    )


def _foreign_exchange(functional_minor_units: str, quoted_on: str) -> JsonObject:
    return {
        "transactionAmount": _money("120000", "USD"),
        "functionalAmount": _money(functional_minor_units),
        "quotedRate": {
            "transactionCurrencyAmount": _money("120000", "USD"),
            "functionalCurrencyAmount": _money(functional_minor_units),
            "quotedOn": quoted_on,
            "quoteSource": "central-bank-reference-rate",
        },
        "treatmentKind": "SPOT_TRANSACTION",
    }
