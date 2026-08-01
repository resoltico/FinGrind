"""Immutable facts and value objects for the pinned PowerShell quality tools."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Final

MAX_ARCHIVE_BYTES: Final = 64 * 1024 * 1024
MAX_ARCHIVE_MEMBERS: Final = 10_000
MAX_EXTRACTED_BYTES: Final = 512 * 1024 * 1024
QUALITY_TOOL_SPECS: Final = (
    ("Pester", "Pester.psd1", "Pester.psm1"),
    ("PSScriptAnalyzer", "PSScriptAnalyzer.psd1", "PSScriptAnalyzer.psm1"),
)


class ProvisioningError(RuntimeError):
    """Raised when a pinned PowerShell quality tool cannot be safely provisioned."""


@dataclass(frozen=True)
class QualityToolArtifact:
    """One fixed PowerShell Gallery module archive and its checked metadata."""

    module_name: str
    version: str
    sha256: str
    manifest_name: str
    root_module_name: str

    @property
    def archive_name(self) -> str:
        """Return the only permitted archive-cache filename for this artifact."""

        return f"{self.module_name}-{self.version}.nupkg"


@dataclass(frozen=True)
class QualityToolsMetadata:
    """The complete immutable module set required by the PowerShell quality gate."""

    artifacts: tuple[QualityToolArtifact, ...]


@dataclass(frozen=True)
class QualityToolsInstallation:
    """Exact manifest locations imported by the host-independent quality runner."""

    pester_manifest: Path
    pester_version: str
    script_analyzer_manifest: Path
    script_analyzer_version: str

    def as_json_object(self) -> dict[str, dict[str, str]]:
        """Return stable, machine-readable exact module locations and versions."""

        return {
            "pester": {
                "manifest": str(self.pester_manifest),
                "version": self.pester_version,
            },
            "psScriptAnalyzer": {
                "manifest": str(self.script_analyzer_manifest),
                "version": self.script_analyzer_version,
            },
        }


Downloader = Callable[[QualityToolArtifact, Path], None]
