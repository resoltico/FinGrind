"""Public-envelope assertions for receipt trust-boundary smoke scenarios."""

from __future__ import annotations

from .artifact_contracts import expected_reported_path
from .models import ReleaseSmokeConfig, SmokePath
from .support import parse_json_output, require

_INVALID_OUTPUT_DIRECTORY_CODE = "invalid-artifact-output-directory"
_INVALID_OUTPUT_DIRECTORY_MESSAGE = (
    "The receipt output parent must be an existing real private directory whose resolved "
    "ancestry resists non-owner substitution."
)
_INVALID_OUTPUT_DIRECTORY_HINT = (
    "Choose an existing private output directory with secure resolved ancestry for "
    "--receipt-file, then rerun the command."
)


def require_successful_receipt_path(
    envelope: dict[str, object],
    payload: dict[str, object],
    expected_path: str,
    expected_retention_messages: list[str],
    retention_field: str,
    label: str,
    purpose: str,
) -> None:
    """Requires the canonical receipt path and exact trust-boundary result."""
    require(
        envelope.get("status") == "ok"
        and payload.get("receiptFile") == expected_path
        and payload.get(retention_field) == expected_retention_messages,
        f"{label} {purpose} did not report the exact canonical receipt path and trust result",
    )
    if retention_field != "warnings":
        return
    artifacts = envelope.get("artifacts")
    require(
        isinstance(artifacts, list)
        and len(artifacts) == 1
        and isinstance(artifacts[0], dict)
        and artifacts[0].get("path") == expected_path,
        f"{label} {purpose} did not report the exact canonical receipt artifact path",
    )


def require_error(
    output: str,
    exit_code: int,
    expected_exit_code: int,
    expected_code: str,
    label: str,
    purpose: str,
) -> None:
    """Requires one exact hostile-path error response."""
    envelope = parse_json_output(output, f"{label} {purpose} output was not valid JSON")
    require(
        exit_code == expected_exit_code
        and envelope.get("status") == "error"
        and envelope.get("code") == expected_code,
        f"{label} {purpose} did not report its exact hostile-path rejection",
    )


def require_invalid_output_directory_refusal(
    output: str,
    exit_code: int,
    config: ReleaseSmokeConfig,
    error_exit_codes: dict[str, int],
    receipt_path: SmokePath,
    label: str,
    purpose: str,
) -> None:
    """Requires the complete public refusal contract for a nonprivate output parent."""
    expected_exit_code = error_exit_codes.get(_INVALID_OUTPUT_DIRECTORY_CODE)
    require(
        type(expected_exit_code) is int,
        f"{label} runtime contract did not publish {_INVALID_OUTPUT_DIRECTORY_CODE} exit semantics",
    )
    envelope = parse_json_output(output, f"{label} {purpose} output was not valid JSON")
    require(
        exit_code == expected_exit_code
        and envelope.get("status") == "error"
        and envelope.get("code") == _INVALID_OUTPUT_DIRECTORY_CODE
        and envelope.get("category") == "precondition"
        and envelope.get("message") == _INVALID_OUTPUT_DIRECTORY_MESSAGE
        and envelope.get("hint") == _INVALID_OUTPUT_DIRECTORY_HINT
        and envelope.get("argument") == "--receipt-file"
        and envelope.get("path") == expected_reported_path(config, receipt_path)
        and envelope.get("relatedPaths") == [],
        f"{label} {purpose} did not report the exact private-output-directory refusal",
    )
