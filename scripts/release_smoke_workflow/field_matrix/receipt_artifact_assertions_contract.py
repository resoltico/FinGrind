"""Synthetic contracts for descriptor-bound attestation-receipt artifacts."""

from __future__ import annotations

import os
import pathlib
import tempfile

from ..bridge_contract_support import base_bridge_config, smoke_path
from .capabilities import ArtifactCapability, OperationCapability
from .receipt_artifact_assertions import canonical_receipt_reported_path, required_receipt_artifact
from .receipt_artifact_coverage_contract import (
    assert_query_matrix_verifies_before_coverage_credit,
)
from .receipt_artifact_response_contract import (
    assert_exported_receipt_response_contract,
)
from .receipt_response_anchor_contract import (
    assert_receipt_response_anchor_contract,
    require_rejected,
)


def assert_receipt_artifact_contract(repo_root: pathlib.Path) -> None:
    """Keep receipt-artifact discovery and coverage evidence fail-closed."""
    assert_query_matrix_verifies_before_coverage_credit()
    with tempfile.TemporaryDirectory() as temporary_directory:
        temporary_path = pathlib.Path(temporary_directory)
        receipt_path = smoke_path(
            temporary_path,
            pathlib.Path("receipts") / "field-matrix-receipt.fgar",
        )
        receipt_path.local_path.parent.mkdir(mode=0o700)
        receipt_path.local_path.write_bytes(b"receipt")
        retained_stage = receipt_path.local_path.with_name(".field-matrix-receipt.fgar-stage")
        retained_stage.write_bytes(b"retained receipt")
        if os.name == "posix":
            receipt_path.local_path.chmod(0o600)
            retained_stage.chmod(0o600)
        config = base_bridge_config(
            repo_root,
            temporary_path,
            temporary_path / "unused-bridge.py",
            smoke_path(temporary_path, pathlib.Path("dummy")),
            runtime_distribution_key="bundleRuntimeDistribution",
            reported_work_root=None,
            book_key_output_permissions="0600",
            pdf_path=receipt_path,
            pdf_argument_override=None,
            stderr_path=temporary_path / "stderr.txt",
            label="receipt artifact regression",
        )
        operation = _receipt_export_operation()
        artifact = required_receipt_artifact(operation)
        expected_path = canonical_receipt_reported_path(config, receipt_path)
        expected_retained_stage = str(retained_stage.resolve(strict=True))
        assert_receipt_response_anchor_contract(receipt_path, expected_path)
        assert_exported_receipt_response_contract(
            config,
            operation,
            artifact,
            receipt_path,
            expected_path,
            expected_retained_stage,
        )
    require_rejected(
        lambda: required_receipt_artifact(
            OperationCapability(
                "export-attestation-receipt",
                "Export Attestation Receipt",
                "query",
                ("text",),
                (ArtifactCapability("synthetic-receipt-format", "--receipt-file <path>"),),
            )
        ),
        "must advertise JSON",
    )


def _receipt_export_operation() -> OperationCapability:
    return OperationCapability(
        "export-attestation-receipt",
        "Export Attestation Receipt",
        "query",
        ("json", "text"),
        (ArtifactCapability("synthetic-receipt-format", "--receipt-file <path>"),),
    )
