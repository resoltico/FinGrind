"""Synthetic text-diagnostic contract for the CSV plus PDF refusal boundary."""

from __future__ import annotations

import tempfile
from collections.abc import Callable
from pathlib import Path
from types import SimpleNamespace

from ..models import ReleaseSmokeFailure, SmokePath
from .capabilities import ArtifactCapability, OperationCapability
from .report_csv_pdf_refusal import _assert_csv_pdf_refusal_text


def assert_csv_pdf_refusal_uses_text_diagnostics() -> None:
    """CSV/PDF selection has a text diagnostic boundary, not an invented JSON one."""
    with tempfile.TemporaryDirectory(prefix="fingrind-csv-pdf-refusal-") as temporary_directory:
        temporary_path = Path(temporary_directory)
        template_pdf = SmokePath(
            Path("reports") / "template.pdf",
            temporary_path / "reports" / "template.pdf",
            str(temporary_path / "reports" / "template.pdf"),
        )
        rejected_pdf = SmokePath(
            Path("reports") / "csv-refused.pdf",
            temporary_path / "reports" / "csv-refused.pdf",
            str(temporary_path / "reports" / "csv-refused.pdf"),
        )
        context = SimpleNamespace(
            config=SimpleNamespace(
                label="synthetic CSV/PDF refusal",
                trial_balance_pdf=template_pdf,
            )
        )
        artifact = ArtifactCapability("pdf", "--pdf-out <path>")
        operation = OperationCapability(
            "trial-balance",
            "Trial Balance",
            "query",
            ("json", "text", "csv"),
            (artifact,),
        )
        stderr = (
            "Error\n=====\n\nCode     : unsupported-output-selection\n"
            "Message  : Unsupported output mode for --output: csv. When --pdf-out is selected, "
            "accepted stdout modes are json or text.\nArgument : --output\n"
        )
        _assert_csv_pdf_refusal_text(context, operation, artifact, rejected_pdf, "", stderr, 2, 2)
        require_route_evidence_rejection(
            lambda: _assert_csv_pdf_refusal_text(
                context,
                operation,
                artifact,
                rejected_pdf,
                '{"status":"error","code":"unsupported-output-selection"}\n',
                "",
                2,
                2,
            ),
            "wrote a primary result to stdout",
        )
        require_route_evidence_rejection(
            lambda: _assert_csv_pdf_refusal_text(
                context,
                operation,
                artifact,
                rejected_pdf,
                "",
                stderr.replace("Argument : --output", "Argument : --pdf-out"),
                2,
                2,
            ),
            "identify --output",
        )


def require_route_evidence_rejection(action: Callable[[], object], expected_message: str) -> None:
    """Require one route-specific response proof to reject an invalid boundary response."""
    try:
        action()
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError("field-matrix accepted a generic or mismatched mutation response")
