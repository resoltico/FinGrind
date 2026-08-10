"""Output-mode credit and published-artifact assertions for administrative workflows."""

from __future__ import annotations

from collections.abc import Mapping
from pathlib import Path

from ..artifact_contracts import reported_artifact_path_matches
from ..models import ReleaseSmokeConfig, SmokePath
from ..support import require
from .administrative_constants import (
    _JSON_MODE,
    _TEXT_ARTIFACT_LABELS,
    _TEXT_MODE,
    _TEXT_PUBLICATION_TRANSACTION_LABELS,
    _TEXT_RETAINED_STAGE_LABELS,
)
from .administrative_models import JsonObject
from .artifact_publication import require_exact_json_artifact_publication
from .artifact_publication_evidence import (
    PublicationEvidenceForm,
    require_text_publication_transaction_evidence,
    require_text_retained_stage_evidence,
)
from .capabilities import ArtifactCapability, OperationCapability
from .context import record_verified_artifact
from .pair_publication_output import single_labeled_text_value


def _record_exact_artifacts(
    operation: OperationCapability,
    artifact_paths: Mapping[ArtifactCapability, Path],
) -> None:
    advertised_artifacts = set(operation.artifact_outputs)
    actual_artifacts = set(artifact_paths)
    require(
        advertised_artifacts == actual_artifacts,
        _artifact_mismatch_message(
            operation.operation_id,
            advertised_artifacts,
            actual_artifacts,
        ),
    )
    for artifact, path in artifact_paths.items():
        require(
            path.is_file() and bool(path.read_bytes()),
            f"field-matrix {operation.operation_id} {_artifact_label(artifact)} is absent or empty",
        )
        record_verified_artifact(operation.operation_id, artifact)


def _require_artifact_publication(
    output_mode: str,
    output: str,
    envelope: JsonObject | None,
    operation: OperationCapability,
    artifact_paths: Mapping[ArtifactCapability, SmokePath],
    config: ReleaseSmokeConfig,
    label: str,
    evidence_form: PublicationEvidenceForm = "retained-stage",
) -> None:
    if output_mode == _JSON_MODE:
        require_exact_json_artifact_publication(
            envelope,
            operation,
            artifact_paths,
            config,
            label,
            evidence_form,
        )
        return
    if output_mode == _TEXT_MODE:
        _require_text_artifact_publication(
            output,
            operation,
            artifact_paths,
            config,
            label,
            evidence_form,
        )
        return
    raise AssertionError(f"unrouted artifact output mode: {output_mode}")


def _require_text_artifact_publication(
    output: str,
    operation: OperationCapability,
    artifact_paths: Mapping[ArtifactCapability, SmokePath],
    config: ReleaseSmokeConfig,
    label: str,
    evidence_form: PublicationEvidenceForm = "retained-stage",
) -> None:
    for artifact, path in artifact_paths.items():
        artifact_label = _TEXT_ARTIFACT_LABELS.get((operation.operation_id, artifact.format))
        require(
            artifact_label is not None,
            f"field-matrix {operation.operation_id} has no text artifact label for "
            f"{_artifact_label(artifact)}",
        )
        if artifact_label is None:
            raise AssertionError("artifact text publication requires a declared label")
        reported_path = single_labeled_text_value(
            output,
            artifact_label,
            f"{config.label} {label} did not publish one {artifact_label} path",
        )
        require(
            reported_artifact_path_matches(config, path, reported_path),
            f"{config.label} {label} did not publish the requested {artifact_label} path",
        )
        match evidence_form:
            case "retained-stage":
                retained_stage_label = _TEXT_RETAINED_STAGE_LABELS.get(
                    (operation.operation_id, artifact.format)
                )
                require(
                    retained_stage_label is not None,
                    f"field-matrix {operation.operation_id} has no text retained-stage label for "
                    f"{_artifact_label(artifact)}",
                )
                if retained_stage_label is None:
                    raise AssertionError(
                        "artifact text publication requires a retained-stage label"
                    )
                require_text_retained_stage_evidence(
                    config,
                    path,
                    reported_path,
                    single_labeled_text_value(
                        output,
                        retained_stage_label,
                        f"{config.label} {label} did not publish one {retained_stage_label} fact",
                    ),
                    label,
                )
            case "publication-transaction":
                transaction_label = _TEXT_PUBLICATION_TRANSACTION_LABELS.get(
                    (operation.operation_id, artifact.format)
                )
                require(
                    transaction_label is not None,
                    f"field-matrix {operation.operation_id} has no transaction label for "
                    f"{_artifact_label(artifact)}",
                )
                if transaction_label is None:
                    raise AssertionError("artifact text publication requires a transaction label")
                require_text_publication_transaction_evidence(
                    config,
                    single_labeled_text_value(
                        output,
                        transaction_label,
                        f"{config.label} {label} did not publish one {transaction_label} fact",
                    ),
                    label,
                )


def _artifact(operation: OperationCapability, artifact_format: str) -> ArtifactCapability:
    matches = tuple(
        artifact for artifact in operation.artifact_outputs if artifact.format == artifact_format
    )
    require(
        len(matches) == 1,
        f"field-matrix {operation.operation_id} did not advertise exactly one {artifact_format} artifact",
    )
    if len(matches) != 1:
        raise AssertionError("require must reject an absent or ambiguous artifact descriptor")
    return matches[0]


def _artifact_mismatch_message(
    operation_id: str,
    advertised_artifacts: set[ArtifactCapability],
    actual_artifacts: set[ArtifactCapability],
) -> str:
    return (
        f"field-matrix {operation_id} artifact coverage differs from live capabilities; "
        "advertised="
        + str(sorted(_artifact_label(artifact) for artifact in advertised_artifacts))
        + " actual="
        + str(sorted(_artifact_label(artifact) for artifact in actual_artifacts))
    )


def _artifact_label(artifact: ArtifactCapability) -> str:
    return f"{artifact.format} via {artifact.option}"
