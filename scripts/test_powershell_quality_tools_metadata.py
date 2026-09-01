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
                "6.1.0",
                "0207a75ea09f81b27c1ded44898b2bb3c845bafa02045bd64a39e26a53ca41b4",
                "https://www.powershellgallery.com/api/v2/package/Pester/6.1.0",
            ),
            "PSScriptAnalyzer": (
                "1.25.0",
                "14e634c828eb98efb9f40b2918ba90f139ed5eccdf663a2a747736d996995d60",
                "https://www.powershellgallery.com/api/v2/package/PSScriptAnalyzer/1.25.0",
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
            source + "\nfingrindPowerShellPesterVersion=6.1.0\n", encoding="utf-8"
        )
        with self.assertRaisesRegex(ProvisioningError, "duplicate canonical keys"):
            load_metadata(metadata_path)

    def test_metadata_rejects_noncanonical_versions_and_hashes(self) -> None:
        source = default_metadata_path().read_text(encoding="utf-8")
        for original, replacement, message in (
            (
                "fingrindPowerShellPesterVersion=6.1.0",
                "fingrindPowerShellPesterVersion=06.1.0",
                "version",
            ),
            (
                (
                    "fingrindPowerShellPSScriptAnalyzerSha256="
                    "14e634c828eb98efb9f40b2918ba90f139ed5eccdf663a2a747736d996995d60"
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
        canonical_delivery_url = "https://cdn.powershellgallery.com/packages/pester.6.1.0.nupkg"

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
                "https://cdn.powershellgallery.com/packages/other.6.1.0.nupkg",
            )
