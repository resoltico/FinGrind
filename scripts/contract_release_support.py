"""Helpers for validating the release-publication contract surface."""

from __future__ import annotations

from contract_value_support import required_string, required_value, string_array


def load_release_publication(
    document: dict[str, object],
    schema: dict[str, object],
    *,
    bundle_layout_targets: dict[str, dict[str, str]],
) -> dict[str, object]:
    required_ci_workflow_name_key = required_string(schema, "requiredCiWorkflowName")
    required_ci_workflow_path_key = required_string(schema, "requiredCiWorkflowPath")
    required_ci_gate_job_name_key = required_string(schema, "requiredCiGateJobName")
    required_ci_job_names_key = required_string(schema, "requiredCiJobNames")
    container_registry_key = required_string(schema, "containerRegistry")
    container_image_name_key = required_string(schema, "containerImageName")
    container_staging_image_name_key = required_string(schema, "containerStagingImageName")
    container_runner_label_key = required_string(schema, "containerRunnerLabel")
    container_platforms_key = required_string(schema, "containerPlatforms")
    latest_publication_policy_key = required_string(schema, "latestPublicationPolicy")

    release_build_targets: dict[str, dict[str, str]] = {}
    supported_bundle_targets = [
        classifier
        for classifier, target in bundle_layout_targets.items()
        if target["publicationStatus"] == "published"
    ]
    for classifier in supported_bundle_targets:
        target = bundle_layout_targets[classifier]
        release_build_targets[classifier] = {
            "runnerLabel": target["runnerLabel"],
            "expectedRunnerOs": target["expectedRunnerOs"],
            "expectedRunnerArch": target["expectedRunnerArch"],
        }

    declared_container_platforms = string_array(document, container_platforms_key)
    expected_container_platforms = _expected_container_platforms(
        supported_bundle_targets, bundle_layout_targets
    )
    if declared_container_platforms != expected_container_platforms:
        raise ValueError(
            "release publication contract containerPlatforms must match the supported Linux public bundle targets"
        )

    return {
        "publicBundleBuildTargets": release_build_targets,
        "requiredCiWorkflowName": required_value(document, required_ci_workflow_name_key),
        "requiredCiWorkflowPath": required_value(document, required_ci_workflow_path_key),
        "requiredCiGateJobName": required_value(document, required_ci_gate_job_name_key),
        "requiredCiJobNames": string_array(document, required_ci_job_names_key),
        "containerRegistry": required_value(document, container_registry_key),
        "containerImageName": required_value(document, container_image_name_key),
        "containerStagingImageName": required_value(document, container_staging_image_name_key),
        "containerRunnerLabel": required_value(document, container_runner_label_key),
        "containerPlatforms": declared_container_platforms,
        "latestPublicationPolicy": required_value(document, latest_publication_policy_key),
    }


def _expected_container_platforms(
    supported_bundle_targets: list[str],
    bundle_layout_targets: dict[str, dict[str, str]],
) -> list[str]:
    supported_linux_targets = [
        classifier
        for classifier in supported_bundle_targets
        if bundle_layout_targets[classifier]["operatingSystemId"] == "linux"
    ]
    expected_container_platforms = []
    for classifier in supported_linux_targets:
        architecture_id = bundle_layout_targets[classifier]["architectureId"]
        docker_architecture = {
            "x86_64": "amd64",
            "aarch64": "arm64",
        }.get(architecture_id)
        if docker_architecture is None:
            raise ValueError(
                f"release publication contract cannot derive Docker platform for {classifier}"
            )
        expected_container_platforms.append(f"linux/{docker_architecture}")
    return expected_container_platforms
