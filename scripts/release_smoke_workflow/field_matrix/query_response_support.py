"""Fail-closed JSON and fact-shape primitives for query scenarios."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ..models import ReleaseSmokeConfig
from ..support import parse_json_output, require
from .query_models import AttestationHeadFacts


def _success_payload(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output: str,
) -> dict[str, Any]:
    envelope = parse_json_output(
        output,
        f"{config.label} field-matrix {operation_id}[json] did not emit valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} field-matrix {operation_id}[json] did not report ok status",
    )
    return _required_mapping(
        envelope,
        "payload",
        f"{config.label} field-matrix {operation_id}[json]",
    )


def _require_attestation_head_payload(
    payload: Mapping[str, Any],
    expected: AttestationHeadFacts,
    purpose: str,
) -> None:
    verified_head = _required_mapping(payload, "verifiedAttestationHead", purpose)
    require(
        payload.get("bookId") == expected.book_id
        and set(verified_head) == {"operationOrder", "operationHead"}
        and verified_head.get("operationOrder") == expected.operation_order
        and verified_head.get("operationHead") == expected.operation_head
        and payload.get("previousHead") == expected.previous_head,
        f"{purpose} did not retain the verified attestation head",
    )


def _require_mapping_list_fact(
    payload: Mapping[str, Any],
    list_key: str,
    fact_key: str,
    expected_fact: str,
    purpose: str,
) -> None:
    rows = payload.get(list_key)
    require(
        isinstance(rows, list)
        and any(isinstance(row, Mapping) and row.get(fact_key) == expected_fact for row in rows),
        f"{purpose} did not retain the known scenario fact {expected_fact!r} in a data row",
    )


def _required_mapping(
    container: Mapping[str, Any],
    key: str,
    purpose: str,
) -> dict[str, Any]:
    value = container.get(key)
    require(isinstance(value, dict), f"{purpose} did not expose {key} as an object")
    if not isinstance(value, dict):
        raise TypeError("require must reject a non-object field")
    return value


def _required_text(container: Mapping[str, Any], key: str, purpose: str) -> str:
    value = container.get(key)
    require(
        isinstance(value, str) and bool(value.strip()),
        f"{purpose} did not expose non-blank {key}",
    )
    if not isinstance(value, str):
        raise TypeError("require must reject a non-text field")
    return value


def _required_integer(container: Mapping[str, Any], key: str, purpose: str) -> int:
    value = container.get(key)
    require(
        isinstance(value, int) and not isinstance(value, bool),
        f"{purpose} did not expose integer {key}",
    )
    if not isinstance(value, int) or isinstance(value, bool):
        raise TypeError("require must reject a non-integer field")
    return value


def _require_successful_query_envelope(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output_mode: str,
    output: str,
) -> None:
    if output_mode != "json":
        require(
            bool(output.strip()),
            f"{config.label} field-matrix {operation_id}[{output_mode}] emitted empty stdout",
        )
        return
    envelope = parse_json_output(
        output,
        f"{config.label} field-matrix {operation_id}[json] did not emit valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} field-matrix {operation_id}[json] did not report ok status",
    )
