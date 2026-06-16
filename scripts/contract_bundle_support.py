"""Bundle-layout and bundle-publication contract helpers."""

from __future__ import annotations

from contract_value_support import required_object, required_string, required_value


def load_bundle_layout_targets(
    document: dict[str, object], schema: dict[str, object]
) -> dict[str, dict[str, str]]:
    bundle_targets = required_object(document, required_string(schema, "bundleTargets"))
    minimum_glibc_version_key = required_string(schema, "minimumGlibcVersion")
    compatibility_smoke_container_image_key = required_string(
        schema, "compatibilitySmokeContainerImage"
    )
    targets: dict[str, dict[str, str]] = {}
    for classifier, raw_target in bundle_targets.items():
        targets[_normalized_classifier(classifier)] = _load_bundle_layout_target(
            raw_target,
            normalized_classifier=_normalized_classifier(classifier),
            operating_system_id_key=required_string(schema, "operatingSystemId"),
            architecture_id_key=required_string(schema, "architectureId"),
            archive_format_key=required_string(schema, "archiveFormat"),
            launcher_path_key=required_string(schema, "launcherPath"),
            launcher_command_key=required_string(schema, "launcherCommand"),
            sqlite_library_file_name_key=required_string(schema, "sqliteLibraryFileName"),
            compatibility_label_key=required_string(schema, "compatibilityLabel"),
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
    publication_targets = required_object(
        publication_document, required_string(publication_schema, "bundleTargets")
    )
    merged_targets: dict[str, dict[str, str]] = {
        classifier: dict(target) for classifier, target in bundle_layout_targets.items()
    }
    for classifier, raw_publication in publication_targets.items():
        _merge_bundle_publication_target(
            raw_publication,
            target=merged_targets.get(_normalized_classifier(classifier)),
            normalized_classifier=_normalized_classifier(classifier),
            publication_status_key=required_string(publication_schema, "status"),
            runner_label_key=required_string(publication_schema, "runnerLabel"),
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


def _normalized_classifier(classifier: object) -> str:
    normalized_classifier = classifier.strip() if isinstance(classifier, str) else ""
    if not normalized_classifier:
        raise ValueError("bundle layout target names must be non-blank")
    return normalized_classifier


def _load_bundle_layout_target(
    raw_target: object,
    *,
    normalized_classifier: str,
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
        raise ValueError(f"bundle layout target {normalized_classifier} must be one object")
    target = {
        "operatingSystemId": required_value(raw_target, operating_system_id_key),
        "architectureId": required_value(raw_target, architecture_id_key),
        "archiveFormat": required_value(raw_target, archive_format_key),
        "launcherPath": required_value(raw_target, launcher_path_key),
        "launcherCommand": required_value(raw_target, launcher_command_key),
        "sqliteLibraryFileName": required_value(raw_target, sqlite_library_file_name_key),
        "compatibilityLabel": required_value(raw_target, compatibility_label_key),
    }
    if raw_target.get(minimum_glibc_version_key) is not None:
        target["minimumGlibcVersion"] = required_value(raw_target, minimum_glibc_version_key)
    if raw_target.get(compatibility_smoke_container_image_key) is not None:
        target["compatibilitySmokeContainerImage"] = required_value(
            raw_target, compatibility_smoke_container_image_key
        )
    recomposed_classifier = target["operatingSystemId"] + "-" + target["architectureId"]
    if normalized_classifier != recomposed_classifier:
        raise ValueError(
            f"bundle layout target {normalized_classifier} must agree with {recomposed_classifier}"
        )
    _validate_linux_compatibility_fields(target, normalized_classifier)
    return target


def _validate_linux_compatibility_fields(
    target: dict[str, str], normalized_classifier: str
) -> None:
    if target["operatingSystemId"] == "linux" and "minimumGlibcVersion" not in target:
        raise ValueError(
            f"bundle layout target {normalized_classifier} must declare minimumGlibcVersion"
        )
    if target["operatingSystemId"] != "linux" and "minimumGlibcVersion" in target:
        raise ValueError(
            f"bundle layout target {normalized_classifier} must omit minimumGlibcVersion outside Linux"
        )
    if target["operatingSystemId"] == "linux" and "compatibilitySmokeContainerImage" not in target:
        raise ValueError(
            f"bundle layout target {normalized_classifier} must declare compatibilitySmokeContainerImage"
        )
    if target["operatingSystemId"] != "linux" and "compatibilitySmokeContainerImage" in target:
        raise ValueError(
            f"bundle layout target {normalized_classifier} must omit compatibilitySmokeContainerImage outside Linux"
        )


def _merge_bundle_publication_target(
    raw_publication: object,
    *,
    target: dict[str, str] | None,
    normalized_classifier: str,
    publication_status_key: str,
    runner_label_key: str,
) -> None:
    if target is None:
        raise ValueError(
            f"bundle publication contract declared unknown target: {normalized_classifier}"
        )
    if not isinstance(raw_publication, dict):
        raise ValueError(f"bundle publication target {normalized_classifier} must be one object")
    publication_status = required_value(raw_publication, publication_status_key)
    if publication_status not in {"published", "not-published"}:
        raise ValueError(
            f"bundle publication target {normalized_classifier} declared unsupported publication status: {publication_status}"
        )
    target["publicationStatus"] = publication_status
    if raw_publication.get(runner_label_key) is not None:
        target["runnerLabel"] = required_value(raw_publication, runner_label_key)
    if publication_status == "published":
        for required_key in ("runnerLabel",):
            if required_key not in target:
                raise ValueError(
                    f"published bundle target {normalized_classifier} must declare {required_key}"
                )
        return
    for forbidden_key in ("runnerLabel",):
        if forbidden_key in target:
            raise ValueError(
                f"non-published bundle target {normalized_classifier} must omit {forbidden_key}"
            )
