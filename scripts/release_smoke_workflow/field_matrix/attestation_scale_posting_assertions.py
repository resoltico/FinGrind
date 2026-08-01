"""Shared posting-provenance assertions for the attestation scale scenario."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_head_checks import AttestationCommit, attestation_commit_from_payload
from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure
from ..support import require, require_string


def record_posting_provenance_row(
    row: object,
    expected_commits: Mapping[str, AttestationCommit],
    seen_posting_ids: set[str],
    config: ReleaseSmokeConfig,
    surface: str,
) -> None:
    """Verify one JSON posting row and remember its unique identity."""
    require(
        isinstance(row, dict),
        f"{config.label} scale {surface} emitted a non-object posting row",
    )
    if not isinstance(row, dict):
        raise ReleaseSmokeFailure("require must reject a non-object posting row")
    posting_id = require_string(row, "postingId")
    require_expected_posting_commit(row, posting_id, expected_commits, config, surface)
    require(
        posting_id not in seen_posting_ids,
        f"{config.label} scale {surface} emitted a duplicate posting",
    )
    seen_posting_ids.add(posting_id)


def require_expected_posting_commit(
    posting: Mapping[str, object],
    posting_id: str,
    expected_commits: Mapping[str, AttestationCommit],
    config: ReleaseSmokeConfig,
    surface: str,
) -> None:
    """Require a posting payload to retain the exact recorded commitment."""
    require(
        posting_id in expected_commits,
        f"{config.label} scale {surface} emitted an unknown posting",
    )
    expected_commit = expected_commits[posting_id]
    actual_commit = attestation_commit_from_payload(
        dict(posting),
        config.label,
        f"scale {surface} posting {posting_id}",
    )
    require(
        actual_commit == expected_commit,
        f"{config.label} scale {surface} did not retain the exact posting attestation commitment",
    )


def require_csv_posting_provenance_rows(
    rows: list[dict[str, str]],
    expected_commits: Mapping[str, AttestationCommit],
    config: ReleaseSmokeConfig,
    surface: str,
) -> None:
    """Require one CSV surface to expose each posting exactly once with its commitment."""
    seen_posting_ids: set[str] = set()
    for row in rows:
        posting_id = row.get("postingId")
        require(
            isinstance(posting_id, str)
            and posting_id in expected_commits
            and posting_id not in seen_posting_ids,
            f"{config.label} scale {surface} emitted an unknown or duplicate posting",
        )
        expected_commit = expected_commits[posting_id]
        require(
            row.get("attestationOperationOrder") == expected_commit.operation_order
            and row.get("attestationOperationHead") == expected_commit.operation_head,
            f"{config.label} scale {surface} did not retain posting attestation provenance",
        )
        seen_posting_ids.add(posting_id)
    require(
        seen_posting_ids == set(expected_commits),
        f"{config.label} scale {surface} did not expose every posting provenance link",
    )
