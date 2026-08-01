from __future__ import annotations

import os
from pathlib import Path

from .evidence_fixtures import retained_source_document
from .models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from .path_support import (
    display_path_components,
    normalize_reported_path,
    normalized_path_components,
)


def expected_source_document(
    request_prefix: str,
    evidence_suffix: str,
    document_date: str,
) -> dict[str, str]:
    return retained_source_document(request_prefix, evidence_suffix, document_date)


def expected_reported_path(config: ReleaseSmokeConfig, smoke_path: SmokePath) -> str:
    """Return the exact runtime-visible path for one smoke-workflow path argument."""
    if config.reported_work_root is not None and smoke_path.argument != str(smoke_path.local_path):
        return str(config.reported_work_root / smoke_path.relative_path)
    return str(smoke_path.local_path)


def canonical_pdf_reported_path(config: ReleaseSmokeConfig, artifact_path: SmokePath) -> str:
    """Return the resolved physical PDF path visible to the selected runtime.

    A report export confirms the artifact actually published by the no-clobber
    publication flow, rather than preserving a caller-supplied symlink spelling.
    Container-relative invocations retain the container's public work-root
    namespace after the host-side path has been resolved.
    """
    try:
        canonical_local_path = artifact_path.local_path.resolve(strict=False)
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"could not resolve PDF artifact path for release smoke: {artifact_path.local_path}"
        ) from exc
    if config.reported_work_root is None or artifact_path.argument == str(artifact_path.local_path):
        return str(canonical_local_path)
    try:
        canonical_relative_path = canonical_local_path.relative_to(
            config.work_root.resolve(strict=True)
        )
    except (OSError, ValueError) as exc:
        raise ReleaseSmokeFailure(
            "canonical PDF artifact path escaped release-smoke work root: "
            + str(canonical_local_path)
        ) from exc
    return str(config.reported_work_root / canonical_relative_path)


def expected_public_pdf_artifact_path_hint(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
) -> str:
    """Mirror the CLI's redacted physical PDF-path confirmation."""
    return public_path_hint_for_runtime_path(canonical_pdf_reported_path(config, artifact_path))


def reported_pdf_artifact_path_matches(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    reported_path: str,
) -> bool:
    """Require a PDF response to name its canonical physical artifact path.

    Text reports intentionally redact the leading path components, while JSON
    reports publish the complete runtime-visible physical path. Both forms are
    exact contracts; a caller alias or filename-only suffix is not sufficient.
    """
    expected_path = canonical_pdf_reported_path(config, artifact_path)
    return normalize_reported_path(reported_path) in {
        normalize_reported_path(expected_path),
        normalize_reported_path(expected_public_pdf_artifact_path_hint(config, artifact_path)),
    }


def reported_artifact_path_matches(
    config: ReleaseSmokeConfig,
    smoke_path: SmokePath,
    reported_path: str,
) -> bool:
    expected_path = expected_reported_path(config, smoke_path)
    if normalize_reported_path(reported_path) == normalize_reported_path(expected_path):
        return True
    expected_public_hint = expected_public_artifact_path_hint(smoke_path)
    if normalize_reported_path(reported_path) == normalize_reported_path(expected_public_hint):
        return True
    reported_components = normalized_path_components(reported_path)
    relative_components = normalized_path_components(smoke_path.relative_path.as_posix())
    return (
        len(reported_components) >= len(relative_components)
        and reported_components[-len(relative_components) :] == relative_components
    )


def expected_public_artifact_path_hint(smoke_path: SmokePath) -> str:
    """Mirror the CLI redacted text-path contract without resolving symlinks.

    The CLI normalizes an absolute path and preserves its last three name
    segments.  Checking only a filename cannot match a valid operator-facing
    hint such as ``<redacted>/private/output/key``.
    """
    return public_path_hint_for_runtime_path(str(Path(os.path.abspath(smoke_path.local_path))))


def public_path_hint_for_runtime_path(runtime_path: str) -> str:
    """Mirror the CLI's three-component redacted display-path contract.

    The input is already the physical runtime path selected by the relevant
    publication flow.  Components retain their display casing because the CLI
    presents a canonical operator path rather than a case-insensitive lookup key.
    """
    path_components = display_path_components(runtime_path)
    if not path_components:
        return "<redacted>"
    return "<redacted>/" + "/".join(path_components[-3:])
