"""Canonical artifact-publication proof for field-matrix command responses.

The field matrix records an artifact only after both the live capability
descriptor and the response's exact, runtime-visible artifact paths agree.
Keeping that comparison here prevents scenario families from drifting into
different notions of what an advertised artifact response proves.
"""

from __future__ import annotations

import os
import stat
from collections.abc import Mapping

from ..artifact_contracts import expected_reported_path
from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from ..path_support import normalize_reported_path, normalized_path_components
from ..support import require, require_string
from .capabilities import ArtifactCapability, OperationCapability


def require_exact_json_artifact_publication(
    envelope: Mapping[str, object] | None,
    operation: OperationCapability,
    artifact_paths: Mapping[ArtifactCapability, SmokePath],
    config: ReleaseSmokeConfig,
    label: str,
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
            require_retained_stage_evidence(
                config,
                artifact_path,
                reported_path,
                raw_artifact.get("retainedStage"),
                label,
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


def require_retained_stage_evidence(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    reported_artifact_path: str,
    raw_retained_stage: object,
    label: str,
) -> None:
    """Require one successful publication to expose and retain its private sibling stage."""
    require(
        isinstance(raw_retained_stage, str) and bool(raw_retained_stage.strip()),
        f"{config.label} {label} did not publish one non-blank retainedStage artifact fact",
    )
    if not isinstance(raw_retained_stage, str):
        raise TypeError("artifact publication requires a string retainedStage")
    retained_stage = raw_retained_stage.strip()
    require(
        normalize_reported_path(retained_stage) != normalize_reported_path(reported_artifact_path),
        f"{config.label} {label} reused the final artifact path as its retained stage",
    )
    artifact_components = normalized_path_components(reported_artifact_path)
    retained_stage_components = normalized_path_components(retained_stage)
    require(
        artifact_components[:-1] == retained_stage_components[:-1]
        and bool(retained_stage_components),
        f"{config.label} {label} published a retained stage outside its final artifact parent",
    )
    _require_private_local_stage(
        config,
        artifact_path,
        retained_stage_components[-1],
        label,
    )


def require_text_retained_stage_evidence(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    reported_artifact_path: str,
    retained_stage: str,
    label: str,
) -> None:
    """Require a redacted text stage fact to name the retained local sibling evidence."""
    require(
        retained_stage.startswith("<redacted>/"),
        f"{config.label} {label} did not redact its retained stage text fact",
    )
    require(
        normalize_reported_path(retained_stage) != normalize_reported_path(reported_artifact_path),
        f"{config.label} {label} reused the final artifact path as its retained stage",
    )
    artifact_components = normalized_path_components(reported_artifact_path)
    retained_stage_components = normalized_path_components(retained_stage)
    require(
        artifact_components[:-1] == retained_stage_components[:-1]
        and bool(retained_stage_components),
        f"{config.label} {label} reported a retained stage outside its artifact parent",
    )
    _require_private_local_stage(
        config,
        artifact_path,
        retained_stage_components[-1],
        label,
    )


def _require_private_local_stage(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    retained_stage_name: str,
    label: str,
) -> None:
    try:
        final_parent = artifact_path.local_path.resolve(strict=True).parent
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} {label} could not resolve the published artifact parent"
        ) from exc
    retained_stage_path = final_parent / retained_stage_name
    try:
        retained_stage_status = retained_stage_path.lstat()
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} {label} did not retain its reported local artifact stage"
        ) from exc
    require(
        stat.S_ISREG(retained_stage_status.st_mode),
        f"{config.label} {label} did not retain a regular artifact stage",
    )
    try:
        retained_stage_bytes = retained_stage_path.read_bytes()
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} {label} could not read its retained artifact stage"
        ) from exc
    require(
        bool(retained_stage_bytes),
        f"{config.label} {label} did not retain a non-empty regular artifact stage",
    )
    if os.name == "posix":
        require(
            stat.S_IMODE(retained_stage_status.st_mode) == 0o600,
            f"{config.label} {label} retained stage did not have 0600 permissions",
        )
