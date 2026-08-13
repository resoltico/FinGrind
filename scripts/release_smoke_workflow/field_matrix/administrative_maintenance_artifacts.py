"""Shared output-artifact credit for administrative maintenance mutations."""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig, SmokePath
from .administrative_models import JsonObject
from .administrative_output import (
    _artifact,
    _record_exact_artifacts,
    _require_artifact_publication,
)
from .capabilities import ArtifactCapability, OperationCapability
from .output_provenance import record_proven_output_mode
from .pair_publication_output import require_maintenance_pair_publication_transaction


def _record_maintenance_artifact_response(
    output_mode: str,
    output: str,
    envelope: JsonObject | None,
    operation: OperationCapability,
    artifact_paths: Mapping[ArtifactCapability, SmokePath],
    config: ReleaseSmokeConfig,
    label: str,
    *,
    rekeyed_book_publication: SmokePath | None = None,
) -> None:
    require_maintenance_artifact_publication_transaction(
        output_mode,
        output,
        envelope,
        operation,
        artifact_paths,
        config,
        label,
        rekeyed_book_publication=rekeyed_book_publication,
    )
    _record_exact_artifacts(
        operation,
        {artifact: path.local_path for artifact, path in artifact_paths.items()},
    )
    record_proven_output_mode(operation, output_mode, output, config, label)


def require_maintenance_artifact_publication_transaction(
    output_mode: str,
    output: str,
    envelope: JsonObject | None,
    operation: OperationCapability,
    artifact_paths: Mapping[ArtifactCapability, SmokePath],
    config: ReleaseSmokeConfig,
    label: str,
    *,
    rekeyed_book_publication: SmokePath | None = None,
) -> None:
    """Require one maintenance response to use only complete transaction evidence."""
    _require_artifact_publication(
        output_mode,
        output,
        envelope,
        operation,
        artifact_paths,
        config,
        label,
        evidence_form="publication-transaction",
    )
    require_maintenance_pair_publication_transaction(
        output_mode,
        output,
        envelope,
        config,
        label,
        _pair_book_publication(operation, artifact_paths, rekeyed_book_publication),
        _pair_generated_secret_publication(operation, artifact_paths),
    )


def _pair_book_publication(
    operation: OperationCapability,
    artifact_paths: Mapping[ArtifactCapability, SmokePath],
    rekeyed_book_publication: SmokePath | None,
) -> SmokePath:
    if operation.operation_id == "backup-book":
        return artifact_paths[_artifact(operation, "backup-file")]
    if operation.operation_id == "restore-book":
        return artifact_paths[_artifact(operation, "book-file")]
    if operation.operation_id == "rekey-book":
        if rekeyed_book_publication is not None:
            return rekeyed_book_publication
        raise AssertionError("rekey-book pair evidence requires its published book path")
    raise AssertionError(f"unsupported protected-book pair operation: {operation.operation_id}")


def _pair_generated_secret_publication(
    operation: OperationCapability,
    artifact_paths: Mapping[ArtifactCapability, SmokePath],
) -> SmokePath:
    artifact_format = {
        "backup-book": "backup-key-file",
        "restore-book": "book-key-file",
        "rekey-book": "book-key-file",
    }.get(operation.operation_id)
    if artifact_format is None:
        raise AssertionError(f"unsupported protected-book pair operation: {operation.operation_id}")
    return artifact_paths[_artifact(operation, artifact_format)]
