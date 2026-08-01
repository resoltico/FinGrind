"""Read and validate the fixed PowerShell Gallery quality-tool identities."""

from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import urlsplit

from powershell_quality_tool_models import (
    QUALITY_TOOL_SPECS,
    ProvisioningError,
    QualityToolArtifact,
    QualityToolsMetadata,
)

_PROPERTY_VERSION_SUFFIX = "Version"
_PROPERTY_SHA256_SUFFIX = "Sha256"
_MODULE_NAME_PATTERN = re.compile(r"^[A-Za-z][A-Za-z0-9]*$")
_VERSION_PATTERN = re.compile(r"^(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)$")
_SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
_GALLERY_API_HOST = "www.powershellgallery.com"


def default_metadata_path() -> Path:
    """Return the repository-owned metadata file for the exact quality-tool set."""

    return Path(__file__).resolve().with_name("powershell-quality-tools.properties")


def load_metadata(metadata_path: Path) -> QualityToolsMetadata:
    """Load exactly the fixed Pester and PSScriptAnalyzer module identities."""

    if not metadata_path.is_file():
        raise ProvisioningError(f"missing PowerShell quality-tool metadata at {metadata_path}")
    values, duplicate_keys = _read_properties(metadata_path)
    required_keys = {
        property_name
        for module_name, _manifest_name, _root_module_name in QUALITY_TOOL_SPECS
        for property_name in (
            _property_name(module_name, _PROPERTY_VERSION_SUFFIX),
            _property_name(module_name, _PROPERTY_SHA256_SUFFIX),
        )
    }
    duplicates = sorted(required_keys.intersection(duplicate_keys))
    if duplicates:
        raise ProvisioningError(
            "PowerShell quality-tool metadata declares duplicate canonical keys: "
            + ", ".join(duplicates)
        )
    missing = sorted(key for key in required_keys if not values.get(key))
    if missing:
        raise ProvisioningError(
            "PowerShell quality-tool metadata is missing required canonical keys: "
            + ", ".join(missing)
        )
    return QualityToolsMetadata(
        artifacts=tuple(
            _artifact_from_properties(values, module_name, manifest_name, root_module_name)
            for module_name, manifest_name, root_module_name in QUALITY_TOOL_SPECS
        )
    )


def artifact_download_url(artifact: QualityToolArtifact) -> str:
    """Return the sole versioned PowerShell Gallery package URL for one known module."""

    validate_artifact_identity(artifact)
    url = f"https://{_GALLERY_API_HOST}/api/v2/package/{artifact.module_name}/{artifact.version}"
    parsed = urlsplit(url)
    expected_path = f"/api/v2/package/{artifact.module_name}/{artifact.version}"
    if (
        parsed.scheme != "https"
        or parsed.netloc != _GALLERY_API_HOST
        or parsed.path != expected_path
        or parsed.query
        or parsed.fragment
    ):
        raise ProvisioningError(
            "PowerShell quality-tool URL did not resolve to the immutable Gallery package"
        )
    return url


def validate_metadata_identity(metadata: QualityToolsMetadata) -> None:
    """Require exactly the supported quality-tool identities in canonical order."""

    expected_names = [module_name for module_name, _manifest, _root in QUALITY_TOOL_SPECS]
    actual_names = [artifact.module_name for artifact in metadata.artifacts]
    if actual_names != expected_names:
        raise ProvisioningError(
            "PowerShell quality-tool metadata must declare exactly the canonical module set: "
            + ", ".join(expected_names)
        )
    for artifact in metadata.artifacts:
        validate_artifact_identity(artifact)


def validate_artifact_identity(artifact: QualityToolArtifact) -> None:
    """Require one artifact's name, pinned release, digest, and manifest contract."""

    if not _MODULE_NAME_PATTERN.fullmatch(artifact.module_name):
        raise ProvisioningError(
            f"invalid PowerShell quality-tool module name: {artifact.module_name!r}"
        )
    expected = next(
        (
            (manifest_name, root_module_name)
            for module_name, manifest_name, root_module_name in QUALITY_TOOL_SPECS
            if module_name == artifact.module_name
        ),
        None,
    )
    if expected is None or (
        not _VERSION_PATTERN.fullmatch(artifact.version)
        or not _SHA256_PATTERN.fullmatch(artifact.sha256)
        or artifact.manifest_name != expected[0]
        or artifact.root_module_name != expected[1]
    ):
        raise ProvisioningError(
            f"PowerShell quality-tool artifact for {artifact.module_name} does not match canonical metadata"
        )


def _read_properties(metadata_path: Path) -> tuple[dict[str, str], set[str]]:
    values: dict[str, str] = {}
    duplicate_keys: set[str] = set()
    for raw_line in metadata_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            continue
        normalized_key = key.strip()
        if normalized_key in values:
            duplicate_keys.add(normalized_key)
        values[normalized_key] = value.strip()
    return values, duplicate_keys


def _artifact_from_properties(
    values: dict[str, str],
    module_name: str,
    manifest_name: str,
    root_module_name: str,
) -> QualityToolArtifact:
    version = values[_property_name(module_name, _PROPERTY_VERSION_SUFFIX)]
    checksum = values[_property_name(module_name, _PROPERTY_SHA256_SUFFIX)]
    if not _VERSION_PATTERN.fullmatch(version):
        raise ProvisioningError(
            "PowerShell quality-tool metadata has an invalid immutable release version for "
            f"{module_name}: {version!r}"
        )
    if not _SHA256_PATTERN.fullmatch(checksum):
        raise ProvisioningError(
            "PowerShell quality-tool metadata has an invalid SHA-256 for "
            f"{module_name}: {checksum!r}"
        )
    return QualityToolArtifact(
        module_name=module_name,
        version=version,
        sha256=checksum,
        manifest_name=manifest_name,
        root_module_name=root_module_name,
    )


def _property_name(module_name: str, suffix: str) -> str:
    return f"fingrindPowerShell{module_name}{suffix}"
