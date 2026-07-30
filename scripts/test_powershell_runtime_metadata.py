"""Regression coverage for pinned PowerShell metadata and immutable URLs."""

from __future__ import annotations

import io
from dataclasses import replace
from unittest.mock import patch

import powershell_runtime_installation
from powershell_runtime import (
    ProvisioningError,
    artifact_download_url,
    default_metadata_path,
    download_artifact,
    load_metadata,
    select_artifact,
)
from test_powershell_runtime_support import PowerShellRuntimeTestCase


class PowerShellRuntimeMetadataTest(PowerShellRuntimeTestCase):
    """Exercise supported artifact identities and immutable metadata boundaries."""

    def test_metadata_selects_every_supported_pinned_artifact(self) -> None:
        metadata = load_metadata(default_metadata_path())
        expected = {
            ("Linux", "x86_64"): (
                "powershell-7.6.4-linux-x64.tar.gz",
                "4471b5a36bfe86ec7af8525d36bb1cacba0128e7aac22d05cc064bc00e604721",
            ),
            ("Linux", "aarch64"): (
                "powershell-7.6.4-linux-arm64.tar.gz",
                "d4ef2382fa452f2ccbdb48a01adbbce9ed64954872123970c16be6d086d1224b",
            ),
            ("Darwin", "x86_64"): (
                "powershell-7.6.4-osx-x64.tar.gz",
                "b58e4b96dbdca20c058d4462f33509d386c0d768751344611bc04aaf32e4187c",
            ),
            ("Darwin", "arm64"): (
                "powershell-7.6.4-osx-arm64.tar.gz",
                "fff37135307d3a57038adb44eded6c3b4dcd2e254382f4913bc253499ef3469d",
            ),
            ("Windows", "AMD64"): (
                "PowerShell-7.6.4-win-x64.zip",
                "80832551c52809301e6071c8bac977beb5a2f1ec953eb4db9f94deb953333793",
            ),
            ("Windows", "ARM64"): (
                "PowerShell-7.6.4-win-arm64.zip",
                "774e541334ae2b2b9f14b96a0808e8905f19a103aefc790ec5d5be2a63ae9314",
            ),
        }
        self.assertEqual(metadata.version, "7.6.4")
        for host, expected_artifact in expected.items():
            artifact = select_artifact(
                metadata,
                operating_system=host[0],
                architecture=host[1],
            )
            self.assertEqual((artifact.archive_name, artifact.sha256), expected_artifact)

    def test_metadata_generates_the_exact_immutable_download_urls(self) -> None:
        metadata = load_metadata(default_metadata_path())
        expected_urls = {
            ("Linux", "x86_64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.4/"
                "powershell-7.6.4-linux-x64.tar.gz"
            ),
            ("Linux", "aarch64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.4/"
                "powershell-7.6.4-linux-arm64.tar.gz"
            ),
            ("Darwin", "x86_64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.4/"
                "powershell-7.6.4-osx-x64.tar.gz"
            ),
            ("Darwin", "arm64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.4/"
                "powershell-7.6.4-osx-arm64.tar.gz"
            ),
            ("Windows", "AMD64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.4/"
                "PowerShell-7.6.4-win-x64.zip"
            ),
            ("Windows", "ARM64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.4/"
                "PowerShell-7.6.4-win-arm64.zip"
            ),
        }
        for host, expected_url in expected_urls.items():
            artifact = select_artifact(
                metadata,
                operating_system=host[0],
                architecture=host[1],
            )
            self.assertEqual(artifact_download_url(metadata, artifact), expected_url)

    def test_metadata_rejects_ambiguous_release_version_identifiers(self) -> None:
        source = default_metadata_path().read_text(encoding="utf-8")
        for version in ("07.6.4", "7.\u0666.4"):
            with self.subTest(version=version):
                metadata_path = self.root / f"metadata-{version.encode().hex()}.properties"
                metadata_path.write_text(
                    source.replace(
                        "fingrindPowerShellVersion=7.6.4", f"fingrindPowerShellVersion={version}"
                    ),
                    encoding="utf-8",
                )
                with self.assertRaisesRegex(ProvisioningError, "invalid immutable release version"):
                    load_metadata(metadata_path)

    def test_default_downloader_accepts_only_the_exact_url_basename(self) -> None:
        metadata = load_metadata(default_metadata_path())
        artifact = select_artifact(metadata, operating_system="Linux", architecture="x86_64")
        destination = self.root / artifact.archive_name
        with patch.object(
            powershell_runtime_installation.urllib.request,
            "urlopen",
            return_value=io.BytesIO(b"archive"),
        ):
            download_artifact(artifact_download_url(metadata, artifact), destination)
        self.assertEqual(destination.read_bytes(), b"archive")
        with self.assertRaisesRegex(ProvisioningError, "unexpected PowerShell archive destination"):
            download_artifact(
                artifact_download_url(metadata, artifact),
                self.root / "different.tar.gz",
            )

    def test_rejects_unexpected_artifact_identity(self) -> None:
        metadata = self.metadata_for(self.write_tar("pwsh", self.fake_powershell("7.6.4")))
        artifact = select_artifact(metadata, operating_system="Linux", architecture="x86_64")
        malformed = replace(artifact, archive_name="unexpected.tar.gz")
        with self.assertRaisesRegex(ProvisioningError, "does not match build metadata"):
            artifact_download_url(metadata, malformed)
