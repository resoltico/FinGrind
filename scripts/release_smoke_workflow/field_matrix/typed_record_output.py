"""Shared output, capability, and artifact assertions for typed-record scenarios."""

from __future__ import annotations

from collections.abc import Mapping
from pathlib import Path

from ..models import ReleaseSmokeConfig
from ..support import parse_json_output, require
from .capabilities import ArtifactCapability, OperationCapability
from .context import current_recorder, record_verified_artifact
from .typed_record_models import JsonObject


def _successful_envelope(
    output: str,
    config: ReleaseSmokeConfig,
    label: str,
) -> JsonObject:
    envelope = parse_json_output(
        output,
        f"{config.label} {label} output was not valid JSON",
    )
    require(envelope.get("status") == "ok", f"{config.label} {label} did not report ok status")
    _payload(envelope, config, label)
    return envelope


def _record_exact_artifacts(
    operation: OperationCapability,
    artifact_paths: Mapping[ArtifactCapability, Path],
) -> None:
    require(
        set(operation.artifact_outputs) == set(artifact_paths),
        f"field-matrix {operation.operation_id} artifact coverage differs from live capabilities",
    )
    for artifact, path in artifact_paths.items():
        _require_nonempty_file(path, None, f"{operation.operation_id} {_artifact_label(artifact)}")
        record_verified_artifact(operation.operation_id, artifact)


def _require_nonempty_file(
    path: Path,
    config: ReleaseSmokeConfig | None,
    label: str,
) -> None:
    prefix = f"{config.label} " if config is not None else "field-matrix "
    require(
        path.is_file() and bool(path.read_bytes()),
        f"{prefix}{label} did not create a non-empty file at {path}",
    )


def _payload(envelope: Mapping[str, object], config: ReleaseSmokeConfig, label: str) -> JsonObject:
    payload = envelope.get("payload")
    require(
        isinstance(payload, dict),
        f"{config.label} {label} did not expose a JSON payload object",
    )
    if not isinstance(payload, dict):
        raise TypeError("require must reject a non-object JSON payload")
    return payload


def _operation(operation_ids: Mapping[str, str], key: str) -> str:
    operation_id = operation_ids.get(key)
    require(
        isinstance(operation_id, str) and bool(operation_id),
        f"typed-record matrix needs the published operation-id key {key}",
    )
    if not isinstance(operation_id, str) or not operation_id:
        raise AssertionError("require must reject a missing operation id")
    return operation_id


def _operation_capability(operation_id: str) -> OperationCapability:
    recorder = current_recorder()
    require(
        recorder is not None,
        f"field-matrix {operation_id} needs an active capability recorder for output validation",
    )
    if recorder is None:
        raise AssertionError("typed-record output validation requires an active recorder")
    return recorder.capabilities.operation(operation_id)


def _artifact(operation: OperationCapability, artifact_format: str) -> ArtifactCapability:
    matches = tuple(
        artifact for artifact in operation.artifact_outputs if artifact.format == artifact_format
    )
    require(
        len(matches) == 1,
        f"field-matrix {operation.operation_id} did not advertise exactly one "
        f"{artifact_format} artifact",
    )
    if len(matches) != 1:
        raise AssertionError("artifact publication requires one matching capability descriptor")
    return matches[0]


def _artifact_label(artifact: ArtifactCapability) -> str:
    return f"{artifact.format} via {artifact.option}"
