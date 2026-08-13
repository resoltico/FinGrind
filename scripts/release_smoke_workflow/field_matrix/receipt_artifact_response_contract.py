"""Descriptor and attestation-anchor contracts for receipt artifact responses."""

from __future__ import annotations

from collections.abc import Mapping

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
    expected_publication_transaction: Mapping[str, str],
) -> None:
    """Require one exact descriptor-bound receipt artifact response."""
    valid_envelope = {
        "status": "ok",
        "artifacts": [
            {
                "format": artifact.format,
                "path": expected_path,
                "publicationTransaction": dict(expected_publication_transaction),
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
                        "publicationTransaction": dict(expected_publication_transaction),
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
                        "publicationTransaction": dict(expected_publication_transaction),
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
                        "publicationTransaction": dict(expected_publication_transaction),
                    },
                    {
                        "format": artifact.format,
                        "path": expected_path,
                        "publicationTransaction": dict(expected_publication_transaction),
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
            "missing publication transaction",
        ),
        "publicationTransaction",
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
                        "publicationTransaction": dict(expected_publication_transaction),
                        "retainedStage": expected_path,
                    }
                ],
            },
            "private retained stage after transaction migration",
        ),
        "private retainedStage",
    )
    blank_state = dict(expected_publication_transaction)
    blank_state["state"] = ""
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
                        "publicationTransaction": blank_state,
                    }
                ],
            },
            "blank publication transaction state",
        ),
        "blank publicationTransaction.state",
    )
