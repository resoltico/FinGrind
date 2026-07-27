"""Receipt symlink-component and selected-input refusal scenarios."""

from __future__ import annotations

from pathlib import Path

from .attestation_receipt_security_assertions import require_invalid_output_directory_refusal
from .attestation_receipt_security_commands import (
    export_receipt_allow_failure,
    verify_receipt_allow_failure,
)
from .attestation_receipt_security_symlinks import make_directory_symlink, make_file_symlink
from .models import ReleaseSmokeConfig, SmokePath
from .scenario_paths import smoke_path_from_local
from .support import parse_json_output, require


def verify_symlink_component_and_final_input_refusals(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
    root: Path,
    physical_root: Path,
    real_parent: Path,
    physical_receipt: SmokePath,
) -> None:
    """Proves every selected symlink component is refused without publication."""
    intermediate_alias = root / "intermediate-alias"
    make_directory_symlink(intermediate_alias, physical_root, config.label)
    aliased_real_parent = intermediate_alias / real_parent.name
    aliased_receipt = smoke_path_from_local(
        config,
        aliased_real_parent / physical_receipt.local_path.name,
    )
    intermediate_export = smoke_path_from_local(
        config,
        aliased_real_parent / "intermediate-alias-export.fgar",
    )
    output, exit_code = export_receipt_allow_failure(config, operation_ids, intermediate_export)
    require_invalid_output_directory_refusal(
        output,
        exit_code,
        config,
        error_exit_codes,
        intermediate_export,
        config.label,
        "receipt intermediate-alias output refusal",
    )
    require(
        not (real_parent / intermediate_export.local_path.name).exists(),
        f"{config.label} receipt intermediate-alias output refusal published an artifact",
    )
    _verify_receipt_input_refusal(
        config,
        operation_ids,
        aliased_receipt,
        "receipt intermediate-alias input refusal",
    )
    _verify_final_input_symlink_refusal(config, operation_ids, root, physical_receipt)


def _verify_final_input_symlink_refusal(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    root: Path,
    physical_receipt: SmokePath,
) -> None:
    selected_link = root / "selected-receipt-link.fgar"
    make_file_symlink(selected_link, physical_receipt.local_path, config.label)
    _verify_receipt_input_refusal(
        config,
        operation_ids,
        smoke_path_from_local(config, selected_link),
        "receipt selected-symlink refusal",
    )
    require(
        selected_link.is_symlink() and physical_receipt.local_path.is_file(),
        f"{config.label} receipt selected-symlink refusal changed the receipt source",
    )


def _verify_receipt_input_refusal(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    receipt_path: SmokePath,
    purpose: str,
) -> None:
    """Requires selected receipt paths with a symlink component to fail closed."""
    output, exit_code = verify_receipt_allow_failure(
        config,
        operation_ids,
        receipt_path,
    )
    envelope = parse_json_output(
        output,
        f"{config.label} {purpose} output was not valid JSON",
    )
    require(
        exit_code == 2
        and envelope.get("status") == "rejected"
        and envelope.get("code") == "receipt-artifact-invalid",
        f"{config.label} {purpose} did not report receipt-artifact-invalid",
    )
