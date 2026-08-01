"""Inspection-surface proof for one noncurrent protected-book format."""

from __future__ import annotations

from ..cli import run_cli
from ..models import ReleaseSmokeConfig, SmokePath
from ..support import parse_json_output, require, required_mapping


def assert_inspection_rejection(
    config: ReleaseSmokeConfig,
    inspect_operation_id: str,
    boundary_book: SmokePath,
    boundary_key: SmokePath,
    boundary_name: str,
    boundary_format: int,
    supported_format: int,
) -> None:
    """Require inspect-book to publish the exact no-compatibility state."""
    inspection = parse_json_output(
        run_cli(
            config,
            inspect_operation_id,
            "--book-file",
            boundary_book.argument,
            "--book-key-file",
            boundary_key.argument,
            "--output",
            "json",
        ),
        f"{config.label} {boundary_name}-format inspect-book output was not valid JSON",
    )
    payload = required_mapping(inspection, "payload")
    migration_policy = required_mapping(payload, "migrationPolicy")
    require(
        inspection.get("status") == "ok"
        and payload.get("state") == "unsupported-format-version"
        and payload.get("compatibleWithCurrentBinary") is False
        and payload.get("canInitializeWithOpenBook") is False
        and payload.get("detectedBookFormatVersion") == boundary_format
        and payload.get("supportedBookFormatVersion") == supported_format,
        f"{config.label} fresh archive did not reject {boundary_name} protected-book format "
        f"{boundary_format} against supported format {supported_format}",
    )
    require(
        migration_policy.get("mode") == "hard-break-reject-noncurrent-formats"
        and migration_policy.get("inPlaceUpgradeSupported") is False
        and migration_policy.get("olderFormatsAccepted") is False
        and migration_policy.get("newerFormatsAccepted") is False
        and migration_policy.get("supportedBookFormatVersion") == supported_format,
        f"{config.label} {boundary_name}-format inspection did not publish the no-compatibility policy",
    )
