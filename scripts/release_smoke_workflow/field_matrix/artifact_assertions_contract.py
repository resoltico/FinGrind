"""Focused synthetic contracts for field-matrix PDF artifact validation."""

from __future__ import annotations

import os
import pathlib
import tempfile

from ..artifact_contracts import (
    expected_public_artifact_path_hint,
    expected_public_pdf_artifact_path_hint,
)
from ..bridge_contract_support import base_bridge_config, smoke_path
from ..fixtures import prepare_owner_only_directory
from .pdf_artifact_contract_filesystem import (
    assert_missing_retained_stage_is_rejected,
    assert_path_mismatch_is_rejected,
    assert_platform_privacy_is_rejected,
    assert_public_hint_preserves_the_cli_visible_suffix,
    assert_symlink_is_rejected,
    assert_truncated_pdf_is_rejected,
)
from .pdf_artifact_contract_semantics import (
    assert_missing_repo_owned_extractor_is_actionable,
    assert_object_stream_page_tree_is_accepted,
    assert_repo_owned_pdf_extractor_contract,
    assert_report_matrix_wires_validation_before_coverage_credit,
    assert_semantic_pdf_evidence_is_required,
)
from .pdf_artifact_contract_support import artifact_confirmation, complete_pdf


def assert_pdf_artifact_contract(repo_root: pathlib.Path) -> None:
    """Keep artifact confirmation, structural completeness, and privacy checks fail-closed."""
    assert_repo_owned_pdf_extractor_contract(repo_root)
    assert_report_matrix_wires_validation_before_coverage_credit()
    with tempfile.TemporaryDirectory() as temporary_directory:
        temporary_path = pathlib.Path(temporary_directory)
        artifact_path = smoke_path(
            temporary_path,
            pathlib.Path("reports odd") / "field-matrix [contract].pdf",
        )
        prepare_owner_only_directory(artifact_path.local_path.parent)
        artifact_path.local_path.write_bytes(complete_pdf())
        retained_stage_path = artifact_path.local_path.with_name(".field-matrix-retained-stage.pdf")
        retained_stage_path.write_bytes(complete_pdf())
        if os.name == "posix":
            artifact_path.local_path.chmod(0o600)
            retained_stage_path.chmod(0o600)
        config = base_bridge_config(
            repo_root,
            temporary_path,
            temporary_path / "bridge.py",
            smoke_path(temporary_path, pathlib.Path("dummy")),
            runtime_distribution_key="bundleRuntimeDistribution",
            reported_work_root=None,
            book_key_output_permissions="owner-only-acl" if os.name == "nt" else "0600",
            pdf_path=artifact_path,
            pdf_argument_override=None,
            stderr_path=temporary_path / "stderr.txt",
            label="PDF artifact regression",
        )
        retained_stage = smoke_path(
            temporary_path,
            artifact_path.relative_path.with_name(retained_stage_path.name),
        )
        stdout = artifact_confirmation(
            expected_public_pdf_artifact_path_hint(config, artifact_path),
            expected_public_artifact_path_hint(retained_stage),
        )
        assert_missing_repo_owned_extractor_is_actionable(config, artifact_path)
        assert_missing_retained_stage_is_rejected(config, artifact_path)
        assert_object_stream_page_tree_is_accepted(config, artifact_path, stdout)
        assert_semantic_pdf_evidence_is_required(config, artifact_path, stdout)
        assert_public_hint_preserves_the_cli_visible_suffix(config, artifact_path)
        assert_path_mismatch_is_rejected(config, artifact_path)
        assert_truncated_pdf_is_rejected(config, artifact_path, stdout)
        assert_symlink_is_rejected(config, artifact_path, stdout)
        assert_platform_privacy_is_rejected(config, artifact_path, stdout)
