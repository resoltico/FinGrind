"""Bundle-layout and bundle-publication contract helpers."""

from __future__ import annotations

from contract_value_support import require_only_properties, required_object


def load_bundle_layout_targets(
    document: dict[str, object], schema: dict[str, object]
) -> dict[str, dict[str, str]]:
    bundle_targets_key = _required_exact_text(schema, "bundleTargets", "bundle-layout schema")
    operating_system_id_key = _required_exact_text(
        schema, "operatingSystemId", "bundle-layout schema"
    )
    architecture_id_key = _required_exact_text(schema, "architectureId", "bundle-layout schema")
    archive_format_key = _required_exact_text(schema, "archiveFormat", "bundle-layout schema")
    launcher_path_key = _required_exact_text(schema, "launcherPath", "bundle-layout schema")
    launcher_command_key = _required_exact_text(schema, "launcherCommand", "bundle-layout schema")
    sqlite_library_file_name_key = _required_exact_text(
        schema, "sqliteLibraryFileName", "bundle-layout schema"
    )
    compatibility_label_key = _required_exact_text(
        schema, "compatibilityLabel", "bundle-layout schema"
    )
    minimum_glibc_version_key = _required_exact_text(
        schema, "minimumGlibcVersion", "bundle-layout schema"
    )
    compatibility_smoke_container_image_key = _required_exact_text(
        schema, "compatibilitySmokeContainerImage", "bundle-layout schema"
    )
    bundle_targets = required_object(document, bundle_targets_key)
    targets: dict[str, dict[str, str]] = {}
    for classifier, raw_target in bundle_targets.items():
        checked_classifier = _required_classifier(classifier)
        targets[checked_classifier] = _load_bundle_layout_target(
            raw_target,
            classifier=checked_classifier,
            operating_system_id_key=operating_system_id_key,
            architecture_id_key=architecture_id_key,
            archive_format_key=archive_format_key,
            launcher_path_key=launcher_path_key,
            launcher_command_key=launcher_command_key,
            sqlite_library_file_name_key=sqlite_library_file_name_key,
            compatibility_label_key=compatibility_label_key,
            minimum_glibc_version_key=minimum_glibc_version_key,
            compatibility_smoke_container_image_key=compatibility_smoke_container_image_key,
        )
    if not targets:
        raise ValueError("bundle layout contract must declare at least one bundle target")
    return targets


def merge_bundle_publication_targets(
    bundle_layout_targets: dict[str, dict[str, str]],
    publication_document: dict[str, object],
    publication_schema: dict[str, object],
) -> dict[str, dict[str, str]]:
    bundle_targets_key = _required_exact_text(
        publication_schema, "bundleTargets", "bundle-publication schema"
    )
    publication_status_key = _required_exact_text(
        publication_schema, "status", "bundle-publication schema"
    )
    require_only_properties(
        publication_document,
        (bundle_targets_key,),
        "bundle publication contract",
    )
    publication_targets = required_object(publication_document, bundle_targets_key)
    merged_targets: dict[str, dict[str, str]] = {
        classifier: dict(target) for classifier, target in bundle_layout_targets.items()
    }
    for classifier, raw_publication in publication_targets.items():
        checked_classifier = _required_classifier(classifier)
        _merge_bundle_publication_target(
            raw_publication,
            target=merged_targets.get(checked_classifier),
            classifier=checked_classifier,
            publication_status_key=publication_status_key,
        )
    missing_publication_targets = sorted(
        classifier
        for classifier, target in merged_targets.items()
        if "publicationStatus" not in target
    )
    if missing_publication_targets:
        raise ValueError(
            "bundle publication contract must declare publication facts for every bundle target: "
            + ", ".join(missing_publication_targets)
        )
    return merged_targets


def load_public_distribution(
    bundle_layout_targets: dict[str, dict[str, str]],
) -> dict[str, list[str]]:
    supported_targets: list[str] = []
    unsupported_targets: list[str] = []
    for classifier, target in bundle_layout_targets.items():
        if target["publicationStatus"] == "published":
            supported_targets.append(classifier)
        else:
            unsupported_targets.append(classifier)
    return {
        "supportedPublicCliBundleTargets": supported_targets,
        "unsupportedPublicCliBundleTargets": unsupported_targets,
    }


def _required_exact_text(document: dict[str, object], key: str, label: str) -> str:
    value = document.get(key)
    if not isinstance(value, str) or not value.strip() or value != value.strip():
        raise ValueError(f"{label} {key} must be one non-blank exact string")
    return value


def _required_classifier(classifier: object) -> str:
    if (
        not isinstance(classifier, str)
        or not classifier.strip()
        or classifier != classifier.strip()
    ):
        raise ValueError("bundle layout target names must be non-blank exact strings")
    return classifier


def _load_bundle_layout_target(
    raw_target: object,
    *,
    classifier: str,
    operating_system_id_key: str,
    architecture_id_key: str,
    archive_format_key: str,
    launcher_path_key: str,
    launcher_command_key: str,
    sqlite_library_file_name_key: str,
    compatibility_label_key: str,
    minimum_glibc_version_key: str,
    compatibility_smoke_container_image_key: str,
) -> dict[str, str]:
    if not isinstance(raw_target, dict):
        raise TypeError(f"bundle layout target {classifier} must be one object")
    target = {
        "operatingSystemId": _required_exact_text(
            raw_target, operating_system_id_key, f"bundle layout target {classifier}"
        ),
        "architectureId": _required_exact_text(
            raw_target, architecture_id_key, f"bundle layout target {classifier}"
        ),
        "archiveFormat": _required_exact_text(
            raw_target, archive_format_key, f"bundle layout target {classifier}"
        ),
        "launcherPath": _required_exact_text(
            raw_target, launcher_path_key, f"bundle layout target {classifier}"
        ),
        "launcherCommand": _required_exact_text(
            raw_target, launcher_command_key, f"bundle layout target {classifier}"
        ),
        "sqliteLibraryFileName": _required_exact_text(
            raw_target,
            sqlite_library_file_name_key,
            f"bundle layout target {classifier}",
        ),
        "compatibilityLabel": _required_exact_text(
            raw_target, compatibility_label_key, f"bundle layout target {classifier}"
        ),
    }
    if raw_target.get(minimum_glibc_version_key) is not None:
        target["minimumGlibcVersion"] = _required_exact_text(
            raw_target,
            minimum_glibc_version_key,
            f"bundle layout target {classifier}",
        )
    if raw_target.get(compatibility_smoke_container_image_key) is not None:
        target["compatibilitySmokeContainerImage"] = _required_exact_text(
            raw_target,
            compatibility_smoke_container_image_key,
            f"bundle layout target {classifier}",
        )
    recomposed_classifier = target["operatingSystemId"] + "-" + target["architectureId"]
    if classifier != recomposed_classifier:
        raise ValueError(
            f"bundle layout target {classifier} must agree with {recomposed_classifier}"
        )
    _validate_linux_compatibility_fields(target, classifier)
    return target


def _validate_linux_compatibility_fields(target: dict[str, str], classifier: str) -> None:
    if target["operatingSystemId"] == "linux" and "minimumGlibcVersion" not in target:
        raise ValueError(f"bundle layout target {classifier} must declare minimumGlibcVersion")
    if target["operatingSystemId"] != "linux" and "minimumGlibcVersion" in target:
        raise ValueError(
            f"bundle layout target {classifier} must omit minimumGlibcVersion outside Linux"
        )
    if target["operatingSystemId"] == "linux" and "compatibilitySmokeContainerImage" not in target:
        raise ValueError(
            f"bundle layout target {classifier} must declare compatibilitySmokeContainerImage"
        )
    if target["operatingSystemId"] != "linux" and "compatibilitySmokeContainerImage" in target:
        raise ValueError(
            "bundle layout target "
            f"{classifier} must omit compatibilitySmokeContainerImage outside Linux"
        )


def _merge_bundle_publication_target(
    raw_publication: object,
    *,
    target: dict[str, str] | None,
    classifier: str,
    publication_status_key: str,
) -> None:
    if target is None:
        raise ValueError(f"bundle publication contract declared unknown target: {classifier}")
    if not isinstance(raw_publication, dict):
        raise TypeError(f"bundle publication target {classifier} must be one object")
    require_only_properties(
        raw_publication,
        (publication_status_key,),
        f"bundle publication target {classifier}",
    )
    publication_status = _required_exact_text(
        raw_publication,
        publication_status_key,
        f"bundle publication target {classifier}",
    )
    if publication_status not in {"published", "not-published"}:
        raise ValueError(
            "bundle publication target "
            f"{classifier} declared unsupported publication status: {publication_status}"
        )
    target["publicationStatus"] = publication_status
