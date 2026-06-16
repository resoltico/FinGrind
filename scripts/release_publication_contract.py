from __future__ import annotations

from pathlib import Path

from contract_values import load_contract_values


def load_release_publication_contract(repo_root: Path) -> dict[str, object]:
    contract_values = load_contract_values(repo_root)
    release_publication = contract_values["releasePublication"]
    return {
        "publicBundleBuildTargets": release_publication["publicBundleBuildTargets"],
        "requiredCiWorkflowName": release_publication["requiredCiWorkflowName"],
        "requiredCiWorkflowPath": release_publication["requiredCiWorkflowPath"],
        "requiredCiGateJobName": release_publication["requiredCiGateJobName"],
        "requiredCiJobNames": release_publication["requiredCiJobNames"],
        "containerRegistry": release_publication["containerRegistry"],
        "containerImageName": release_publication["containerImageName"],
        "containerStagingImageName": release_publication["containerStagingImageName"],
        "containerRunnerLabel": release_publication["containerRunnerLabel"],
        "containerPlatforms": release_publication["containerPlatforms"],
        "latestPublicationPolicy": release_publication["latestPublicationPolicy"],
    }


def load_release_publication_plan(
    repo_root: Path,
    *,
    version: str,
    repository_owner: str | None = None,
) -> dict[str, object]:
    contract_values = load_contract_values(repo_root)
    bundle_targets = contract_values["bundleLayout"]["targets"]
    public_distribution = contract_values["publicDistribution"]
    release_publication = load_release_publication_contract(repo_root)

    supported_targets = public_distribution["supportedPublicCliBundleTargets"]
    bundle_matrix: list[dict[str, str]] = []
    published_unix_matrix: list[dict[str, str]] = []
    container_matrix: list[dict[str, str]] = []
    release_asset_names: list[str] = []

    for classifier in supported_targets:
        bundle_target = bundle_targets[classifier]
        release_target = release_publication["publicBundleBuildTargets"][classifier]
        archive_extension = bundle_target["archiveFormat"]
        matrix_entry = {
            "runner": release_target["runnerLabel"],
            "classifier": classifier,
            "archiveExtension": archive_extension,
            "operatingSystemId": bundle_target["operatingSystemId"],
            "architectureId": bundle_target["architectureId"],
        }
        bundle_matrix.append(matrix_entry)
        if bundle_target["operatingSystemId"] != "windows":
            published_unix_matrix.append(matrix_entry)
        if bundle_target["operatingSystemId"] == "linux":
            docker_platform = {
                "x86_64": "linux/amd64",
                "aarch64": "linux/arm64",
            }[bundle_target["architectureId"]]
            container_matrix.append(
                {
                    "runner": release_target["runnerLabel"],
                    "classifier": classifier,
                    "dockerPlatform": docker_platform,
                    "operatingSystemId": bundle_target["operatingSystemId"],
                    "architectureId": bundle_target["architectureId"],
                }
            )
        archive_name = f"fingrind-{version}-{classifier}.{archive_extension}"
        release_asset_names.append(archive_name)
        release_asset_names.append(f"{archive_name}.sha256")

    container_image_ref = None
    container_staging_image_ref = None
    if repository_owner:
        container_image_ref = (
            f"{release_publication['containerRegistry']}/{repository_owner}/"
            f"{release_publication['containerImageName']}"
        )
        container_staging_image_ref = (
            f"{release_publication['containerRegistry']}/{repository_owner}/"
            f"{release_publication['containerStagingImageName']}"
        )

    attestation_subject_paths = [
        f"release-assets/{asset_name}" for asset_name in release_asset_names
    ]
    return {
        "bundleBuildMatrix": bundle_matrix,
        "containerBuildMatrix": container_matrix,
        "publishedUnixBundleSmokeMatrix": published_unix_matrix,
        "releaseAssetNames": release_asset_names,
        "releaseAttestationSubjectPaths": attestation_subject_paths,
        "requiredCiWorkflowName": release_publication["requiredCiWorkflowName"],
        "requiredCiWorkflowPath": release_publication["requiredCiWorkflowPath"],
        "requiredCiGateJobName": release_publication["requiredCiGateJobName"],
        "requiredCiJobNames": release_publication["requiredCiJobNames"],
        "containerRunnerLabel": release_publication["containerRunnerLabel"],
        "containerPlatforms": release_publication["containerPlatforms"],
        "containerImageRef": container_image_ref,
        "containerStagingImageRef": container_staging_image_ref,
        "latestPublicationPolicy": release_publication["latestPublicationPolicy"],
    }
