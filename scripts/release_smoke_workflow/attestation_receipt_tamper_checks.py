from __future__ import annotations

from .attestation_diagnostic_catalog import (
    AttestationDiagnostic,
    required_verification_diagnostic,
)
from .attestation_diagnostic_checks import require_exact_rejected_diagnostic
from .attestation_head_checks import verified_attestation_head
from .cli import run_cli
from .models import ReleaseSmokeConfig
from .scenario_paths import sibling_smoke_path
from .support import parse_json_output, require


def verify_tampered_receipt_rejection(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    attestation_verification_diagnostics: dict[str, dict[str, AttestationDiagnostic]],
) -> None:
    verify_receipt_operation = operation_ids["verifyReceipt"]
    signature_invalid = required_verification_diagnostic(
        attestation_verification_diagnostics,
        verify_receipt_operation,
        "attestation-signature-invalid",
        f"{config.label} capabilities output",
    )
    tampered_receipt = sibling_smoke_path(
        config.attestation_receipt, "attestation-receipt-tampered.fgr"
    )
    original_receipt = config.attestation_receipt.local_path.read_bytes()
    require(original_receipt, f"{config.label} exported an empty attestation receipt")
    require(
        not tampered_receipt.local_path.exists(),
        f"{config.label} tampered receipt target unexpectedly already exists",
    )
    tampered_receipt.local_path.write_bytes(
        original_receipt[:-1] + bytes([original_receipt[-1] ^ 0x01])
    )

    head_before = verified_attestation_head(
        config, operation_ids, "before tampered receipt verification"
    )
    require_exact_rejected_diagnostic(
        config,
        (
            verify_receipt_operation,
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--receipt-file",
            tampered_receipt.argument,
        ),
        signature_invalid.code,
        signature_invalid.message,
        signature_invalid.hint,
        f"{config.label} tampered receipt verification",
    )
    require(
        verified_attestation_head(config, operation_ids, "after tampered receipt verification")
        == head_before,
        f"{config.label} tampered receipt verification changed the verified attestation head",
    )
    _require_original_receipt_still_valid(config, operation_ids)


def _require_original_receipt_still_valid(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    envelope = parse_json_output(
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
        f"{config.label} original receipt re-verification output was not valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} original receipt did not remain valid after tampered receipt verification",
    )
