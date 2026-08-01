"""Bundle-manifest equivalence policy for the canonical Windows publication plan."""

from __future__ import annotations

from windows_publication_plan_policy import validate_canonical_plan
from windows_publication_policy_boundary import (
    PublicationPolicyError,
    load_json_object,
    normalized_absolute_path,
    paths_equal,
    require_only_properties,
)


def validate_manifest_artifacts(
    *, plan: dict[str, object], bundle_archive_manifest: str
) -> dict[str, str]:
    """Validate the generated manifest against the canonical plan, without opening files."""

    canonical_plan = validate_canonical_plan(plan)
    manifest = load_json_object(bundle_archive_manifest, "bundle archive manifest")
    require_only_properties(
        manifest,
        ("archivePath", "checksumPath"),
        "bundle archive manifest",
    )
    _validate_manifest_path(
        manifest,
        property_name="archivePath",
        expected_path=canonical_plan["archivePath"],
    )
    _validate_manifest_path(
        manifest,
        property_name="checksumPath",
        expected_path=canonical_plan["checksumPath"],
    )
    return {
        "archivePath": canonical_plan["archivePath"],
        "checksumPath": canonical_plan["checksumPath"],
    }


def _validate_manifest_path(
    manifest: dict[str, object],
    *,
    property_name: str,
    expected_path: str,
) -> None:
    declared_path = manifest.get(property_name)
    if not isinstance(declared_path, str) or not declared_path.strip():
        raise PublicationPolicyError(f"bundle archive manifest did not declare {property_name}")
    normalized_declared_path = normalized_absolute_path(
        declared_path, f"bundle archive manifest {property_name}"
    )
    if not paths_equal(normalized_declared_path, expected_path):
        raise PublicationPolicyError(
            "bundle archive manifest "
            + property_name
            + " does not match the canonical Windows publication path"
        )
