"""Verify the pinned JaCoCo artifacts with bounded handling of transient repository failures."""

from __future__ import annotations

import argparse
import math
import sys
import time
import urllib.error
import urllib.request
from collections.abc import Callable
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime
from typing import Protocol, Self

_ARTIFACT_BASE = "https://repo.maven.apache.org/maven2/org/jacoco"
_ARTIFACT_PATHS = {
    "agent": "org.jacoco.agent/{version}/org.jacoco.agent-{version}.jar",
    "ant": "org.jacoco.ant/{version}/org.jacoco.ant-{version}.jar",
    "core": "org.jacoco.core/{version}/org.jacoco.core-{version}.jar",
    "report": "org.jacoco.report/{version}/org.jacoco.report-{version}.jar",
}
_REQUEST_TIMEOUT_SECONDS = 30
_MAX_ATTEMPTS = 5
_MAX_RETRY_DELAY_SECONDS = 30


class _HttpResponse(Protocol):
    """The small response surface required from urllib and deterministic test doubles."""

    status: int

    def __enter__(self) -> Self: ...

    def __exit__(self, exc_type: object, exc_value: object, traceback: object) -> bool: ...


class JacocoArtifactVerificationError(RuntimeError):
    """Raised when one pinned JaCoCo artifact cannot be verified as reachable."""


def artifact_urls(version: str) -> dict[str, str]:
    """Return the canonical Maven Central URLs for one exact JaCoCo version."""

    return {
        label: f"{_ARTIFACT_BASE}/{path.format(version=version)}"
        for label, path in _ARTIFACT_PATHS.items()
    }


def verify_artifacts(
    version: str,
    *,
    open_request: Callable[..., _HttpResponse] = urllib.request.urlopen,
    sleeper: Callable[[float], None] = time.sleep,
    clock: Callable[[], datetime] = lambda: datetime.now(UTC),
) -> None:
    """Prove every exact JaCoCo artifact is reachable from its canonical repository URL."""

    for label, url in artifact_urls(version).items():
        verify_artifact(label, url, open_request=open_request, sleeper=sleeper, clock=clock)


def verify_artifact(
    label: str,
    url: str,
    *,
    open_request: Callable[..., _HttpResponse] = urllib.request.urlopen,
    sleeper: Callable[[float], None] = time.sleep,
    clock: Callable[[], datetime] = lambda: datetime.now(UTC),
) -> None:
    """Verify one artifact, retrying only failures that are operationally transient."""

    request = urllib.request.Request(
        url,
        headers={"User-Agent": "FinGrind-JaCoCo-Artifact-Verifier"},
        method="HEAD",
    )
    for attempt in range(1, _MAX_ATTEMPTS + 1):
        try:
            with open_request(request, timeout=_REQUEST_TIMEOUT_SECONDS) as response:
                if response.status != 200:
                    raise JacocoArtifactVerificationError(
                        f"JaCoCo artifact {label!r} was not reachable at {url!r}: "
                        f"received HTTP {response.status}"
                    )
                return
        except urllib.error.HTTPError as error:
            if not _is_transient_http_error(error) or attempt == _MAX_ATTEMPTS:
                raise _failure(label, url, attempt, error) from error
            delay_seconds = _retry_delay_seconds(error, attempt, clock)
            _report_retry(label, attempt, error.code, delay_seconds)
            sleeper(delay_seconds)
        except urllib.error.URLError as error:
            if attempt == _MAX_ATTEMPTS:
                raise _failure(label, url, attempt, error) from error
            delay_seconds = _exponential_delay_seconds(attempt)
            _report_retry(label, attempt, "network", delay_seconds)
            sleeper(delay_seconds)
        except TimeoutError as error:
            if attempt == _MAX_ATTEMPTS:
                raise _failure(label, url, attempt, error) from error
            delay_seconds = _exponential_delay_seconds(attempt)
            _report_retry(label, attempt, "timeout", delay_seconds)
            sleeper(delay_seconds)

    raise AssertionError("bounded JaCoCo artifact retry loop terminated without a result")


def _failure(
    label: str, url: str, attempt: int, error: BaseException
) -> JacocoArtifactVerificationError:
    return JacocoArtifactVerificationError(
        f"JaCoCo artifact {label!r} was not reachable at {url!r} after {attempt} attempt(s): "
        f"{error}"
    )


def _is_transient_http_error(error: urllib.error.HTTPError) -> bool:
    return error.code == 429 or 500 <= error.code <= 599


def _retry_delay_seconds(
    error: urllib.error.HTTPError, attempt: int, clock: Callable[[], datetime]
) -> float:
    retry_after = error.headers.get("Retry-After") if error.headers is not None else None
    if retry_after is None:
        return _exponential_delay_seconds(attempt)
    parsed_delay_seconds = _parse_retry_after_seconds(retry_after, clock)
    if parsed_delay_seconds is None:
        return _exponential_delay_seconds(attempt)
    return parsed_delay_seconds


def _parse_retry_after_seconds(value: str, clock: Callable[[], datetime]) -> float | None:
    normalized_value = value.strip()
    if normalized_value.isdecimal():
        return float(min(max(int(normalized_value), 1), _MAX_RETRY_DELAY_SECONDS))
    try:
        retry_at = parsedate_to_datetime(normalized_value)
    except (IndexError, TypeError, ValueError):
        return None
    if retry_at.tzinfo is None:
        return None
    remaining_seconds = math.ceil((retry_at - clock()).total_seconds())
    if remaining_seconds <= 0:
        return None
    return float(min(remaining_seconds, _MAX_RETRY_DELAY_SECONDS))


def _exponential_delay_seconds(attempt: int) -> float:
    return float(min(2 ** (attempt - 1), _MAX_RETRY_DELAY_SECONDS))


def _report_retry(label: str, attempt: int, status: int | str, delay_seconds: float) -> None:
    print(
        "JaCoCo artifact request retry: "
        f"label={label} attempt={attempt}/{_MAX_ATTEMPTS} status={status} "
        f"delay_seconds={delay_seconds:g}",
        file=sys.stderr,
    )


def main(argv: list[str] | None = None) -> None:
    """Run the repository-facing verifier command."""

    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    arguments = parser.parse_args(argv)
    verify_artifacts(arguments.version)
    print(f"JaCoCo artifacts verified: version={arguments.version}")


if __name__ == "__main__":
    main()
