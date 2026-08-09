"""Reliably download one pinned PowerShell archive without weakening its admission rules."""

from __future__ import annotations

import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Final

from powershell_runtime_archives import copy_stream
from powershell_runtime_metadata import immutable_archive_name_from_url
from powershell_runtime_models import MAX_ARCHIVE_BYTES, ProvisioningError

DOWNLOAD_ATTEMPTS: Final = 3
RETRY_DELAY_SECONDS: Final = 1


def download_artifact(url: str, destination: Path) -> None:
    """Download one immutable archive, retrying only retryable transport failures."""

    expected_archive_name = immutable_archive_name_from_url(url)
    if destination.name != expected_archive_name:
        raise ProvisioningError(
            "refusing unexpected PowerShell archive destination: "
            f"expected {expected_archive_name!r}, received {destination.name!r}"
        )
    destination.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(1, DOWNLOAD_ATTEMPTS + 1):
        try:
            _download_once(url, destination)
            return
        except (TimeoutError, ConnectionError, urllib.error.URLError) as error:
            if not _is_retryable_transport_error(error) or attempt == DOWNLOAD_ATTEMPTS:
                raise ProvisioningError(
                    f"could not download PowerShell release archive: {error}"
                ) from error
            _discard_partial_download(destination)
            time.sleep(RETRY_DELAY_SECONDS * attempt)
        except OSError as error:
            raise ProvisioningError(
                f"could not download PowerShell release archive: {error}"
            ) from error


def _download_once(url: str, destination: Path) -> None:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "FinGrind-PowerShell-Provisioner"},
    )
    with (
        urllib.request.urlopen(request, timeout=30) as response,
        destination.open("xb") as output,
    ):
        copy_stream(response, output, maximum_bytes=MAX_ARCHIVE_BYTES)


def _discard_partial_download(destination: Path) -> None:
    try:
        destination.unlink(missing_ok=True)
    except OSError as error:
        raise ProvisioningError(
            f"could not discard incomplete PowerShell release archive: {error}"
        ) from error


def _is_retryable_transport_error(error: BaseException) -> bool:
    if isinstance(error, (TimeoutError, ConnectionError)):
        return True
    return (
        isinstance(error, urllib.error.URLError)
        and not isinstance(error, urllib.error.HTTPError)
        and isinstance(error.reason, OSError)
    )
