"""Receipt-output semantics and exact response-anchor parsing."""

from __future__ import annotations

import re
from collections.abc import Mapping
from typing import Any

from ..models import ReleaseSmokeConfig, SmokePath
from ..path_support import normalize_reported_path
from ..support import require
from .query_contract_catalog import (
    _MAX_UNSIGNED_64,
    _RECEIPT_ATTESTATION_ANCHOR_FIELDS,
    _RECEIPT_EXPORT_RESPONSE_FIELDS,
    _RECEIPT_VERIFICATION_RESPONSE_FIELDS,
    ModeSemanticAssertion,
)
from .query_models import AttestationHeadFacts, ReceiptFacts
from .query_response_support import _required_mapping, _required_text, _success_payload
from .query_text_contract import _public_path_token, _require_text_facts
from .receipt_artifact_assertions import canonical_receipt_reported_path


def _export_receipt_mode_assertion(
    config: ReleaseSmokeConfig,
    receipt: ReceiptFacts,
    attestation_head: AttestationHeadFacts,
) -> ModeSemanticAssertion:
    def assertion(output_mode: str, output: str) -> None:
        if output_mode == "json":
            payload = _success_payload(config, "export-attestation-receipt", output)
            reported_receipt_file = _required_text(
                payload,
                "receiptFile",
                f"{config.label} field-matrix export-attestation-receipt[json]",
            )
            reported_receipt = _exported_receipt_facts_from_payload(
                receipt.receipt_path,
                payload,
                f"{config.label} field-matrix export-attestation-receipt[json]",
            )
            require(
                normalize_reported_path(reported_receipt_file)
                == normalize_reported_path(
                    canonical_receipt_reported_path(config, receipt.receipt_path)
                )
                and reported_receipt.book_id == receipt.book_id
                and reported_receipt.operation_order == receipt.operation_order
                and reported_receipt.operation_head == attestation_head.operation_head,
                f"{config.label} field-matrix export-attestation-receipt[json] did not retain "
                "the requested receipt and attestation head",
            )
            return
        _require_text_facts(
            config,
            "export-attestation-receipt",
            output_mode,
            output,
            _public_path_token(receipt.receipt_path),
            receipt.book_id,
            receipt.operation_order,
            receipt.operation_head,
        )

    return assertion


def _verify_receipt_mode_assertion(
    config: ReleaseSmokeConfig,
    receipt: ReceiptFacts,
) -> ModeSemanticAssertion:
    def assertion(output_mode: str, output: str) -> None:
        if output_mode == "json":
            payload = _success_payload(config, "verify-receipt", output)
            reported_receipt_file = _required_text(
                payload,
                "receiptFile",
                f"{config.label} field-matrix verify-receipt[json]",
            )
            reported_receipt = _verified_receipt_facts_from_payload(
                receipt.receipt_path,
                payload,
                f"{config.label} field-matrix verify-receipt[json]",
            )
            require(
                normalize_reported_path(reported_receipt_file)
                == normalize_reported_path(
                    canonical_receipt_reported_path(config, receipt.receipt_path)
                )
                and reported_receipt.book_id == receipt.book_id
                and reported_receipt.operation_order == receipt.operation_order
                and reported_receipt.operation_head == receipt.operation_head,
                f"{config.label} field-matrix verify-receipt[json] did not retain the selected "
                "receipt result",
            )
            return
        _require_text_facts(
            config,
            "verify-receipt",
            output_mode,
            output,
            _public_path_token(receipt.receipt_path),
            receipt.book_id,
            receipt.operation_order,
            receipt.operation_head,
        )

    return assertion


def _exported_receipt_facts_from_payload(
    receipt_path: SmokePath,
    payload: Mapping[str, Any],
    purpose: str,
) -> ReceiptFacts:
    facts = _receipt_facts_from_payload(
        receipt_path,
        payload,
        _RECEIPT_EXPORT_RESPONSE_FIELDS,
        purpose,
    )
    require(
        isinstance(payload.get("warnings"), list),
        f"{purpose} did not expose warnings as an array",
    )
    return facts


def _verified_receipt_facts_from_payload(
    receipt_path: SmokePath,
    payload: Mapping[str, Any],
    purpose: str,
) -> ReceiptFacts:
    facts = _receipt_facts_from_payload(
        receipt_path,
        payload,
        _RECEIPT_VERIFICATION_RESPONSE_FIELDS,
        purpose,
    )
    require(
        isinstance(payload.get("findings"), list),
        f"{purpose} did not expose findings as an array",
    )
    return facts


def _receipt_facts_from_payload(
    receipt_path: SmokePath,
    payload: Mapping[str, Any],
    expected_payload_fields: frozenset[str],
    purpose: str,
) -> ReceiptFacts:
    require(
        set(payload) == expected_payload_fields,
        f"{purpose} did not expose the exact receipt response fields",
    )
    receipt_anchor = _required_mapping(payload, "receiptAttestationAnchor", purpose)
    require(
        set(receipt_anchor) == _RECEIPT_ATTESTATION_ANCHOR_FIELDS,
        f"{purpose} did not expose the exact receipt attestation anchor",
    )
    operation_order = _required_text(receipt_anchor, "operationOrder", purpose)
    operation_head = _required_text(receipt_anchor, "operationHead", purpose)
    require(
        re.fullmatch(r"0|[1-9][0-9]*", operation_order) is not None
        and int(operation_order) <= _MAX_UNSIGNED_64,
        f"{purpose} did not expose a canonical unsigned 64-bit attestation order",
    )
    require(
        re.fullmatch(r"[0-9a-f]{64}", operation_head) is not None,
        f"{purpose} did not expose a canonical attestation head",
    )
    return ReceiptFacts(
        receipt_path,
        _required_text(payload, "bookId", purpose),
        operation_order,
        operation_head,
    )
