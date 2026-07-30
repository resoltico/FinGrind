"""Shared isolated fixtures for PowerShell quality-tool provisioning regression tests."""

from __future__ import annotations

import hashlib
import shutil
import tempfile
import unittest
import zipfile
from dataclasses import replace
from pathlib import Path

from powershell_quality_tools import (
    QualityToolArtifact,
    QualityToolsMetadata,
    default_metadata_path,
    load_metadata,
    provision_quality_tools,
)


class PowerShellQualityToolsTestCase(unittest.TestCase):
    """Build isolated module archives and run the provisioning boundary against them."""

    def setUp(self) -> None:
        repository_temp_root = Path(__file__).resolve().parent.parent / "tmp"
        self.temporary_directory = tempfile.TemporaryDirectory(dir=repository_temp_root)
        self.root = Path(self.temporary_directory.name)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def canonical_metadata(self) -> QualityToolsMetadata:
        return load_metadata(default_metadata_path())

    def archives_for(
        self,
        metadata: QualityToolsMetadata,
        *,
        override_members: dict[str, dict[str, bytes]] | None = None,
    ) -> dict[str, Path]:
        archives: dict[str, Path] = {}
        for artifact in metadata.artifacts:
            archive = self.root / f"{artifact.module_name}.nupkg"
            members = {
                artifact.manifest_name: self.module_manifest(artifact).encode("utf-8"),
                artifact.root_module_name: b"# module entrypoint\n",
                "LICENSE": b"fixture license\n",
            }
            if override_members is not None:
                members.update(override_members.get(artifact.module_name, {}))
            self.write_zip(archive, members)
            archives[artifact.module_name] = archive
        return archives

    @staticmethod
    def module_manifest(artifact: QualityToolArtifact) -> str:
        return (
            "@{\n"
            f"    RootModule = '{artifact.root_module_name}'\n"
            f"    ModuleVersion = '{artifact.version}'\n"
            "}\n"
        )

    @staticmethod
    def write_zip(archive: Path, members: dict[str, bytes]) -> None:
        with zipfile.ZipFile(archive, mode="w") as package:
            for member_name, content in members.items():
                package.writestr(member_name, content)

    @staticmethod
    def metadata_for_archives(
        metadata: QualityToolsMetadata,
        archives: dict[str, Path],
    ) -> QualityToolsMetadata:
        return QualityToolsMetadata(
            artifacts=tuple(
                replace(
                    artifact,
                    sha256=hashlib.sha256(archives[artifact.module_name].read_bytes()).hexdigest(),
                )
                for artifact in metadata.artifacts
            )
        )

    def provision(
        self,
        metadata: QualityToolsMetadata,
        archives: dict[str, Path],
        *,
        install_root: Path | None = None,
    ) -> object:
        return provision_quality_tools(
            metadata,
            install_root or self.root / "install",
            downloader=lambda artifact, destination: shutil.copyfile(
                archives[artifact.module_name], destination
            ),
        )
