"""Descriptor and attestation-anchor contracts for receipt artifact responses."""

from __future__ import annotations

from ..models import ReleaseSmokeConfig, SmokePath
from .capabilities import ArtifactCapability, OperationCapability
from .receipt_artifact_assertions import assert_exported_receipt_artifact
from .receipt_response_anchor_contract import require_rejected


def assert_exported_receipt_response_contract(
    config: ReleaseSmokeConfig,
    operation: OperationCapability,
    artifact: ArtifactCapability,
    receipt_path: SmokePath,
    expected_path: str,
    expected_retained_stage: str,
) -> None:
    """Require one exact descriptor-bound receipt artifact response."""
    valid_envelope = {
        "status": "ok",
        "artifacts": [
            {
                "format": artifact.format,
                "path": expected_path,
                "retainedStage": expected_retained_stage,
            }
        ],
    }
    assert_exported_receipt_artifact(
        config,
        operation,
        artifact,
        receipt_path,
        valid_envelope,
        "synthetic receipt export",
    )
    require_rejected(
        lambda: assert_exported_receipt_artifact(
            config,
            operation,
            artifact,
            receipt_path,
            {
                "status": "ok",
                "artifacts": [
                    {
                        "format": "wrong-receipt-format",
                        "path": expected_path,
                        "retainedStage": expected_retained_stage,
                    }
                ],
            },
            "wrong receipt format",
        ),
        "advertised receipt artifact format",
    )
    require_rejected(
        lambda: assert_exported_receipt_artifact(
            config,
            operation,
            artifact,
            receipt_path,
            {
                "status": "ok",
                "artifacts": [
                    {
                        "format": artifact.format,
                        "path": "receipts/wrong.fgar",
                        "retainedStage": expected_retained_stage,
                    }
                ],
            },
            "wrong receipt path",
        ),
        "canonical receipt artifact path",
    )
    require_rejected(
        lambda: assert_exported_receipt_artifact(
            config,
            operation,
            artifact,
            receipt_path,
            {
                "status": "ok",
                "artifacts": [
                    {
                        "format": artifact.format,
                        "path": expected_path,
                        "retainedStage": expected_retained_stage,
                    },
                    {
                        "format": artifact.format,
                        "path": expected_path,
                        "retainedStage": expected_retained_stage,
                    },
                ],
            },
            "duplicate receipt artifacts",
        ),
        "exactly one receipt artifact",
    )
    require_rejected(
        lambda: assert_exported_receipt_artifact(
            config,
            operation,
            artifact,
            receipt_path,
            {"status": "ok", "artifacts": [{"format": artifact.format, "path": expected_path}]},
            "missing retained stage",
        ),
        "retainedStage",
    )
    require_rejected(
        lambda: assert_exported_receipt_artifact(
            config,
            operation,
            artifact,
            receipt_path,
            {
                "status": "ok",
                "artifacts": [
                    {
                        "format": artifact.format,
                        "path": expected_path,
                        "retainedStage": expected_path,
                    }
                ],
            },
            "self-referential retained stage",
        ),
        "reused the final artifact path",
    )
