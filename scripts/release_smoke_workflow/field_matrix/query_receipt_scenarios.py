"""Receipt export/verification workflow for the query matrix."""

from __future__ import annotations

from ..attestation_arguments import signing_credential_arguments
from ..cli import run_cli
from ..models import ReleaseSmokeConfig, SmokePath
from ..scenario_paths import sibling_smoke_path
from ..support import parse_json_output, require
from .capabilities import OperationCapability
from .context import record_verified_artifact
from .invocation import invoke_all_advertised_modes, validate_and_record_output_mode
from .query_models import AttestationHeadFacts, ReceiptFacts
from .query_receipt_semantics import (
    _export_receipt_mode_assertion,
    _verified_receipt_facts_from_payload,
    _verify_receipt_mode_assertion,
)
from .query_response_support import _success_payload
from .query_runtime_facts import _book_access_arguments
from .receipt_artifact_assertions import assert_exported_receipt_artifact, required_receipt_artifact


def _export_matrix_receipts(
    config: ReleaseSmokeConfig,
    operation: OperationCapability,
    verification_operation: OperationCapability,
    attestation_head: AttestationHeadFacts,
) -> dict[str, ReceiptFacts]:
    receipt_artifact = required_receipt_artifact(operation)
    require(
        "json" in verification_operation.output_modes,
        f"field-matrix {verification_operation.operation_id} must advertise JSON receipt verification",
    )
    receipt_facts_by_mode: dict[str, ReceiptFacts] = {}
    for output_mode in operation.output_modes:
        receipt_path = sibling_smoke_path(
            config.attestation_receipt,
            f"field-matrix-attestation-receipt-{output_mode}.fgar",
        )
        require(
            not receipt_path.local_path.exists(),
            f"{config.label} field-matrix receipt path already exists: {receipt_path.local_path}",
        )
        output = run_cli(
            config,
            operation.operation_id,
            *_book_access_arguments(
                config,
                "--receipt-file",
                receipt_path.argument,
                *signing_credential_arguments(config),
            ),
            "--output",
            output_mode,
        )
        if output_mode == "json":
            envelope = parse_json_output(
                output,
                f"{config.label} field-matrix {operation.operation_id}[json] did not emit valid JSON",
            )
            assert_exported_receipt_artifact(
                config,
                operation,
                receipt_artifact,
                receipt_path,
                envelope,
                f"field-matrix {operation.operation_id}[json]",
            )
        require(
            receipt_path.local_path.is_file() and bool(receipt_path.local_path.read_bytes()),
            f"{config.label} field-matrix {operation.operation_id}[{output_mode}] did not create a receipt",
        )
        receipt_facts = _verify_exported_receipt(config, verification_operation, receipt_path)
        require(
            receipt_facts.book_id == attestation_head.book_id
            and receipt_facts.operation_order == attestation_head.operation_order
            and receipt_facts.operation_head == attestation_head.operation_head,
            f"{config.label} field-matrix {operation.operation_id}[{output_mode}] exported "
            "a receipt for an unexpected attestation head",
        )
        validate_and_record_output_mode(
            operation,
            output_mode,
            output,
            f"field-matrix {operation.operation_id}[{output_mode}]",
            _export_receipt_mode_assertion(config, receipt_facts, attestation_head),
        )
        if output_mode == "json":
            record_verified_artifact(operation.operation_id, receipt_artifact)
        receipt_facts_by_mode[output_mode] = receipt_facts
    return receipt_facts_by_mode


def _verify_receipts(
    config: ReleaseSmokeConfig,
    operation: OperationCapability,
    receipt_facts_by_mode: dict[str, ReceiptFacts],
) -> None:
    require(
        receipt_facts_by_mode,
        f"{config.label} field-matrix cannot verify a receipt because no receipt mode succeeded",
    )
    receipt_facts = next(iter(receipt_facts_by_mode.values()))
    invoke_all_advertised_modes(
        config,
        operation,
        lambda _mode: _book_access_arguments(
            config,
            "--receipt-file",
            receipt_facts.receipt_path.argument,
        ),
        "attestation receipt verification matrix",
        _verify_receipt_mode_assertion(config, receipt_facts),
    )


def _verify_exported_receipt(
    config: ReleaseSmokeConfig,
    operation: OperationCapability,
    receipt_path: SmokePath,
) -> ReceiptFacts:
    output = run_cli(
        config,
        operation.operation_id,
        *_book_access_arguments(
            config,
            "--receipt-file",
            receipt_path.argument,
        ),
        "--output",
        "json",
    )
    payload = _success_payload(config, operation.operation_id, output)
    return _verified_receipt_facts_from_payload(
        receipt_path,
        payload,
        f"{config.label} field-matrix verify-receipt[json]",
    )
