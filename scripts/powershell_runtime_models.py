"""Immutable facts and value objects for the pinned PowerShell runtime."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Final

PROPERTY_VERSION: Final = "fingrindPowerShellVersion"
PROPERTY_SHA256_BY_PLATFORM: Final = {
    "linux-x64": "fingrindPowerShellLinuxX64Sha256",
    "linux-arm64": "fingrindPowerShellLinuxArm64Sha256",
    "macos-x64": "fingrindPowerShellMacosX64Sha256",
    "macos-arm64": "fingrindPowerShellMacosArm64Sha256",
    "windows-x64": "fingrindPowerShellWindowsX64Sha256",
    "windows-arm64": "fingrindPowerShellWindowsArm64Sha256",
}
ARTIFACT_DETAILS: Final = {
    "linux-x64": ("linux-x64", "x64", "tar.gz", "pwsh"),
    "linux-arm64": ("linux-arm64", "arm64", "tar.gz", "pwsh"),
    "macos-x64": ("osx-x64", "x64", "tar.gz", "pwsh"),
    "macos-arm64": ("osx-arm64", "arm64", "tar.gz", "pwsh"),
    "windows-x64": ("win-x64", "win-x64", "zip", "pwsh.exe"),
    "windows-arm64": ("win-arm64", "win-arm64", "zip", "pwsh.exe"),
}
HOST_PLATFORM_IDS: Final = {
    ("linux", "x86_64"): "linux-x64",
    ("linux", "amd64"): "linux-x64",
    ("linux", "aarch64"): "linux-arm64",
    ("linux", "arm64"): "linux-arm64",
    ("darwin", "x86_64"): "macos-x64",
    ("darwin", "amd64"): "macos-x64",
    ("darwin", "aarch64"): "macos-arm64",
    ("darwin", "arm64"): "macos-arm64",
    ("windows", "amd64"): "windows-x64",
    ("windows", "x86_64"): "windows-x64",
    ("windows", "arm64"): "windows-arm64",
    ("windows", "aarch64"): "windows-arm64",
}
MAX_ARCHIVE_BYTES: Final = 512 * 1024 * 1024
MAX_EXTRACTED_BYTES: Final = 2 * 1024 * 1024 * 1024
MAX_ARCHIVE_MEMBERS: Final = 100_000


class ProvisioningError(RuntimeError):
    """Raised when a PowerShell runtime cannot be securely provisioned."""


@dataclass(frozen=True)
class PowerShellMetadata:
    """The canonical version and checksum set read from build metadata."""

    version: str
    sha256_by_platform: dict[str, str]


@dataclass(frozen=True)
class PowerShellArtifact:
    """One immutable PowerShell release artifact selected for the current host."""

    platform_id: str
    archive_name: str
    archive_kind: str
    executable_name: str
    sha256: str


Downloader = Callable[[str, Path], None]
