"""Regression tests for bounded JaCoCo artifact reachability verification."""

from __future__ import annotations

import sys
import unittest
import urllib.error
from datetime import UTC, datetime
from email.message import Message
from pathlib import Path
from typing import Self

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import jacoco_artifact_verification


class Response:
    """Deterministic successful HTTP response."""

    def __init__(self, status: int) -> None:
        self.status = status

    def __enter__(self) -> Self:
        return self

    def __exit__(self, exc_type: object, exc_value: object, traceback: object) -> bool:
        return False


class SequenceOpener:
    """Return the supplied responses and failures in call order."""

    def __init__(self, outcomes: list[Response | BaseException]) -> None:
        self._outcomes = outcomes
        self.requests: list[tuple[str, str, int]] = []

    def __call__(self, request: urllib.request.Request, *, timeout: int) -> Response:
        self.requests.append((request.full_url, request.get_method(), timeout))
        outcome = self._outcomes.pop(0)
        if isinstance(outcome, BaseException):
            raise outcome
        return outcome


def http_error(status: int, retry_after: str | None = None) -> urllib.error.HTTPError:
    """Create one URL error with an optional server-directed retry interval."""

    headers = Message()
    if retry_after is not None:
        headers["Retry-After"] = retry_after
    return urllib.error.HTTPError(
        "https://repo.maven.apache.org/example.jar", status, "failure", headers, None
    )


class JacocoArtifactVerificationTest(unittest.TestCase):
    """Prove exact artifact reachability and retry boundaries without a network dependency."""

    def test_verifies_all_canonical_artifacts_with_head_requests(self) -> None:
        opener = SequenceOpener([Response(200) for _ in range(4)])

        jacoco_artifact_verification.verify_artifacts("0.8.15", open_request=opener)

        self.assertEqual(
            [request[0] for request in opener.requests],
            [
                (
                    "https://repo.maven.apache.org/maven2/org/jacoco/org.jacoco.agent/0.8.15/"
                    "org.jacoco.agent-0.8.15.jar"
                ),
                (
                    "https://repo.maven.apache.org/maven2/org/jacoco/org.jacoco.ant/0.8.15/"
                    "org.jacoco.ant-0.8.15.jar"
                ),
                (
                    "https://repo.maven.apache.org/maven2/org/jacoco/org.jacoco.core/0.8.15/"
                    "org.jacoco.core-0.8.15.jar"
                ),
                (
                    "https://repo.maven.apache.org/maven2/org/jacoco/org.jacoco.report/0.8.15/"
                    "org.jacoco.report-0.8.15.jar"
                ),
            ],
        )
        self.assertEqual(
            [(request[1], request[2]) for request in opener.requests],
            [("HEAD", 30)] * 4,
        )

    def test_retries_rate_limit_using_server_delay_then_succeeds(self) -> None:
        opener = SequenceOpener([http_error(429, "7"), Response(200)])
        delays: list[float] = []

        jacoco_artifact_verification.verify_artifact(
            "agent",
            "https://repo.maven.apache.org/example.jar",
            open_request=opener,
            sleeper=delays.append,
        )

        self.assertEqual(delays, [7.0])
        self.assertEqual(len(opener.requests), 2)

    def test_retries_rate_limit_using_http_date_with_a_bounded_delay(self) -> None:
        opener = SequenceOpener([http_error(429, "Wed, 01 Jan 2026 00:02:00 GMT"), Response(200)])
        delays: list[float] = []

        jacoco_artifact_verification.verify_artifact(
            "agent",
            "https://repo.maven.apache.org/example.jar",
            open_request=opener,
            sleeper=delays.append,
            clock=lambda: datetime(2026, 1, 1, tzinfo=UTC),
        )

        self.assertEqual(delays, [30.0])

    def test_retries_rate_limit_using_exponential_delay_for_an_invalid_header(self) -> None:
        opener = SequenceOpener([http_error(429, "not-a-date"), Response(200)])
        delays: list[float] = []

        jacoco_artifact_verification.verify_artifact(
            "agent",
            "https://repo.maven.apache.org/example.jar",
            open_request=opener,
            sleeper=delays.append,
        )

        self.assertEqual(delays, [1.0])

    def test_rejects_non_transient_http_failure_without_retry(self) -> None:
        opener = SequenceOpener([http_error(404)])
        delays: list[float] = []

        with self.assertRaisesRegex(
            jacoco_artifact_verification.JacocoArtifactVerificationError,
            "after 1 attempt\\(s\\): HTTP Error 404",
        ):
            jacoco_artifact_verification.verify_artifact(
                "agent",
                "https://repo.maven.apache.org/example.jar",
                open_request=opener,
                sleeper=delays.append,
            )

        self.assertEqual(delays, [])

    def test_fails_after_bounded_transient_retries(self) -> None:
        opener = SequenceOpener([http_error(503)] * 5)
        delays: list[float] = []

        with self.assertRaisesRegex(
            jacoco_artifact_verification.JacocoArtifactVerificationError,
            "after 5 attempt\\(s\\): HTTP Error 503",
        ):
            jacoco_artifact_verification.verify_artifact(
                "agent",
                "https://repo.maven.apache.org/example.jar",
                open_request=opener,
                sleeper=delays.append,
            )

        self.assertEqual(delays, [1.0, 2.0, 4.0, 8.0])

    def test_retries_network_failure_then_succeeds(self) -> None:
        opener = SequenceOpener([urllib.error.URLError("temporary failure"), Response(200)])
        delays: list[float] = []

        jacoco_artifact_verification.verify_artifact(
            "agent",
            "https://repo.maven.apache.org/example.jar",
            open_request=opener,
            sleeper=delays.append,
        )

        self.assertEqual(delays, [1.0])

    def test_fails_after_bounded_network_retries(self) -> None:
        opener = SequenceOpener([urllib.error.URLError("temporary failure")] * 5)
        delays: list[float] = []

        with self.assertRaisesRegex(
            jacoco_artifact_verification.JacocoArtifactVerificationError,
            "after 5 attempt\\(s\\): <urlopen error temporary failure>",
        ):
            jacoco_artifact_verification.verify_artifact(
                "agent",
                "https://repo.maven.apache.org/example.jar",
                open_request=opener,
                sleeper=delays.append,
            )

        self.assertEqual(delays, [1.0, 2.0, 4.0, 8.0])

    def test_retries_direct_timeout_then_succeeds(self) -> None:
        opener = SequenceOpener([TimeoutError("temporary timeout"), Response(200)])
        delays: list[float] = []

        jacoco_artifact_verification.verify_artifact(
            "agent",
            "https://repo.maven.apache.org/example.jar",
            open_request=opener,
            sleeper=delays.append,
        )

        self.assertEqual(delays, [1.0])

    def test_rejects_unexpected_success_status(self) -> None:
        opener = SequenceOpener([Response(204)])

        with self.assertRaisesRegex(
            jacoco_artifact_verification.JacocoArtifactVerificationError,
            "received HTTP 204",
        ):
            jacoco_artifact_verification.verify_artifact(
                "agent",
                "https://repo.maven.apache.org/example.jar",
                open_request=opener,
            )


if __name__ == "__main__":
    unittest.main()
