from __future__ import annotations

from .attestation_arguments import signing_credential_arguments
from .attestation_diagnostic_catalog import AttestationDiagnostic
from .attestation_receipt_security_checks import verify_receipt_trust_and_path_security
from .attestation_receipt_tamper_checks import verify_tampered_receipt_rejection
from .attestation_review_text_checks import verify_review_projections
from .cli import run_cli, run_cli_allow_failure
from .field_matrix.capabilities import CapabilityMatrix
from .field_matrix.receipt_artifact_assertions import (
    assert_exported_receipt_artifact,
    required_receipt_artifact,
)
from .models import ReleaseSmokeConfig
from .support import parse_json_output, require, require_match


def verify_attestation_inspection_and_receipt_artifacts(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
    attestation_verification_diagnostics: dict[str, dict[str, AttestationDiagnostic]],
    capability_matrix: CapabilityMatrix,
) -> None:
    print(f"{config.label}: verifying attestation inspection and receipt artifacts")
    for operation_key in (
        "verifyBook",
        "attestationReview",
        "exportAttestationReceipt",
        "verifyReceipt",
    ):
        operation = operation_ids[operation_key]
        require(
            operation in attestation_verification_diagnostics,
            f"{config.label} capabilities output did not publish attestation verification diagnostics for {operation}",
        )
    verification = parse_json_output(
        run_cli(
            config,
            operation_ids["verifyBook"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--output",
            "json",
        ),
        f"{config.label} verify-book output was not valid JSON",
    )
    review = parse_json_output(
        run_cli(
            config,
            operation_ids["attestationReview"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--output",
            "json",
        ),
        f"{config.label} attestation-review output was not valid JSON",
    )
    verify_review_projections(config, operation_ids, error_exit_codes, verification, review)
    receipt_operation = capability_matrix.operation(operation_ids["exportAttestationReceipt"])
    receipt_artifact = required_receipt_artifact(receipt_operation)
    receipt_export = parse_json_output(
        run_cli(
            config,
            operation_ids["exportAttestationReceipt"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--receipt-file",
            config.attestation_receipt.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
        ),
        f"{config.label} export-attestation-receipt output was not valid JSON",
    )
    assert_exported_receipt_artifact(
        config,
        receipt_operation,
        receipt_artifact,
        config.attestation_receipt,
        receipt_export,
        "export-attestation-receipt",
    )
    receipt_verification = parse_json_output(
        run_cli(
            config,
            operation_ids["verifyReceipt"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--receipt-file",
            config.attestation_receipt.argument,
            "--output",
            "json",
        ),
        f"{config.label} verify-receipt output was not valid JSON",
    )
    duplicate_export_output, duplicate_export_status = run_cli_allow_failure(
        config,
        operation_ids["exportAttestationReceipt"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--receipt-file",
        config.attestation_receipt.argument,
        *signing_credential_arguments(config),
        "--output",
        "json",
    )

    for operation, payload in (
        ("verify-book", verification),
        ("attestation-review", review),
        ("export-attestation-receipt", receipt_export),
        ("verify-receipt", receipt_verification),
    ):
        require(
            payload.get("status") == "ok",
            f"{config.label} {operation} did not report ok status",
        )
    require(
        config.attestation_receipt.local_path.is_file(),
        f"{config.label} export-attestation-receipt did not create the requested artifact",
    )
    verify_tampered_receipt_rejection(config, operation_ids, attestation_verification_diagnostics)
    verify_receipt_trust_and_path_security(config, operation_ids, error_exit_codes)
    require(
        duplicate_export_status != 0,
        f"{config.label} export-attestation-receipt overwrote an existing artifact",
    )
    require_match(
        duplicate_export_output,
        r'"code"[[:space:]]*:[[:space:]]*"artifact-output-already-exists"',
        f"{config.label} receipt no-clobber refusal did not report artifact-output-already-exists",
    )
