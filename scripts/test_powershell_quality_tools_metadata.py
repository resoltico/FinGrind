"""Regression tests for PowerShell quality-tool metadata and Gallery admission."""

from __future__ import annotations

import io
import urllib.request
from unittest.mock import patch

import powershell_quality_tool_provisioning
from powershell_quality_tool_provisioning import PowerShellGalleryRedirectHandler
from powershell_quality_tools import (
    ProvisioningError,
    artifact_download_url,
    default_metadata_path,
    download_artifact,
    load_metadata,
)
from powershell_quality_tools_test_support import PowerShellQualityToolsTestCase


class GalleryResponse(io.BytesIO):
    """One deterministic Gallery response with an inspectable final URL."""

    def __init__(self, payload: bytes, final_url: str) -> None:
        super().__init__(payload)
        self._final_url = final_url

    def geturl(self) -> str:
        """Return the final URL selected by the mocked redirect chain."""

        return self._final_url


class GalleryOpener:
    """Minimal opener seam used to prove downloader URL admission without a network call."""

    def __init__(self, response: GalleryResponse) -> None:
        self._response = response

    def open(self, _request: object, *, timeout: int) -> GalleryResponse:
        """Return the fixed response after asserting the bounded downloader timeout."""

        if timeout != 30:
            raise AssertionError(f"unexpected downloader timeout: {timeout}")
        return self._response


class PowerShellQualityToolsMetadataTest(PowerShellQualityToolsTestCase):
    """Prove metadata permits only the expected pinned Gallery package identities."""

    def test_default_metadata_names_versions_hashes_and_urls_are_exact(self) -> None:
        metadata = self.canonical_metadata()
        expected = {
            "Pester": (
                "5.7.1",
                "4a27904c6814a5fbe4758f8e49861f6a1994aee77b71165a5c43c0371ba6c580",
                "https://www.powershellgallery.com/api/v2/package/Pester/5.7.1",
            ),
            "PSScriptAnalyzer": (
                "1.24.0",
                "e86c97d44bb1bc8a1de35e753b85ea1d938f6f9f881639a181507e079bca4556",
                "https://www.powershellgallery.com/api/v2/package/PSScriptAnalyzer/1.24.0",
            ),
        }
        self.assertEqual([artifact.module_name for artifact in metadata.artifacts], list(expected))
        for artifact in metadata.artifacts:
            version, checksum, url = expected[artifact.module_name]
            self.assertEqual((artifact.version, artifact.sha256), (version, checksum))
            self.assertEqual(artifact_download_url(artifact), url)

    def test_metadata_rejects_duplicate_canonical_keys(self) -> None:
        source = default_metadata_path().read_text(encoding="utf-8")
        metadata_path = self.root / "duplicate.properties"
        metadata_path.write_text(
            source + "\nfingrindPowerShellPesterVersion=5.7.1\n", encoding="utf-8"
        )
        with self.assertRaisesRegex(ProvisioningError, "duplicate canonical keys"):
            load_metadata(metadata_path)

    def test_metadata_rejects_noncanonical_versions_and_hashes(self) -> None:
        source = default_metadata_path().read_text(encoding="utf-8")
        for original, replacement, message in (
            (
                "fingrindPowerShellPesterVersion=5.7.1",
                "fingrindPowerShellPesterVersion=05.7.1",
                "version",
            ),
            (
                (
                    "fingrindPowerShellPSScriptAnalyzerSha256="
                    "e86c97d44bb1bc8a1de35e753b85ea1d938f6f9f881639a181507e079bca4556"
                ),
                "fingrindPowerShellPSScriptAnalyzerSha256=" + "A" * 64,
                "SHA-256",
            ),
        ):
            with self.subTest(message=message):
                metadata_path = self.root / f"invalid-{message}.properties"
                metadata_path.write_text(source.replace(original, replacement), encoding="utf-8")
                with self.assertRaisesRegex(ProvisioningError, message):
                    load_metadata(metadata_path)

    def test_default_downloader_requires_the_exact_artifact_destination(self) -> None:
        artifact = self.canonical_metadata().artifacts[0]
        destination = self.root / artifact.archive_name
        opener = GalleryOpener(GalleryResponse(b"archive", artifact_download_url(artifact)))
        with patch.object(
            powershell_quality_tool_provisioning.urllib.request,
            "build_opener",
            return_value=opener,
        ):
            download_artifact(artifact, destination)
        self.assertEqual(destination.read_bytes(), b"archive")
        with self.assertRaisesRegex(
            ProvisioningError,
            "unexpected PowerShell quality-tool archive destination",
        ):
            download_artifact(artifact, self.root / "different.nupkg")

    def test_redirect_handler_admits_only_the_canonical_https_delivery_url(self) -> None:
        artifact = self.canonical_metadata().artifacts[0]
        handler = PowerShellGalleryRedirectHandler(artifact)
        request = urllib.request.Request(artifact_download_url(artifact))
        canonical_delivery_url = "https://cdn.powershellgallery.com/packages/pester.5.7.1.nupkg"

        redirected = handler.redirect_request(
            request, None, 302, "Found", {}, canonical_delivery_url
        )

        self.assertIsNotNone(redirected)
        self.assertEqual(redirected.full_url, canonical_delivery_url)
        with self.assertRaisesRegex(
            ProvisioningError, "canonical HTTPS Gallery package delivery URL"
        ):
            handler.redirect_request(
                request,
                None,
                302,
                "Found",
                {},
                canonical_delivery_url.replace("https", "http", 1),
            )
        with self.assertRaisesRegex(
            ProvisioningError, "canonical HTTPS Gallery package delivery URL"
        ):
            handler.redirect_request(
                request,
                None,
                302,
                "Found",
                {},
                "https://cdn.powershellgallery.com/packages/other.5.7.1.nupkg",
            )
