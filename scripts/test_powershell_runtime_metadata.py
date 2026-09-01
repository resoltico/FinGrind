"""Regression coverage for pinned PowerShell metadata and immutable URLs."""

from __future__ import annotations

import io
from dataclasses import replace
from unittest.mock import patch

import powershell_runtime_download
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
                "powershell-7.6.5-linux-x64.tar.gz",
                "b34ab3b19acac1d3d4d0d3cfdb02acf62f457b0b6a962ff008132033f7566844",
            ),
            ("Linux", "aarch64"): (
                "powershell-7.6.5-linux-arm64.tar.gz",
                "ed4084f215d8bce2edd23aa7cb1f1e7b0818e41363a635a22065d2701b6141df",
            ),
            ("Darwin", "x86_64"): (
                "powershell-7.6.5-osx-x64.tar.gz",
                "3db1d177ab39511c1b6b73b05a1630a5db4e8dce22857ca76f14c5d98f2733fd",
            ),
            ("Darwin", "arm64"): (
                "powershell-7.6.5-osx-arm64.tar.gz",
                "8196d4b4e7c21b7f6df9d45687bb4e42dc8335f330b580d9eb15f3ef5042a8c3",
            ),
            ("Windows", "AMD64"): (
                "PowerShell-7.6.5-win-x64.zip",
                "32eb8f6cdce08f86e987d625a2733e54ac3e289ae7e1621b14c0b5bcec2434ea",
            ),
            ("Windows", "ARM64"): (
                "PowerShell-7.6.5-win-arm64.zip",
                "20514a755d16428dc4355c85e0883c859531e71cc3e122670aa1fccdbf96ba7e",
            ),
        }
        self.assertEqual(metadata.version, "7.6.5")
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
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.5/"
                "powershell-7.6.5-linux-x64.tar.gz"
            ),
            ("Linux", "aarch64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.5/"
                "powershell-7.6.5-linux-arm64.tar.gz"
            ),
            ("Darwin", "x86_64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.5/"
                "powershell-7.6.5-osx-x64.tar.gz"
            ),
            ("Darwin", "arm64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.5/"
                "powershell-7.6.5-osx-arm64.tar.gz"
            ),
            ("Windows", "AMD64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.5/"
                "PowerShell-7.6.5-win-x64.zip"
            ),
            ("Windows", "ARM64"): (
                "https://github.com/PowerShell/PowerShell/releases/download/v7.6.5/"
                "PowerShell-7.6.5-win-arm64.zip"
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
        for version in ("07.6.5", "7.\u0666.4"):
            with self.subTest(version=version):
                metadata_path = self.root / f"metadata-{version.encode().hex()}.properties"
                metadata_path.write_text(
                    source.replace(
                        "fingrindPowerShellVersion=7.6.5", f"fingrindPowerShellVersion={version}"
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
            powershell_runtime_download.urllib.request,
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
        metadata = self.metadata_for(self.write_tar("pwsh", self.fake_powershell("7.6.5")))
        artifact = select_artifact(metadata, operating_system="Linux", architecture="x86_64")
        malformed = replace(artifact, archive_name="unexpected.tar.gz")
        with self.assertRaisesRegex(ProvisioningError, "does not match build metadata"):
            artifact_download_url(metadata, malformed)
