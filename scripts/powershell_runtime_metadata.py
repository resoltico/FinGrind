"""Read, validate, and identify the one allowed PowerShell release artifact."""

from __future__ import annotations

import platform
from pathlib import Path
from urllib.parse import urlsplit

from powershell_runtime_models import (
    ARTIFACT_DETAILS,
    HOST_PLATFORM_IDS,
    PROPERTY_SHA256_BY_PLATFORM,
    PROPERTY_VERSION,
    PowerShellArtifact,
    PowerShellMetadata,
    ProvisioningError,
)


def default_metadata_path() -> Path:
    """Return the repository's canonical build metadata location."""

    return Path(__file__).resolve().parent.parent / "gradle" / "fingrind-build.properties"


def load_metadata(metadata_path: Path) -> PowerShellMetadata:
    """Load and strictly validate PowerShell metadata from the build-property owner."""

    if not metadata_path.is_file():
        raise ProvisioningError(f"missing PowerShell metadata at {metadata_path}")

    values: dict[str, str] = {}
    duplicate_keys: set[str] = set()
    for raw_line in metadata_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            continue
        key = key.strip()
        value = value.strip()
        if key in values:
            duplicate_keys.add(key)
        values[key] = value

    required_keys = {PROPERTY_VERSION, *PROPERTY_SHA256_BY_PLATFORM.values()}
    duplicates = sorted(required_keys.intersection(duplicate_keys))
    if duplicates:
        raise ProvisioningError(
            "PowerShell metadata declares duplicate canonical keys: " + ", ".join(duplicates)
        )
    missing = sorted(key for key in required_keys if not values.get(key))
    if missing:
        raise ProvisioningError(
            "PowerShell metadata is missing required canonical keys: " + ", ".join(missing)
        )

    version = values[PROPERTY_VERSION]
    if not _is_release_version(version):
        raise ProvisioningError(
            f"PowerShell metadata has an invalid immutable release version: {version!r}"
        )

    sha256_by_platform: dict[str, str] = {}
    for platform_id, property_name in PROPERTY_SHA256_BY_PLATFORM.items():
        checksum = values[property_name]
        if not _is_sha256(checksum):
            raise ProvisioningError(
                f"PowerShell metadata has an invalid SHA-256 for {platform_id}: {checksum!r}"
            )
        sha256_by_platform[platform_id] = checksum
    return PowerShellMetadata(version=version, sha256_by_platform=sha256_by_platform)


def select_artifact(
    metadata: PowerShellMetadata,
    *,
    operating_system: str | None = None,
    architecture: str | None = None,
) -> PowerShellArtifact:
    """Select the only supported immutable artifact for the supplied host identity."""

    observed_system = operating_system or platform.system()
    observed_architecture = architecture or platform.machine()
    platform_id = HOST_PLATFORM_IDS.get(
        (observed_system.casefold(), observed_architecture.casefold())
    )
    if platform_id is None:
        raise ProvisioningError(
            "unsupported PowerShell provisioning host: "
            f"operating system {observed_system!r}, architecture {observed_architecture!r}"
        )

    artifact_platform, _asset_architecture, archive_kind, executable_name = ARTIFACT_DETAILS[
        platform_id
    ]
    archive_prefix = "PowerShell" if platform_id.startswith("windows-") else "powershell"
    archive_name = f"{archive_prefix}-{metadata.version}-{artifact_platform}.{archive_kind}"
    return PowerShellArtifact(
        platform_id=platform_id,
        archive_name=archive_name,
        archive_kind=archive_kind,
        executable_name=executable_name,
        sha256=metadata.sha256_by_platform[platform_id],
    )


def artifact_download_url(metadata: PowerShellMetadata, artifact: PowerShellArtifact) -> str:
    """Return the versioned GitHub release URL after validating the selected artifact identity."""

    _validate_artifact_identity(metadata, artifact)
    url = (
        "https://github.com/PowerShell/PowerShell/releases/download/"
        f"v{metadata.version}/{artifact.archive_name}"
    )
    parsed = urlsplit(url)
    expected_path = (
        f"/PowerShell/PowerShell/releases/download/v{metadata.version}/{artifact.archive_name}"
    )
    if parsed.scheme != "https" or parsed.netloc != "github.com" or parsed.path != expected_path:
        raise ProvisioningError(
            "PowerShell release URL did not resolve to the immutable GitHub asset"
        )
    return url


def immutable_archive_name_from_url(url: str) -> str:
    """Validate an immutable GitHub release URL and return its allowed archive name."""

    parsed = urlsplit(url)
    path_parts = [part for part in parsed.path.split("/") if part]
    if (
        parsed.scheme != "https"
        or parsed.netloc != "github.com"
        or parsed.query
        or parsed.fragment
        or len(path_parts) != 6
        or path_parts[:4] != ["PowerShell", "PowerShell", "releases", "download"]
        or not path_parts[4].startswith("v")
    ):
        raise ProvisioningError("refusing a non-immutable PowerShell release URL")
    version = path_parts[4][1:]
    if not _is_release_version(version):
        raise ProvisioningError(f"PowerShell release URL has an invalid version: {version!r}")
    expected_names = {
        f"{'PowerShell' if platform_id.startswith('windows-') else 'powershell'}-"
        f"{version}-{artifact_platform}.{archive_kind}"
        for platform_id, (
            artifact_platform,
            _asset_architecture,
            archive_kind,
            _executable_name,
        ) in ARTIFACT_DETAILS.items()
    }
    archive_name = path_parts[5]
    if archive_name not in expected_names:
        raise ProvisioningError(
            f"refusing an unexpected PowerShell release archive: {archive_name!r}"
        )
    return archive_name


def _is_release_version(value: str) -> bool:
    parts = value.split(".")
    return len(parts) == 3 and all(
        part and part.isascii() and part.isdecimal() and (part == "0" or not part.startswith("0"))
        for part in parts
    )


def _is_sha256(value: str) -> bool:
    return len(value) == 64 and all(character in "0123456789abcdef" for character in value)


def _validate_artifact_identity(metadata: PowerShellMetadata, artifact: PowerShellArtifact) -> None:
    if artifact.platform_id not in ARTIFACT_DETAILS:
        raise ProvisioningError(
            f"unsupported PowerShell artifact platform: {artifact.platform_id!r}"
        )
    artifact_platform, asset_architecture, archive_kind, executable_name = ARTIFACT_DETAILS[
        artifact.platform_id
    ]
    archive_prefix = "PowerShell" if artifact.platform_id.startswith("windows-") else "powershell"
    expected_archive_name = (
        f"{archive_prefix}-{metadata.version}-{artifact_platform}.{archive_kind}"
    )
    if (
        artifact.archive_name != expected_archive_name
        or artifact.archive_kind != archive_kind
        or artifact.executable_name != executable_name
        or artifact.sha256 != metadata.sha256_by_platform.get(artifact.platform_id)
    ):
        raise ProvisioningError(
            f"PowerShell artifact identity for {artifact.platform_id} does not match build metadata"
        )
    if asset_architecture not in artifact.archive_name:
        raise ProvisioningError(
            f"unexpected PowerShell artifact architecture: {artifact.archive_name!r}"
        )
