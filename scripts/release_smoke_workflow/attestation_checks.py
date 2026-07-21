from __future__ import annotations

from .attestation_arguments import signing_credential_arguments
from .cli import run_cli, run_cli_allow_failure
from .models import ReleaseSmokeConfig
from .support import parse_json_output, require, require_match


def verify_attestation_inspection_and_receipt_artifacts(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    print(f"{config.label}: verifying attestation inspection and receipt artifacts")
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
    require(
        duplicate_export_status != 0,
        f"{config.label} export-attestation-receipt overwrote an existing artifact",
    )
    require_match(
        duplicate_export_output,
        r'"code"[[:space:]]*:[[:space:]]*"artifact-output-already-exists"',
        f"{config.label} receipt no-clobber refusal did not report artifact-output-already-exists",
    )
