"""Response-envelope and request-fact validation for administrative workflows."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeConfig
from ..support import parse_json_output, require
from .administrative_models import AdministrativeWorld, JsonObject


def _payload(envelope: Mapping[str, object], config: ReleaseSmokeConfig, label: str) -> JsonObject:
    payload = envelope.get("payload")
    require(
        isinstance(payload, dict),
        f"{config.label} {label} did not expose a JSON payload object",
    )
    if not isinstance(payload, dict):
        raise TypeError("require must reject a non-object payload")
    return payload


def _required_object(
    container: Mapping[str, object],
    key: str,
    config: ReleaseSmokeConfig,
    label: str,
) -> JsonObject:
    value = container.get(key)
    require(
        isinstance(value, dict),
        f"{config.label} {label} did not expose {key} as an object",
    )
    if not isinstance(value, dict):
        raise TypeError("require must reject a non-object field")
    return value


def _response_payload(
    world: AdministrativeWorld,
    operation_id: str,
    output: str,
    label: str,
) -> JsonObject:
    envelope = parse_json_output(
        output,
        f"{world.config.label} {label} {operation_id}[json] did not emit valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{world.config.label} {label} {operation_id}[json] did not report ok status",
    )
    return _payload(envelope, world.config, label)


def _require_response_attestation_commit(
    payload: Mapping[str, object],
    expected_head: VerifiedAttestationHead,
    world: AdministrativeWorld,
    operation_id: str,
    output_mode: str,
    label: str,
) -> None:
    commit = payload.get("attestationCommit")
    require(
        isinstance(commit, Mapping)
        and commit.get("operationOrder") == expected_head.operation_order
        and commit.get("operationHead") == expected_head.operation_head,
        f"{world.config.label} {label} {operation_id}[{output_mode}] did not bind its response "
        "to the verified appended attestation head",
    )


def _require_text_title(
    world: AdministrativeWorld,
    operation_id: str,
    output: str,
    expected_title: str,
    label: str,
) -> None:
    first_line = next((line.strip() for line in output.splitlines() if line.strip()), "")
    require(
        first_line == expected_title,
        f"{world.config.label} {label} {operation_id}[text] did not emit canonical title "
        f"{expected_title!r}",
    )


def _request_text(
    request: Mapping[str, object],
    parent_key: str | None,
    key: str,
    world: AdministrativeWorld,
    label: str,
) -> str:
    container = request if parent_key is None else request.get(parent_key)
    require(
        isinstance(container, Mapping),
        f"{world.config.label} {label} request did not expose {parent_key or 'root'} object",
    )
    if not isinstance(container, Mapping):
        raise TypeError("request proof requires a mapping container")
    value = container.get(key)
    require(
        isinstance(value, str) and bool(value.strip()),
        f"{world.config.label} {label} request did not expose non-blank {key}",
    )
    if not isinstance(value, str):
        raise TypeError("request proof requires a text field")
    return value


def _argument_value(
    arguments: tuple[str, ...],
    option: str,
    world: AdministrativeWorld,
    operation_id: str,
    label: str,
) -> str:
    positions = [index for index, value in enumerate(arguments) if value == option]
    require(
        len(positions) == 1 and positions[0] + 1 < len(arguments),
        f"{world.config.label} {label} {operation_id} did not retain one {option} argument",
    )
    if len(positions) != 1 or positions[0] + 1 >= len(arguments):
        raise AssertionError("route proof requires a single value-bearing command argument")
    return arguments[positions[0] + 1]
