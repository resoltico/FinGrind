"""Positive and in-boundary receipt trust-boundary smoke scenarios."""

from __future__ import annotations

import os
from dataclasses import replace
from pathlib import Path

from .attestation_receipt_security_assertions import require_successful_receipt_path
from .attestation_receipt_security_commands import export_receipt, verify_receipt
from .field_matrix.receipt_artifact_assertions import canonical_receipt_reported_path
from .models import ReleaseSmokeConfig, SmokePath
from .scenario_paths import smoke_path_from_local
from .support import require

_RECEIPT_NOT_INDEPENDENT = "receipt-not-independent"


def verify_canonical_receipt_export(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    real_parent: Path,
) -> SmokePath:
    """Exports through a dot-spelled path and requires the physical canonical path."""
    physical_receipt = smoke_path_from_local(config, real_parent / "positive.fgar")
    exported_receipt = replace(
        physical_receipt,
        argument=_dot_spelling(physical_receipt.argument),
    )
    expected_physical_path = canonical_receipt_reported_path(config, physical_receipt)
    export_envelope, export_payload = export_receipt(config, operation_ids, exported_receipt)
    require_successful_receipt_path(
        export_envelope,
        export_payload,
        expected_physical_path,
        [],
        "warnings",
        config.label,
        "positive receipt export",
    )
    require(
        physical_receipt.local_path.is_file() and bool(physical_receipt.local_path.read_bytes()),
        f"{config.label} positive receipt export did not create a physical receipt artifact",
    )
    return physical_receipt


def verify_in_boundary_receipt(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    """Requires a receipt inside the book boundary to disclose its lack of independence."""
    in_boundary_receipt = smoke_path_from_local(
        config,
        config.book.local_path.parent / "receipt-security-in-book-parent.fgar",
    )
    expected_physical_path = canonical_receipt_reported_path(config, in_boundary_receipt)
    export_envelope, export_payload = export_receipt(config, operation_ids, in_boundary_receipt)
    require_successful_receipt_path(
        export_envelope,
        export_payload,
        expected_physical_path,
        [_RECEIPT_NOT_INDEPENDENT],
        "warnings",
        config.label,
        "in-boundary receipt export",
    )
    verification_envelope, verification_payload = verify_receipt(
        config,
        operation_ids,
        in_boundary_receipt,
    )
    require_successful_receipt_path(
        verification_envelope,
        verification_payload,
        expected_physical_path,
        [_RECEIPT_NOT_INDEPENDENT],
        "findings",
        config.label,
        "in-boundary receipt verification",
    )


def _dot_spelling(argument: str) -> str:
    argument_path = Path(argument)
    return f"{argument_path.parent}{os.sep}.{os.sep}{argument_path.name}"
