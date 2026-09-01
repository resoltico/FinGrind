"""Regression coverage for transient PowerShell release-download failures."""

from __future__ import annotations

import io
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

from powershell_runtime import ProvisioningError, download_artifact

_URL = (
    "https://github.com/PowerShell/PowerShell/releases/download/v7.6.5/PowerShell-7.6.5-win-x64.zip"
)


class _PartialTimeoutResponse(io.BytesIO):
    """Yield one partial chunk before modelling a read timeout."""

    def __init__(self, payload: bytes) -> None:
        super().__init__(payload)
        self._served_partial_chunk = False

    def read(self, size: int = -1) -> bytes:
        if self._served_partial_chunk:
            raise TimeoutError("The read operation timed out")
        self._served_partial_chunk = True
        return super().read(min(size, 3))


class PowerShellRuntimeDownloadTest(unittest.TestCase):
    """Prove bounded recovery without retrying integrity or HTTP-status failures."""

    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.destination = Path(self.temporary_directory.name) / "PowerShell-7.6.5-win-x64.zip"

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def test_retries_a_partial_timeout_with_a_fresh_destination(self) -> None:
        payload = b"complete verified response"
        with (
            patch(
                "powershell_runtime_download.urllib.request.urlopen",
                side_effect=[_PartialTimeoutResponse(b"partial"), io.BytesIO(payload)],
            ) as opener,
            patch("powershell_runtime_download.time.sleep") as sleeper,
        ):
            download_artifact(_URL, self.destination)

        self.assertEqual(payload, self.destination.read_bytes())
        self.assertEqual(2, opener.call_count)
        sleeper.assert_called_once_with(1)

    def test_does_not_retry_an_http_status_failure(self) -> None:
        failure = urllib.error.HTTPError(_URL, 404, "Not Found", None, None)
        with (
            patch(
                "powershell_runtime_download.urllib.request.urlopen",
                side_effect=failure,
            ) as opener,
            patch("powershell_runtime_download.time.sleep") as sleeper,
            self.assertRaisesRegex(ProvisioningError, "HTTP Error 404"),
        ):
            download_artifact(_URL, self.destination)

        opener.assert_called_once()
        sleeper.assert_not_called()

    def test_does_not_retry_a_non_transport_url_failure(self) -> None:
        with (
            patch(
                "powershell_runtime_download.urllib.request.urlopen",
                side_effect=urllib.error.URLError("unknown url type"),
            ) as opener,
            patch("powershell_runtime_download.time.sleep") as sleeper,
            self.assertRaisesRegex(ProvisioningError, "unknown url type"),
        ):
            download_artifact(_URL, self.destination)

        opener.assert_called_once()
        sleeper.assert_not_called()

    def test_stops_after_the_bounded_timeout_attempts(self) -> None:
        with (
            patch(
                "powershell_runtime_download.urllib.request.urlopen",
                side_effect=TimeoutError("The read operation timed out"),
            ) as opener,
            patch("powershell_runtime_download.time.sleep") as sleeper,
            self.assertRaisesRegex(ProvisioningError, "read operation timed out"),
        ):
            download_artifact(_URL, self.destination)

        self.assertEqual(3, opener.call_count)
        self.assertEqual(2, sleeper.call_count)
        self.assertFalse(self.destination.exists())
