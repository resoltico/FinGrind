"""Shared envelope, identity, and text facts for discovery representation assertions."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ..models import ReleaseSmokeConfig
from ..support import parse_json_output, project_version, require


def success_payload(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output: str,
) -> dict[str, Any]:
    """Read the successful discovery JSON envelope and its object payload."""
    envelope = parse_json_output(
        output,
        f"{config.label} field-matrix {operation_id}[json] did not emit valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} field-matrix {operation_id}[json] did not report ok status",
    )
    return required_mapping(
        envelope,
        "payload",
        f"{config.label} field-matrix {operation_id}[json]",
    )


def required_mapping(
    container: Mapping[str, Any],
    key: str,
    purpose: str,
) -> dict[str, Any]:
    """Read one required object field without accepting mapping lookalikes."""
    value = container.get(key)
    require(isinstance(value, dict), f"{purpose} did not expose {key} as an object")
    if not isinstance(value, dict):
        raise TypeError("require must reject a non-object field")
    return value


def require_identity(
    payload: Mapping[str, Any],
    config: ReleaseSmokeConfig,
    operation_id: str,
    protocol_version: str,
) -> None:
    """Require the application identity present on discovery identity surfaces."""
    require(
        payload.get("application") == "FinGrind"
        and payload.get("version") == project_version(config.repo_root)
        and payload.get("protocolVersion") == protocol_version,
        f"{config.label} field-matrix {operation_id}[json] did not retain application identity",
    )


def command_catalog_contains(commands: Mapping[str, Any], expected_name: str) -> bool:
    """Return whether the full capability catalog contains one expected command."""
    return any(
        isinstance(command, Mapping) and command.get("name") == expected_name
        for family in commands.values()
        if isinstance(family, list)
        for command in family
    )


def require_text_facts(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output: str,
    *facts: str,
) -> None:
    """Require all operation-specific facts from a native text response."""
    missing = [fact for fact in facts if fact not in output]
    require(
        not missing,
        f"{config.label} field-matrix {operation_id}[text] omitted durable discovery facts: "
        + ", ".join(repr(fact) for fact in missing),
    )
