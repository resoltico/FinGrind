"""Assertion ownership for maintenance target-collision refusals."""

from __future__ import annotations

from .support import parse_json_output, require

_MAINTENANCE_REJECTION_EXIT_CODE = 7


def require_maintenance_collision_rejection(
    output: str,
    exit_code: int,
    expected_code: str,
    label: str,
    purpose: str,
) -> None:
    """Require one maintenance command to report its exact no-clobber refusal."""
    envelope = parse_json_output(output, f"{label} {purpose} output was not valid JSON")
    require(
        exit_code == _MAINTENANCE_REJECTION_EXIT_CODE
        and envelope.get("status") == "rejected"
        and envelope.get("code") == expected_code,
        f"{label} {purpose} did not report its exact no-clobber rejection: "
        f"exit={exit_code}, status={envelope.get('status')!r}, "
        f"code={envelope.get('code')!r}, output={output!r}",
    )
