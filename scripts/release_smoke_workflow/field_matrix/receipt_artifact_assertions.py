"""Descriptor-bound checks for exported attestation-receipt artifacts."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from ..path_support import normalize_reported_path
from ..support import require
from .artifact_publication_evidence import require_publication_evidence
from .capabilities import ArtifactCapability, OperationCapability

_RECEIPT_FILE_OPTION = "--receipt-file"


def required_receipt_artifact(operation: OperationCapability) -> ArtifactCapability:
    """Return the sole receipt artifact advertised by an export operation.

    Receipt exports are machine-readable artifact producers.  The matrix must
    refuse an incomplete descriptor rather than infer the receipt format from
    a response or duplicate it in workflow code.
    """
    require(
        "json" in operation.output_modes,
        f"field-matrix {operation.operation_id} must advertise JSON to publish its receipt artifact",
    )
    receipt_artifacts = tuple(
        artifact
        for artifact in operation.artifact_outputs
        if artifact.option_flag == _RECEIPT_FILE_OPTION
    )
    require(
        len(receipt_artifacts) == 1,
        f"field-matrix {operation.operation_id} did not advertise exactly one receipt artifact",
    )
    require(
        len(operation.artifact_outputs) == 1,
        f"field-matrix {operation.operation_id} advertised unsupported additional artifacts",
    )
    return receipt_artifacts[0]


def assert_exported_receipt_artifact(
    config: ReleaseSmokeConfig,
    operation: OperationCapability,
    artifact: ArtifactCapability,
    receipt_path: SmokePath,
    envelope: Mapping[str, Any],
    purpose: str,
) -> None:
    """Bind a JSON export response to its exact advertised receipt artifact."""
    require(
        artifact in operation.artifact_outputs,
        f"{config.label} {purpose} used an unadvertised receipt artifact descriptor",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} {purpose} did not report ok status",
    )
    raw_artifacts = envelope.get("artifacts")
    require(
        isinstance(raw_artifacts, list) and len(raw_artifacts) == 1,
        f"{config.label} {purpose} did not publish exactly one receipt artifact",
    )
    if not isinstance(raw_artifacts, list) or len(raw_artifacts) != 1:
        raise AssertionError("require must reject an invalid receipt artifact array")
    reported_artifact = raw_artifacts[0]
    require(
        isinstance(reported_artifact, dict),
        f"{config.label} {purpose} published a non-object receipt artifact",
    )
    if not isinstance(reported_artifact, dict):
        raise TypeError("require must reject a non-object receipt artifact")
    require(
        reported_artifact.get("format") == artifact.format,
        f"{config.label} {purpose} did not report the advertised receipt artifact format",
    )
    reported_path = reported_artifact.get("path")
    require(
        isinstance(reported_path, str) and bool(reported_path.strip()),
        f"{config.label} {purpose} did not report a receipt artifact path",
    )
    if not isinstance(reported_path, str):
        raise TypeError("require must reject a missing receipt artifact path")
    expected_path = canonical_receipt_reported_path(config, receipt_path)
    require(
        normalize_reported_path(reported_path) == normalize_reported_path(expected_path),
        f"{config.label} {purpose} did not report the canonical receipt artifact path",
    )
    require_publication_evidence(
        config,
        receipt_path,
        reported_path,
        reported_artifact,
        purpose,
        "publication-transaction",
    )


def canonical_receipt_reported_path(config: ReleaseSmokeConfig, receipt_path: SmokePath) -> str:
    """Return the canonical physical receipt path visible to the selected runtime.

    A receipt is a security-sensitive artifact.  Successful export and
    verification report its resolved physical location, not an alias supplied
    by the caller.  Relative container invocations still report in the
    container's public work-root namespace.
    """
    try:
        canonical_local_path = receipt_path.local_path.resolve(strict=False)
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"could not resolve receipt artifact path for release smoke: {receipt_path.local_path}"
        ) from exc
    if config.reported_work_root is None or receipt_path.argument == str(receipt_path.local_path):
        return str(canonical_local_path)
    try:
        canonical_relative_path = canonical_local_path.relative_to(
            config.work_root.resolve(strict=True)
        )
    except (OSError, ValueError) as exc:
        raise ReleaseSmokeFailure(
            f"canonical receipt artifact path escaped release-smoke work root: {canonical_local_path}"
        ) from exc
    return str(config.reported_work_root / canonical_relative_path)
