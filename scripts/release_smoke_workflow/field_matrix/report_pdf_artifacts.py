"""PDF artifact execution and evidence for report scenarios."""

from __future__ import annotations

import os

from ..attestation_receipt_security_symlinks import make_directory_symlink
from ..cli import run_cli_allow_failure_with_split_streams, run_cli_with_split_streams
from ..fixtures import prepare_owner_only_directory
from ..scenario_paths import sibling_smoke_path, smoke_path_from_local
from ..support import parse_json_output, require
from .artifact_assertions import assert_pdf_artifact
from .capabilities import ArtifactCapability, OperationCapability
from .context import record_verified_artifact
from .report_contexts import ReportBookContext

_INVALID_OUTPUT_DIRECTORY_CODE = "invalid-artifact-output-directory"


def _verify_pdf_artifact(
    context: ReportBookContext,
    operation: OperationCapability,
    arguments: tuple[str, ...],
    artifact: ArtifactCapability,
    artifact_index: int,
) -> None:
    require(
        "text" in operation.output_modes,
        f"field-matrix report {operation.operation_id} cannot render its PDF with text stdout",
    )
    require(
        artifact.format == "pdf",
        f"field-matrix report {operation.operation_id} advertised a non-PDF report artifact "
        f"{artifact.format} via {artifact.option}",
    )
    pdf_path = sibling_smoke_path(
        context.config.trial_balance_pdf,
        f"field-matrix-{operation.operation_id}-{artifact_index}.pdf",
    )
    require(
        not pdf_path.local_path.exists(),
        f"{context.config.label} field-matrix PDF target already exists: {pdf_path.local_path}",
    )
    stdout, stderr = run_cli_with_split_streams(
        context.config,
        operation.operation_id,
        *arguments,
        "--output",
        "text",
        artifact.option_flag,
        pdf_path.argument,
    )
    require(
        stderr == "",
        f"{context.config.label} field-matrix {operation.operation_id}[pdf] wrote unexpected stderr:\n{stderr}",
    )
    assert_pdf_artifact(
        context.config,
        pdf_path,
        stdout,
        f"field-matrix {operation.operation_id}[{artifact.option}]",
        expected_document_title=operation.display_label,
        expected_text_facts=(context.expected_report_token(operation.operation_id),),
    )
    record_verified_artifact(operation.operation_id, artifact)


def verify_pdf_intermediate_symlink_refusal(
    context: ReportBookContext,
    operation: OperationCapability,
    arguments: tuple[str, ...],
    artifact: ArtifactCapability,
    expected_exit_code: int,
) -> None:
    """Requires the shared PDF publisher to reject an intermediate symlink component."""
    if os.name != "posix":
        return
    security_root = (
        context.config.trial_balance_pdf.local_path.parent
        / f"field-matrix-{operation.operation_id}-pdf-output-security"
    )
    require(
        not security_root.exists(),
        f"{context.config.label} PDF output security root already exists: {security_root}",
    )
    prepare_owner_only_directory(security_root)
    physical_root = security_root / "physical-root"
    real_parent = physical_root / "real-parent"
    prepare_owner_only_directory(physical_root)
    prepare_owner_only_directory(real_parent)
    intermediate_alias = security_root / "intermediate-alias"
    make_directory_symlink(intermediate_alias, physical_root, context.config.label)
    rejected_pdf = smoke_path_from_local(
        context.config,
        intermediate_alias / real_parent.name / "must-not-publish.pdf",
    )
    stdout, stderr, exit_code = run_cli_allow_failure_with_split_streams(
        context.config,
        operation.operation_id,
        *arguments,
        "--output",
        "json",
        artifact.option_flag,
        rejected_pdf.argument,
    )
    require(
        exit_code == expected_exit_code and stdout == "",
        f"{context.config.label} field-matrix {operation.operation_id} PDF intermediate-alias "
        "refusal did not use the advertised error exit without stdout output",
    )
    envelope = parse_json_output(
        stderr,
        f"{context.config.label} field-matrix {operation.operation_id} PDF intermediate-alias "
        "refusal did not write a JSON diagnostic",
    )
    require(
        envelope.get("status") == "error"
        and envelope.get("code") == _INVALID_OUTPUT_DIRECTORY_CODE
        and envelope.get("argument") == artifact.option_flag
        and "artifacts" not in envelope,
        f"{context.config.label} field-matrix {operation.operation_id} PDF intermediate-alias "
        "refusal did not preserve the public no-publication contract",
    )
    require(
        not tuple(real_parent.iterdir()),
        f"{context.config.label} field-matrix {operation.operation_id} PDF intermediate-alias "
        "refusal created a staged or final artifact",
    )
