from __future__ import annotations

from .evidence_fixtures import retained_source_document
from .models import ReleaseSmokeConfig, SmokePath
from .path_support import normalize_reported_path, normalized_path_components


def expected_source_document(
    actor_prefix: str,
    evidence_suffix: str,
    document_date: str,
) -> dict[str, str]:
    return retained_source_document(actor_prefix, evidence_suffix, document_date)


def expected_reported_artifact_path(config: ReleaseSmokeConfig, smoke_path: SmokePath) -> str:
    if config.reported_work_root is not None and smoke_path.argument != str(smoke_path.local_path):
        return str(config.reported_work_root / smoke_path.relative_path)
    return str(smoke_path.local_path)


def reported_artifact_path_matches(
    config: ReleaseSmokeConfig,
    smoke_path: SmokePath,
    reported_path: str,
) -> bool:
    expected_path = expected_reported_artifact_path(config, smoke_path)
    if normalize_reported_path(reported_path) == normalize_reported_path(expected_path):
        return True
    expected_public_hint = f"<redacted>/{smoke_path.local_path.name}"
    if normalize_reported_path(reported_path) == normalize_reported_path(expected_public_hint):
        return True
    reported_components = normalized_path_components(reported_path)
    relative_components = normalized_path_components(smoke_path.relative_path.as_posix())
    return (
        len(reported_components) >= len(relative_components)
        and reported_components[-len(relative_components) :] == relative_components
    )
