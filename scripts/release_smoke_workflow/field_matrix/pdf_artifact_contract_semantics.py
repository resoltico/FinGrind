"""Semantic and parser-bound PDF artifact contract checks."""

from __future__ import annotations

import inspect
import os
import pathlib
import sys
from unittest.mock import patch

from ..models import ReleaseSmokeConfig, SmokePath
from .artifact_assertions import _extract_pdf_semantic_text, _PdfSemanticText, assert_pdf_artifact
from .pdf_artifact_contract_support import (
    complete_pdf,
    object_stream_page_tree_pdf,
    require_rejected,
)


def assert_repo_owned_pdf_extractor_contract(repo_root: pathlib.Path) -> None:
    """Keep semantic evidence independent of ambient Poppler installation state."""
    requirements_path = repo_root / "requirements-release-smoke-workflow.txt"
    requirement_lines = [
        line.strip()
        for line in requirements_path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    assert requirement_lines == ["pypdf==6.16.2"], (
        "release-smoke must provision exactly its pinned repo-owned PDF extractor"
    )
    extractor_source = inspect.getsource(_extract_pdf_semantic_text)
    assert "from pypdf import PdfReader" in extractor_source
    assert "pdftotext" not in extractor_source


def assert_missing_repo_owned_extractor_is_actionable(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
) -> None:
    """Require an actionable diagnostic from a direct host-Python launch."""
    with patch.dict(sys.modules, {"pypdf": None, "pypdf.errors": None}):
        require_rejected(
            lambda: _extract_pdf_semantic_text(config, artifact_path, "missing PDF extractor"),
            "pinned uv tool environment",
        )


def assert_report_matrix_wires_validation_before_coverage_credit() -> None:
    """Keep semantic artifact proof ahead of the matrix's exact-artifact credit."""
    from .report_pdf_artifacts import _verify_pdf_artifact

    source = inspect.getsource(_verify_pdf_artifact)
    validation_call = source.index("assert_pdf_artifact(")
    coverage_credit = source.index("record_verified_artifact(")
    assert validation_call < coverage_credit, (
        "field-matrix recorded a PDF artifact before complete/private artifact validation"
    )
    assert "expected_document_title=operation.display_label" in source
    assert "expected_text_facts=(context.expected_report_token(operation.operation_id),)" in source


def assert_semantic_pdf_evidence_is_required(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    stdout: str,
) -> None:
    """A complete private PDF is insufficient without identity and a known fact."""
    with patch(
        "release_smoke_workflow.field_matrix.artifact_assertions._extract_pdf_semantic_text",
        return_value=_PdfSemanticText(
            layout="Trial Balance\nGenerated 2026-01-01 / Prepared by FinGrind\nknown-account\n",
            default="Trial Balance\nGenerated 2026-01-01 / Prepared by FinGrind\nknown-account\n",
        ),
    ):
        assert_pdf_artifact(
            config,
            artifact_path,
            stdout,
            "semantic PDF export",
            expected_document_title="Trial Balance",
            expected_text_facts=("known-account",),
        )
        require_rejected(
            lambda: assert_pdf_artifact(
                config,
                artifact_path,
                stdout,
                "wrong-report PDF export",
                expected_document_title="Account Ledger",
                expected_text_facts=("known-account",),
            ),
            "canonical report title",
        )
        require_rejected(
            lambda: assert_pdf_artifact(
                config,
                artifact_path,
                stdout,
                "missing-fact PDF export",
                expected_document_title="Trial Balance",
                expected_text_facts=("different-known-account",),
            ),
            "required report facts",
        )
    with patch(
        "release_smoke_workflow.field_matrix.artifact_assertions._extract_pdf_semantic_text",
        return_value=_PdfSemanticText(
            layout="Accrual Cut-O\ufb00 Schedule\nmatrix-prepayment-        PREPAYMENT\n2026-q1\n",
            default="Accrual Cut-O\ufb00 Schedule\nmatrix-prepayment-\n2026-q1\n",
        ),
    ):
        assert_pdf_artifact(
            config,
            artifact_path,
            stdout,
            "ligature semantic PDF export",
            expected_document_title="Accrual Cut-Off Schedule",
            expected_text_facts=("matrix-prepayment-2026-q1",),
        )
        require_rejected(
            lambda: assert_pdf_artifact(
                config,
                artifact_path,
                stdout,
                "misspelled ligature semantic PDF export",
                expected_document_title="Accrual Cut-Of Schedule",
                expected_text_facts=("matrix-prepayment-2026-q1",),
            ),
            "canonical report title",
        )
        require_rejected(
            lambda: assert_pdf_artifact(
                config,
                artifact_path,
                stdout,
                "missing interleaved semantic PDF fact",
                expected_document_title="Accrual Cut-Off Schedule",
                expected_text_facts=("matrix-prepayment-2026-q2",),
            ),
            "required report facts",
        )


def assert_object_stream_page_tree_is_accepted(
    config: ReleaseSmokeConfig,
    artifact_path: SmokePath,
    stdout: str,
) -> None:
    """A valid compressed page tree must not fail raw-byte scanning."""
    object_stream_pdf = object_stream_page_tree_pdf()
    assert b"/Type /Pages" not in object_stream_pdf
    assert b"/Type /ObjStm" in object_stream_pdf
    artifact_path.local_path.write_bytes(object_stream_pdf)
    if os.name == "posix":
        artifact_path.local_path.chmod(0o600)
    assert_pdf_artifact(config, artifact_path, stdout, "object-stream PDF export")
    artifact_path.local_path.write_bytes(complete_pdf())
    if os.name == "posix":
        artifact_path.local_path.chmod(0o600)
