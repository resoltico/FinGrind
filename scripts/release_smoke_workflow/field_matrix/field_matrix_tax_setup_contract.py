"""Synthetic setup proof for durable tax-report evidence and format boundaries."""

from __future__ import annotations

import json
from types import SimpleNamespace

from ..attestation_head_checks import VerifiedAttestationHead
from ..fixture_payloads import PLAN_TAX_REGISTRATION_ID
from ..models import ReleaseSmokeFailure
from . import tax_report_setup
from .field_matrix_contract_fixtures import head
from .format_boundary_scenarios import _format_boundary_versions
from .tax_report_setup import TaxReportFact

_SYNTHETIC_SUPPORTED_BOOK_FORMAT_VERSION = 17


def assert_protected_book_format_boundaries_are_adjacent() -> None:
    """Require an integer format with exactly one lower and one higher SQLite line."""
    assert _format_boundary_versions(
        {"formatVersion": _SYNTHETIC_SUPPORTED_BOOK_FORMAT_VERSION},
        "synthetic format boundary",
    ) == (
        _SYNTHETIC_SUPPORTED_BOOK_FORMAT_VERSION,
        _SYNTHETIC_SUPPORTED_BOOK_FORMAT_VERSION - 1,
        _SYNTHETIC_SUPPORTED_BOOK_FORMAT_VERSION + 1,
    )
    for invalid_format in (
        True,
        1,
        0,
        -1,
        2_147_483_647,
        str(_SYNTHETIC_SUPPORTED_BOOK_FORMAT_VERSION),
    ):
        try:
            _format_boundary_versions(
                {"formatVersion": invalid_format}, "synthetic format boundary"
            )
        except ReleaseSmokeFailure:
            continue
        raise AssertionError(
            "format-boundary scenario accepted a protected-book format without valid adjacent lines"
        )


def assert_tax_report_setup_parses_durable_tax_fact() -> None:
    """Exercise the full taxed-sale setup before report coverage uses its durable fact."""
    config = SimpleNamespace(
        label="synthetic field-matrix taxed sale",
        book=SimpleNamespace(argument="book.db"),
        book_key=SimpleNamespace(argument="book.key"),
        request_taxed_sale=SimpleNamespace(argument="taxed-sale.json"),
        attestation_founder_principal_id="founder",
        attestation_founder_key=SimpleNamespace(argument="attestation.key"),
        attestation_founder_passphrase=SimpleNamespace(argument="attestation.passphrase"),
    )
    operation_ids = {"recordSaleSettled": "record-sale-settled", "getPosting": "get-posting"}
    before_head, after_head = (
        head("7", "a", previous_head_character="9"),
        head("8", "b", previous_head_character="a"),
    )
    observed_heads = iter((before_head, after_head))
    observed_appends: list[tuple[str, object, object, object]] = []
    original_run_cli = tax_report_setup.run_cli
    original_verified_head = tax_report_setup.verified_attestation_head
    original_record_append = tax_report_setup.record_new_attestation_append

    def synthetic_run_cli(_config: object, operation_id: str, *arguments: str) -> str:
        if operation_id == operation_ids["recordSaleSettled"]:
            assert "--request-file" in arguments and "taxed-sale.json" in arguments
            return json.dumps(
                {
                    "status": "ok",
                    "payload": {
                        "postingId": "taxed-posting-1",
                        "attestationCommit": {
                            "operationOrder": after_head.operation_order,
                            "operationHead": after_head.operation_head,
                        },
                    },
                }
            )
        if operation_id == operation_ids["getPosting"]:
            assert "--posting-id" in arguments and "taxed-posting-1" in arguments
            return json.dumps(
                {
                    "status": "ok",
                    "payload": {
                        "posting": {
                            "entry": {
                                "appliedTax": {
                                    "taxCode": "release-smoke-vat-sale",
                                    "taxAmount": {"minorUnits": "123"},
                                }
                            }
                        }
                    },
                }
            )
        raise AssertionError(f"unexpected taxed-sale operation: {operation_id}")

    def synthetic_verified_head(*_arguments: object) -> VerifiedAttestationHead:
        return next(observed_heads)

    def synthetic_record_append(
        operation_id: str,
        envelope: object,
        *,
        before_head: object,
        after_head: object,
    ) -> None:
        observed_appends.append((operation_id, envelope, before_head, after_head))

    try:
        tax_report_setup.run_cli = synthetic_run_cli
        tax_report_setup.verified_attestation_head = synthetic_verified_head
        tax_report_setup.record_new_attestation_append = synthetic_record_append
        tax_fact = tax_report_setup.prepare_tax_report_fact(config, operation_ids)
    finally:
        tax_report_setup.run_cli = original_run_cli
        tax_report_setup.verified_attestation_head = original_verified_head
        tax_report_setup.record_new_attestation_append = original_record_append

    assert tax_fact == TaxReportFact(PLAN_TAX_REGISTRATION_ID, "release-smoke-vat-sale", "123")
    assert observed_appends == [
        (
            operation_ids["recordSaleSettled"],
            {
                "status": "ok",
                "payload": {
                    "postingId": "taxed-posting-1",
                    "attestationCommit": {
                        "operationOrder": after_head.operation_order,
                        "operationHead": after_head.operation_head,
                    },
                },
            },
            before_head,
            after_head,
        )
    ]
