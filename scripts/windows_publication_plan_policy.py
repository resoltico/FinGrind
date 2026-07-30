"""Canonical artifact-plan policy for one Windows publication target."""

from __future__ import annotations

from windows_publication_policy_boundary import (
    PublicationPolicyError,
    join_path,
    load_json_object,
    normalized_absolute_path,
    paths_equal,
    release_path_component,
    require_only_properties,
    required_object,
    required_text,
)

_PLAN_KEYS = (
    "repositoryRoot",
    "cliBuildDirectory",
    "manifestPath",
    "archivePath",
    "checksumPath",
    "projectVersion",
    "bundleClassifier",
)


def build_publication_plan(
    *,
    repository_root: str,
    gradle_properties: str,
    bundle_layout_contract: str,
    expected_operating_system_id: str,
    expected_architecture_id: str,
    bundle_classifier: str,
) -> dict[str, str]:
    """Build the sole canonical artifact plan for one Windows publication target."""

    normalized_repository_root = normalized_absolute_path(repository_root, "target repository")
    project_version = release_path_component(
        _project_version(gradle_properties), "target Gradle properties release version"
    )
    normalized_classifier = release_path_component(
        required_text({"bundleClassifier": bundle_classifier}, "bundleClassifier", "request"),
        "requested bundle classifier",
    )
    expected_os = required_text(
        {"expectedOperatingSystemId": expected_operating_system_id},
        "expectedOperatingSystemId",
        "request",
    )
    expected_architecture = required_text(
        {"expectedArchitectureId": expected_architecture_id},
        "expectedArchitectureId",
        "request",
    )
    _validate_bundle_target(
        bundle_layout_contract,
        bundle_classifier=normalized_classifier,
        expected_operating_system_id=expected_os,
        expected_architecture_id=expected_architecture,
    )

    cli_build_directory = join_path(normalized_repository_root, "cli", "build")
    manifest_path = join_path(
        cli_build_directory, "generated", "bundle", "bundle-archive-manifest.json"
    )
    archive_path = join_path(
        cli_build_directory,
        "distributions",
        f"fingrind-{project_version}-{normalized_classifier}.zip",
    )
    return {
        "repositoryRoot": normalized_repository_root,
        "cliBuildDirectory": cli_build_directory,
        "manifestPath": manifest_path,
        "archivePath": archive_path,
        "checksumPath": archive_path + ".sha256",
        "projectVersion": project_version,
        "bundleClassifier": normalized_classifier,
    }


def validate_canonical_plan(plan: dict[str, object]) -> dict[str, str]:
    """Reject a plan whose fields differ from the one canonical release-artifact topology."""

    require_only_properties(plan, _PLAN_KEYS, "Windows publication plan")
    normalized_plan = {
        "repositoryRoot": normalized_absolute_path(
            required_text(plan, "repositoryRoot", "Windows publication plan"),
            "Windows publication plan repositoryRoot",
        ),
        "cliBuildDirectory": normalized_absolute_path(
            required_text(plan, "cliBuildDirectory", "Windows publication plan"),
            "Windows publication plan cliBuildDirectory",
        ),
        "manifestPath": normalized_absolute_path(
            required_text(plan, "manifestPath", "Windows publication plan"),
            "Windows publication plan manifestPath",
        ),
        "archivePath": normalized_absolute_path(
            required_text(plan, "archivePath", "Windows publication plan"),
            "Windows publication plan archivePath",
        ),
        "checksumPath": normalized_absolute_path(
            required_text(plan, "checksumPath", "Windows publication plan"),
            "Windows publication plan checksumPath",
        ),
        "projectVersion": release_path_component(
            required_text(plan, "projectVersion", "Windows publication plan"),
            "Windows publication plan projectVersion",
        ),
        "bundleClassifier": release_path_component(
            required_text(plan, "bundleClassifier", "Windows publication plan"),
            "Windows publication plan bundleClassifier",
        ),
    }
    expected_cli_build_directory = join_path(normalized_plan["repositoryRoot"], "cli", "build")
    expected_manifest_path = join_path(
        expected_cli_build_directory, "generated", "bundle", "bundle-archive-manifest.json"
    )
    expected_archive_path = join_path(
        expected_cli_build_directory,
        "distributions",
        "fingrind-"
        + normalized_plan["projectVersion"]
        + "-"
        + normalized_plan["bundleClassifier"]
        + ".zip",
    )
    expected_values = {
        "cliBuildDirectory": expected_cli_build_directory,
        "manifestPath": expected_manifest_path,
        "archivePath": expected_archive_path,
        "checksumPath": expected_archive_path + ".sha256",
    }
    for property_name, expected_value in expected_values.items():
        if not paths_equal(normalized_plan[property_name], expected_value):
            raise PublicationPolicyError(
                f"Windows publication plan {property_name} is not the canonical publication path"
            )
    return normalized_plan


def _project_version(gradle_properties: str) -> str:
    version_lines = [line for line in gradle_properties.splitlines() if line.startswith("version=")]
    if len(version_lines) != 1:
        raise PublicationPolicyError(
            "target Gradle properties must declare exactly one release version"
        )
    project_version = version_lines[0][len("version=") :]
    if not project_version:
        raise PublicationPolicyError("target Gradle properties declares a blank release version")
    return project_version


def _validate_bundle_target(
    bundle_layout_contract: str,
    *,
    bundle_classifier: str,
    expected_operating_system_id: str,
    expected_architecture_id: str,
) -> None:
    bundle_layout = load_json_object(bundle_layout_contract, "target bundle layout contract")
    bundle_targets = required_object(
        bundle_layout, "bundleTargets", "target bundle layout contract"
    )
    bundle_target = bundle_targets.get(bundle_classifier)
    if bundle_target is None:
        raise PublicationPolicyError(
            f"target bundle layout contract does not declare classifier {bundle_classifier}"
        )
    if not isinstance(bundle_target, dict):
        raise PublicationPolicyError(
            f"target bundle layout contract classifier {bundle_classifier} must be one object"
        )
    operating_system_id = required_text(
        bundle_target, "operatingSystemId", f"target bundle layout contract {bundle_classifier}"
    )
    architecture_id = required_text(
        bundle_target, "architectureId", f"target bundle layout contract {bundle_classifier}"
    )
    archive_format = required_text(
        bundle_target, "archiveFormat", f"target bundle layout contract {bundle_classifier}"
    )
    if (
        operating_system_id != expected_operating_system_id
        or architecture_id != expected_architecture_id
        or archive_format != "zip"
    ):
        raise PublicationPolicyError(
            "target bundle layout contract does not describe the requested Windows publication target"
        )
