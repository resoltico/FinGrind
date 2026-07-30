from __future__ import annotations

import re
from typing import Any

from .attestation_review_scope import ReviewScope
from .models import ReleaseSmokeConfig
from .support import parse_json_output, require


def assert_review_json(
    review: dict[str, Any], scope: ReviewScope, config: ReleaseSmokeConfig
) -> None:
    require(
        review.get("status") == "ok",
        f"{config.label} attestation-review did not report ok status",
    )
    payload = review.get("payload")
    require(
        isinstance(payload, dict),
        f"{config.label} attestation-review did not expose a payload object",
    )
    if not isinstance(payload, dict):
        raise TypeError("require must reject a missing attestation-review payload")
    require(
        set(payload) == {"bookId", "verifiedAttestationHead", "findings"},
        f"{config.label} attestation-review did not publish the current-protocol payload shape",
    )
    verified_head = payload.get("verifiedAttestationHead")
    require(
        isinstance(verified_head, dict)
        and set(verified_head) == {"operationOrder", "operationHead"}
        and payload.get("bookId") == scope.book_id
        and verified_head.get("operationOrder") == scope.operation_order
        and verified_head.get("operationHead") == scope.operation_head,
        f"{config.label} attestation-review did not retain the verified immutable head",
    )
    assert_flat_review_findings(
        payload.get("findings"), scope, f"{config.label} attestation-review"
    )


def assert_strict_review_json(output: str, scope: ReviewScope, config: ReleaseSmokeConfig) -> None:
    envelope = parse_json_output(
        output,
        f"{config.label} strict attestation-review output was not valid JSON",
    )
    require(
        envelope.get("status") == "rejected"
        and envelope.get("code") == "attestation-review-required"
        and "payload" not in envelope,
        f"{config.label} strict attestation-review did not publish its rejected envelope",
    )
    details = envelope.get("details")
    require(
        isinstance(details, dict)
        and set(details) == {"bookId", "verifiedAttestationHead", "previousHead", "reviewFindings"},
        f"{config.label} strict attestation-review did not publish the current-protocol rejection details",
    )
    if not isinstance(details, dict):
        raise TypeError("require must reject missing strict attestation-review details")
    verified_head = details.get("verifiedAttestationHead")
    require(
        isinstance(verified_head, dict)
        and set(verified_head) == {"operationOrder", "operationHead"}
        and details.get("bookId") == scope.book_id
        and verified_head.get("operationOrder") == scope.operation_order
        and verified_head.get("operationHead") == scope.operation_head
        and details.get("previousHead") == scope.previous_head,
        f"{config.label} strict attestation-review details did not retain the immutable head",
    )
    assert_flat_review_findings(
        details.get("reviewFindings"),
        scope,
        f"{config.label} strict attestation-review details",
        require_complete_window=True,
    )


def assert_flat_review_findings(
    value: object,
    scope: ReviewScope,
    label: str,
    *,
    require_complete_window: bool = False,
) -> None:
    require(isinstance(value, list), f"{label} did not expose review findings as an array")
    if not isinstance(value, list):
        raise TypeError("require must reject non-array review findings")
    expected_fields = {
        "credentialKeyId",
        "firstAffectedOrder",
        "lastAffectedOrder",
        "operationOrder",
    }
    finding_orders: set[str] = set()
    for finding in value:
        require(
            isinstance(finding, dict)
            and set(finding) == expected_fields
            and finding.get("credentialKeyId") == scope.credential_key_id
            and finding.get("firstAffectedOrder") == "0"
            and finding.get("lastAffectedOrder") == scope.operation_order
            and isinstance(finding.get("operationOrder"), str)
            and re.fullmatch(r"0|[1-9][0-9]*", finding["operationOrder"]) is not None,
            f"{label} did not retain flat, canonical review findings",
        )
        if not isinstance(finding, dict):
            raise TypeError("require must reject a non-object review finding")
        finding_orders.add(finding["operationOrder"])
    if require_complete_window:
        expected_orders = {str(order) for order in range(int(scope.operation_order) + 1)}
        require(
            finding_orders == expected_orders and len(value) == len(expected_orders),
            f"{label} did not retain every affected operation in its declared review window",
        )
