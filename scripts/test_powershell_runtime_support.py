"""Shared fixture construction for PowerShell runtime provisioning regressions."""

from __future__ import annotations

import hashlib
import io
import shutil
import tarfile
import tempfile
import unittest
from pathlib import Path

from powershell_runtime import PowerShellMetadata, provision_runtime


class PowerShellRuntimeTestCase(unittest.TestCase):
    """Provide isolated archives and deterministic runtime publication helpers."""

    def setUp(self) -> None:
        repository_temp_root = Path(__file__).resolve().parent.parent / "tmp"
        self.temporary_directory = tempfile.TemporaryDirectory(dir=repository_temp_root)
        self.root = Path(self.temporary_directory.name)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def provision(
        self,
        archive: Path,
        *,
        operating_system: str = "Linux",
        architecture: str = "x86_64",
    ) -> Path:
        return self.provision_with_metadata(
            self.metadata_for(archive),
            archive,
            operating_system=operating_system,
            architecture=architecture,
        )

    def provision_with_metadata(
        self,
        metadata: PowerShellMetadata,
        archive: Path,
        *,
        operating_system: str = "Linux",
        architecture: str = "x86_64",
    ) -> Path:
        return provision_runtime(
            metadata,
            self.root / "install",
            operating_system=operating_system,
            architecture=architecture,
            downloader=lambda _url, destination: shutil.copyfile(archive, destination),
        )

    def metadata_for(self, archive: Path, *, checksum: str | None = None) -> PowerShellMetadata:
        digest = checksum or hashlib.sha256(archive.read_bytes()).hexdigest()
        return PowerShellMetadata(
            version="7.6.4",
            sha256_by_platform={
                "linux-x64": digest,
                "linux-arm64": digest,
                "macos-x64": digest,
                "macos-arm64": digest,
                "windows-x64": digest,
                "windows-arm64": digest,
            },
        )

    def write_tar(self, member_name: str, content: bytes) -> Path:
        return self.write_tar_members({member_name: content})

    def write_tar_members(self, members: dict[str, bytes]) -> Path:
        archive = self.root / "runtime.tar.gz"
        with tarfile.open(archive, mode="w:gz") as package:
            for member_name, content in members.items():
                self.add_tar_member(package, member_name, content)
        return archive

    @staticmethod
    def add_tar_member(package: tarfile.TarFile, name: str, content: bytes) -> None:
        member = tarfile.TarInfo(name)
        member.mode = 0o755
        member.size = len(content)
        package.addfile(member, io.BytesIO(content))

    @staticmethod
    def fake_powershell(version: str) -> bytes:
        return f"#!/usr/bin/env bash\nprintf '%s\\n' '{version}'\n".encode()
