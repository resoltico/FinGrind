"""CSV/PDF conflict execution and text-diagnostic proof."""

from __future__ import annotations

from ..cli import run_cli_allow_failure_with_split_streams
from ..models import SmokePath
from ..scenario_paths import sibling_smoke_path
from ..support import require, require_labeled_text_value, require_match
from .capabilities import ArtifactCapability, OperationCapability
from .report_contexts import ReportBookContext
from .report_routes import _CSV_PDF_REFUSAL_CODE, _CSV_PDF_REFUSAL_MESSAGE


def _verify_csv_pdf_refusal(
    context: ReportBookContext,
    operation: OperationCapability,
    arguments: tuple[str, ...],
    artifact: ArtifactCapability,
    artifact_index: int,
    expected_exit_code: int,
) -> None:
    require(
        "csv" in operation.output_modes,
        f"field-matrix report {operation.operation_id} did not advertise CSV stdout",
    )
    rejected_pdf_path = sibling_smoke_path(
        context.config.trial_balance_pdf,
        f"field-matrix-{operation.operation_id}-{artifact_index}-csv-rejected.pdf",
    )
    require(
        not rejected_pdf_path.local_path.exists(),
        f"{context.config.label} field-matrix rejected PDF target already exists: {rejected_pdf_path.local_path}",
    )
    stdout, stderr, exit_code = run_cli_allow_failure_with_split_streams(
        context.config,
        operation.operation_id,
        *arguments,
        "--output",
        "csv",
        artifact.option_flag,
        rejected_pdf_path.argument,
    )
    _assert_csv_pdf_refusal_text(
        context,
        operation,
        artifact,
        rejected_pdf_path,
        stdout,
        stderr,
        exit_code,
        expected_exit_code,
    )


def _assert_csv_pdf_refusal_text(
    context: ReportBookContext,
    operation: OperationCapability,
    artifact: ArtifactCapability,
    rejected_pdf_path: SmokePath,
    stdout: str,
    stderr: str,
    exit_code: int,
    expected_exit_code: int,
) -> None:
    """Verify the documented text-only diagnostic for the CSV/PDF conflict.

    CSV has no failure grammar.  Selecting CSV therefore deliberately routes
    this refusal to the text diagnostic renderer on stderr; requiring a JSON
    envelope here would reject the public contract it is meant to protect.
    """
    require(
        exit_code == expected_exit_code,
        f"{context.config.label} field-matrix {operation.operation_id} CSV/{artifact.format} refusal "
        f"exited with {exit_code} instead of the published {_CSV_PDF_REFUSAL_CODE} exit code",
    )
    require(
        stdout == "",
        f"{context.config.label} field-matrix {operation.operation_id} CSV/{artifact.format} refusal "
        "wrote a primary result to stdout",
    )
    require_match(
        stderr,
        r"^Error$",
        f"{context.config.label} field-matrix {operation.operation_id} CSV/{artifact.format} refusal "
        "did not use the text error renderer",
    )
    require_labeled_text_value(
        stderr,
        "Code",
        _CSV_PDF_REFUSAL_CODE,
        f"{context.config.label} field-matrix {operation.operation_id} CSV/{artifact.format} refusal "
        f"did not report {_CSV_PDF_REFUSAL_CODE}",
    )
    require_labeled_text_value(
        stderr,
        "Message",
        _CSV_PDF_REFUSAL_MESSAGE,
        f"{context.config.label} field-matrix {operation.operation_id} CSV/{artifact.format} refusal "
        "did not report the exact CSV/PDF diagnostic",
    )
    require_labeled_text_value(
        stderr,
        "Argument",
        "--output",
        f"{context.config.label} field-matrix {operation.operation_id} CSV/{artifact.format} refusal "
        "did not identify --output as the invalid selection",
    )
    require(
        not rejected_pdf_path.local_path.exists(),
        f"{context.config.label} field-matrix {operation.operation_id} created a {artifact.format} "
        "artifact after CSV refusal",
    )
