"""CLI transport for receipt trust-boundary release-smoke scenarios."""

from __future__ import annotations

from .attestation_arguments import signing_credential_arguments
from .cli import run_cli, run_cli_allow_failure
from .models import ReleaseSmokeConfig, SmokePath
from .support import parse_json_output, required_mapping


def export_receipt(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    receipt_path: SmokePath,
) -> tuple[dict[str, object], dict[str, object]]:
    """Exports one receipt and returns its validated JSON envelope and payload."""
    envelope = parse_json_output(
        run_cli(
            config,
            operation_ids["exportAttestationReceipt"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--receipt-file",
            receipt_path.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
        ),
        f"{config.label} receipt export output was not valid JSON",
    )
    return dict(envelope), dict(required_mapping(envelope, "payload"))


def verify_receipt(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    receipt_path: SmokePath,
) -> tuple[dict[str, object], dict[str, object]]:
    """Verifies one receipt and returns its validated JSON envelope and payload."""
    envelope = parse_json_output(
        run_cli(
            config,
            operation_ids["verifyReceipt"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--receipt-file",
            receipt_path.argument,
            "--output",
            "json",
        ),
        f"{config.label} receipt verification output was not valid JSON",
    )
    return dict(envelope), dict(required_mapping(envelope, "payload"))


def export_receipt_allow_failure(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    receipt_path: SmokePath,
) -> tuple[str, int]:
    """Attempts receipt export while retaining its public error envelope."""
    return run_cli_allow_failure(
        config,
        operation_ids["exportAttestationReceipt"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--receipt-file",
        receipt_path.argument,
        *signing_credential_arguments(config),
        "--output",
        "json",
    )


def verify_receipt_allow_failure(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    receipt_path: SmokePath,
) -> tuple[str, int]:
    """Attempts receipt verification while retaining its public error envelope."""
    return run_cli_allow_failure(
        config,
        operation_ids["verifyReceipt"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--receipt-file",
        receipt_path.argument,
        "--output",
        "json",
    )
