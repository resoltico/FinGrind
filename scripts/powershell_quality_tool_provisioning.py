"""Download and provision the exact PowerShell quality-tool set."""

from __future__ import annotations

import tempfile
import urllib.request
from pathlib import Path
from urllib.parse import urljoin, urlsplit

from powershell_quality_tool_archives import extract_module_archive, validate_module_tree
from powershell_quality_tool_cache import acquire_verified_archive, publish_staged_tree
from powershell_quality_tool_filesystem import (
    copy_stream,
    prepare_directory,
    prepare_install_root,
    remove_safe_tree,
)
from powershell_quality_tool_metadata import (
    artifact_download_url,
    validate_artifact_identity,
    validate_metadata_identity,
)
from powershell_quality_tool_models import (
    MAX_ARCHIVE_BYTES,
    Downloader,
    ProvisioningError,
    QualityToolArtifact,
    QualityToolsInstallation,
    QualityToolsMetadata,
)

_GALLERY_DELIVERY_HOST = "cdn.powershellgallery.com"


class PowerShellGalleryRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Allow only the Gallery's canonical HTTPS package delivery redirect."""

    def __init__(self, artifact: QualityToolArtifact) -> None:
        super().__init__()
        self._artifact = artifact

    def redirect_request(
        self,
        request: urllib.request.Request,
        file_pointer: object,
        code: int,
        message: str,
        headers: object,
        new_url: str,
    ) -> urllib.request.Request | None:
        resolved_url = urljoin(request.full_url, new_url)
        validate_gallery_delivery_url(resolved_url, self._artifact)
        return super().redirect_request(
            request,
            file_pointer,
            code,
            message,
            headers,
            resolved_url,
        )


def provision_quality_tools(
    metadata: QualityToolsMetadata,
    install_root: Path,
    *,
    downloader: Downloader | None = None,
) -> QualityToolsInstallation:
    """Rebuild exact module trees from SHA-256-verified archive snapshots."""

    validate_metadata_identity(metadata)
    prepared_root = prepare_install_root(install_root)
    work_directory = Path(
        tempfile.mkdtemp(prefix=".fingrind-powershell-quality-tools-", dir=prepared_root)
    )
    try:
        (work_directory / "archive").mkdir(mode=0o700)
        staged_modules_directory = work_directory / "modules"
        staged_modules_directory.mkdir(mode=0o700)
        archive_cache_directory = prepare_directory(
            prepared_root / "archive-cache", "archive cache"
        )
        installation_paths: dict[str, tuple[Path, str]] = {}
        selected_downloader = downloader or download_artifact
        for artifact in metadata.artifacts:
            private_archive = acquire_verified_archive(
                artifact,
                archive_cache_directory,
                work_directory,
                selected_downloader,
            )
            staged_module = staged_modules_directory / artifact.module_name / artifact.version
            extract_module_archive(private_archive, artifact, staged_module)
            manifest_path = validate_module_tree(staged_module, artifact)
            target_parent = prepare_directory(
                prepared_root / "modules" / artifact.module_name,
                f"{artifact.module_name} module parent",
            )
            target_module = target_parent / artifact.version
            publish_staged_tree(staged_module, target_module, artifact.module_name)
            installation_paths[artifact.module_name] = (
                target_module / manifest_path.name,
                artifact.version,
            )
        pester_manifest, pester_version = installation_paths["Pester"]
        analyzer_manifest, analyzer_version = installation_paths["PSScriptAnalyzer"]
        return QualityToolsInstallation(
            pester_manifest=pester_manifest,
            pester_version=pester_version,
            script_analyzer_manifest=analyzer_manifest,
            script_analyzer_version=analyzer_version,
        )
    finally:
        remove_safe_tree(work_directory, "PowerShell quality-tool staging directory")


def download_artifact(artifact: QualityToolArtifact, destination: Path) -> None:
    """Download one exact Gallery package without shelling out or accepting a mutable URL."""

    validate_artifact_identity(artifact)
    if destination.name != artifact.archive_name:
        raise ProvisioningError(
            "refusing unexpected PowerShell quality-tool archive destination: "
            f"expected {artifact.archive_name!r}, received {destination.name!r}"
        )
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(
        artifact_download_url(artifact),
        headers={"User-Agent": "FinGrind-PowerShell-Quality-Tools-Provisioner"},
    )
    opener = urllib.request.build_opener(PowerShellGalleryRedirectHandler(artifact))
    try:
        with opener.open(request, timeout=30) as response, destination.open("xb") as output:
            validate_gallery_response_url(response.geturl(), artifact)
            copy_stream(response, output, maximum_bytes=MAX_ARCHIVE_BYTES)
    except OSError as error:
        raise ProvisioningError(
            f"could not download PowerShell quality-tool archive: {error}"
        ) from error


def validate_gallery_delivery_url(url: str, artifact: QualityToolArtifact) -> None:
    """Require the sole canonical PowerShell Gallery CDN URL for the module package."""

    parsed = urlsplit(url)
    expected_path = f"/packages/{artifact.module_name.lower()}.{artifact.version}.nupkg"
    if (
        parsed.scheme != "https"
        or parsed.netloc != _GALLERY_DELIVERY_HOST
        or parsed.path != expected_path
        or parsed.query
        or parsed.fragment
    ):
        raise ProvisioningError(
            "PowerShell quality-tool redirect did not resolve to the canonical HTTPS Gallery package delivery URL"
        )


def validate_gallery_response_url(url: str, artifact: QualityToolArtifact) -> None:
    """Admit the immutable Gallery API endpoint or its one canonical CDN redirect."""

    if url == artifact_download_url(artifact):
        return
    validate_gallery_delivery_url(url, artifact)
