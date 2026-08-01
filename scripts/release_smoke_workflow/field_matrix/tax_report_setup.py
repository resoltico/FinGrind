"""Prepare one real taxed posting so the tax-obligation report has durable facts."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from ..attestation_arguments import signing_credential_arguments
from ..attestation_head_checks import verified_attestation_head
from ..cli import run_cli
from ..fixture_payloads import PLAN_TAX_REGISTRATION_ID
from ..models import ReleaseSmokeConfig
from ..support import parse_json_output, require
from .context import record_new_attestation_append


@dataclass(frozen=True)
class TaxReportFact:
    """Known durable output-tax fact that the tax report must retain."""

    registration_id: str
    tax_code: str
    output_tax_minor_units: str


def prepare_tax_report_fact(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> TaxReportFact:
    """Append one fresh taxed sale after the tax-registration plan has succeeded."""
    print(f"{config.label}: posting a taxed sale for meaningful tax-obligation coverage")
    before_head = verified_attestation_head(
        config,
        operation_ids,
        "field-matrix taxed sale before",
    )
    output = run_cli(
        config,
        operation_ids["recordSaleSettled"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--request-file",
        config.request_taxed_sale.argument,
        *signing_credential_arguments(config),
        "--output",
        "json",
    )
    after_head = verified_attestation_head(
        config,
        operation_ids,
        "field-matrix taxed sale after",
    )
    envelope = parse_json_output(
        output,
        f"{config.label} field-matrix taxed sale did not emit valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} field-matrix taxed sale did not report ok status",
    )
    record_new_attestation_append(
        operation_ids["recordSaleSettled"],
        envelope,
        before_head=before_head,
        after_head=after_head,
    )
    purpose = f"{config.label} field-matrix taxed sale"
    posting = _taxed_posting(config, operation_ids, envelope)
    applied_tax = _required_mapping(
        _required_mapping(posting, "entry", purpose),
        "appliedTax",
        purpose,
    )
    require(
        applied_tax.get("taxCode") == "release-smoke-vat-sale",
        f"{purpose} did not retain release-smoke-vat-sale",
    )
    tax_amount = _required_mapping(
        applied_tax,
        "taxAmount",
        purpose,
    )
    minor_units = tax_amount.get("minorUnits")
    require(
        isinstance(minor_units, str) and minor_units.isdigit() and int(minor_units) > 0,
        f"{purpose} did not retain positive output tax",
    )
    if not isinstance(minor_units, str):
        raise TypeError("require must reject missing tax minor units")
    return TaxReportFact(PLAN_TAX_REGISTRATION_ID, "release-smoke-vat-sale", minor_units)


def _taxed_posting(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    envelope: dict[str, Any],
) -> dict[str, Any]:
    payload = _required_mapping(envelope, "payload", f"{config.label} field-matrix taxed sale")
    posting_id = payload.get("postingId")
    require(
        isinstance(posting_id, str) and bool(posting_id),
        f"{config.label} field-matrix taxed sale did not expose postingId",
    )
    if not isinstance(posting_id, str) or not posting_id:
        raise AssertionError("require must reject missing taxed posting id")
    readback = parse_json_output(
        run_cli(
            config,
            operation_ids["getPosting"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--posting-id",
            posting_id,
            "--output",
            "json",
        ),
        f"{config.label} field-matrix taxed sale read-back did not emit valid JSON",
    )
    require(
        readback.get("status") == "ok",
        f"{config.label} field-matrix taxed sale read-back did not report ok status",
    )
    return _required_mapping(
        _required_mapping(readback, "payload", f"{config.label} field-matrix taxed read-back"),
        "posting",
        f"{config.label} field-matrix taxed read-back",
    )


def _required_mapping(
    container: dict[str, Any],
    key: str,
    purpose: str,
) -> dict[str, Any]:
    value = container.get(key)
    require(
        isinstance(value, dict),
        f"{purpose} did not expose {key} as an object",
    )
    if not isinstance(value, dict):
        raise TypeError("require must reject a non-object field")
    return value
