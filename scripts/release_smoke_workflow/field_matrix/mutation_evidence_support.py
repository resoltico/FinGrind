"""Shared low-level parsing rules for mutation-response evidence.

Route-specific assertions live in the focused bootstrap, account, posting, and
tax evidence modules. This module owns the common envelope, text-label, and
attestation-commit proof rules so every mutation family applies the same
fail-closed interpretation.
"""

from __future__ import annotations

import re
from collections.abc import Mapping
from typing import Any

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeConfig
from ..support import require


def _require_attestation_commit(
    payload: Mapping[str, Any],
    expected_head: VerifiedAttestationHead,
    config: ReleaseSmokeConfig,
    operation_id: str,
    purpose: str,
    output_mode: str,
) -> None:
    commit = payload.get("attestationCommit")
    require(
        isinstance(commit, Mapping)
        and commit.get("operationOrder") == expected_head.operation_order
        and commit.get("operationHead") == expected_head.operation_head,
        f"{config.label} {purpose} {operation_id}[{output_mode}] did not bind its posting "
        "response to the verified appended attestation head",
    )


def _success_payload(
    envelope: Mapping[str, Any],
    config: ReleaseSmokeConfig,
    operation_id: str,
    purpose: str,
    output_mode: str,
) -> Mapping[str, Any]:
    require(
        envelope.get("status") == "ok",
        f"{config.label} {purpose} {operation_id}[{output_mode}] did not report ok status",
    )
    payload = envelope.get("payload")
    require(
        isinstance(payload, Mapping),
        f"{config.label} {purpose} {operation_id}[{output_mode}] did not expose a payload object",
    )
    if not isinstance(payload, Mapping):
        raise TypeError("require must reject a non-object mutation payload")
    return payload


def _required_labeled_text_value(
    output: str,
    label: str,
    config: ReleaseSmokeConfig,
    operation_id: str,
    purpose: str,
) -> str:
    values: list[str] = []
    expression = re.compile(rf"^{re.escape(label)}\s*:\s*(?P<value>\S.*?)\s*$")
    for line in output.splitlines():
        match = expression.match(line)
        if match is not None:
            values.append(match.group("value"))
    require(
        len(values) == 1,
        f"{config.label} {purpose} {operation_id}[text] did not expose one {label} value",
    )
    if len(values) != 1:
        raise AssertionError("require must reject missing or duplicate text evidence labels")
    return values[0]


def _require_text_title(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output: str,
    expected_title: str,
    purpose: str,
) -> None:
    first_line = next((line.strip() for line in output.splitlines() if line.strip()), "")
    require(
        first_line == expected_title,
        f"{config.label} {purpose} {operation_id}[text] did not emit canonical title "
        f"{expected_title!r}",
    )


def _require_nonblank_text_label(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output: str,
    label: str,
    purpose: str,
) -> None:
    value = _required_labeled_text_value(output, label, config, operation_id, purpose)
    require(
        bool(value.strip()),
        f"{config.label} {purpose} {operation_id}[text] did not expose non-blank {label}",
    )


def _required_text(
    container: Mapping[str, object],
    key: str,
    config: ReleaseSmokeConfig,
    operation_id: str,
    purpose: str,
    location: str,
) -> str:
    value = container.get(key)
    require(
        isinstance(value, str) and bool(value.strip()),
        f"{config.label} {purpose} {operation_id}[{location}] did not expose non-blank {key}",
    )
    if not isinstance(value, str):
        raise TypeError("require must reject non-text mutation evidence")
    return value
