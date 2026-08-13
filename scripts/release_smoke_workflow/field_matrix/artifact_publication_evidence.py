"""Evidence-form assertions for public artifact publication responses."""

from __future__ import annotations

import os
import stat
from collections.abc import Mapping
from typing import Literal

from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from ..path_support import normalize_reported_path, normalized_path_components
from ..support import require

PublicationEvidenceForm = Literal["retained-stage", "publication-transaction"]


def require_publication_evidence(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    reported_artifact_path: str,
    raw_artifact: Mapping[str, object],
    label: str,
    evidence_form: PublicationEvidenceForm,
) -> None:
    """Require the evidence form declared for this publisher migration state."""
    match evidence_form:
        case "retained-stage":
            require(
                raw_artifact.get("publicationTransaction") is None,
                f"{config.label} {label} mixed legacy-stage and transaction publication facts",
            )
            require_retained_stage_evidence(
                config,
                artifact_path,
                reported_artifact_path,
                raw_artifact.get("retainedStage"),
                label,
            )
        case "publication-transaction":
            require(
                raw_artifact.get("retainedStage") is None,
                f"{config.label} {label} exposed a private retainedStage after transaction migration",
            )
            require_publication_transaction_evidence(
                config, raw_artifact.get("publicationTransaction"), label
            )


def require_publication_transaction_evidence(
    config: ReleaseSmokeConfig, raw_transaction: object, label: str
) -> None:
    """Require the public, ID-only transaction evidence used after a publisher migration."""
    require(
        isinstance(raw_transaction, Mapping),
        f"{config.label} {label} did not publish one publicationTransaction artifact fact",
    )
    if not isinstance(raw_transaction, Mapping):
        raise TypeError("transaction publication requires an object")
    for field_name in ("id", "state", "commitOutcome", "cleanupOutcome"):
        value = raw_transaction.get(field_name)
        require(
            isinstance(value, str) and bool(value.strip()),
            f"{config.label} {label} published a blank publicationTransaction.{field_name}",
        )


def require_text_publication_transaction_evidence(
    config: ReleaseSmokeConfig, transaction_id: str, label: str
) -> None:
    """Require the non-blank transaction identifier exposed by the text projection."""
    require(
        bool(transaction_id.strip()),
        f"{config.label} {label} published a blank publication transaction identifier",
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
    _require_private_local_stage(config, artifact_path, retained_stage_components[-1], label)


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
    _require_private_local_stage(config, artifact_path, retained_stage_components[-1], label)


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
