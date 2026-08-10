"""Canonical artifact-publication proof for field-matrix command responses.

The field matrix records an artifact only after both the live capability
descriptor and the response's exact, runtime-visible artifact paths agree.
Keeping that comparison here prevents scenario families from drifting into
different notions of what an advertised artifact response proves.
"""

from __future__ import annotations

from collections.abc import Mapping

from ..artifact_contracts import expected_reported_path
from ..models import ReleaseSmokeConfig, SmokePath
from ..support import require, require_string
from .artifact_publication_evidence import PublicationEvidenceForm, require_publication_evidence
from .capabilities import ArtifactCapability, OperationCapability


def require_exact_json_artifact_publication(
    envelope: Mapping[str, object] | None,
    operation: OperationCapability,
    artifact_paths: Mapping[ArtifactCapability, SmokePath],
    config: ReleaseSmokeConfig,
    label: str,
    evidence_form: PublicationEvidenceForm = "retained-stage",
) -> None:
    """Require JSON ``artifacts`` to match the live descriptors and requested paths exactly."""
    require(
        envelope is not None,
        f"{config.label} {label} did not expose a JSON artifact publication envelope",
    )
    if envelope is None:
        raise AssertionError("JSON artifact publication requires an envelope")
    raw_artifacts = envelope.get("artifacts")
    require(
        isinstance(raw_artifacts, list),
        f"{config.label} {label} did not publish artifacts[]",
    )
    if not isinstance(raw_artifacts, list):
        raise TypeError("artifact publication requires an artifacts array")
    expected_artifact_paths = {
        (artifact.format, expected_reported_path(config, path)): path
        for artifact, path in artifact_paths.items()
    }
    published_artifacts: set[tuple[str, str]] = set()
    for raw_artifact in raw_artifacts:
        require(
            isinstance(raw_artifact, dict),
            f"{config.label} {label} published a non-object artifact",
        )
        if not isinstance(raw_artifact, dict):
            raise TypeError("artifact publication requires object entries")
        artifact_format = require_string(raw_artifact, "format")
        reported_path = require_string(raw_artifact, "path")
        artifact_path = expected_artifact_paths.get((artifact_format, reported_path))
        if artifact_path is not None:
            require_publication_evidence(
                config, artifact_path, reported_path, raw_artifact, label, evidence_form
            )
        published_artifacts.add((artifact_format, reported_path))
    require(
        len(published_artifacts) == len(raw_artifacts),
        f"{config.label} {label} published duplicate artifacts[] entries",
    )
    expected_artifacts = set(expected_artifact_paths)
    require(
        published_artifacts == expected_artifacts,
        _artifact_publication_mismatch_message(
            operation.operation_id,
            expected_artifacts,
            published_artifacts,
        ),
    )


def _artifact_publication_mismatch_message(
    operation_id: str,
    expected_artifacts: set[tuple[str, str]],
    published_artifacts: set[tuple[str, str]],
) -> str:
    def render(artifacts: set[tuple[str, str]]) -> str:
        return ", ".join(
            f"{artifact_format} at {path}" for artifact_format, path in sorted(artifacts)
        )

    return (
        f"field-matrix {operation_id} artifacts[] differs from the exact advertised artifact paths; "
        f"expected={render(expected_artifacts)} published={render(published_artifacts)}"
    )
