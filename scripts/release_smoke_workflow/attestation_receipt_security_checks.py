"""Coordinates receipt trust-boundary release-smoke scenarios."""

from __future__ import annotations

import os

from .attestation_head_checks import verified_attestation_head
from .attestation_receipt_security_aliases import (
    verify_symlink_component_and_final_input_refusals,
)
from .attestation_receipt_security_output_refusals import verify_output_path_refusals
from .attestation_receipt_security_positive import (
    verify_canonical_receipt_export,
    verify_in_boundary_receipt,
)
from .fixtures import prepare_owner_only_directory
from .models import ReleaseSmokeConfig
from .support import require

_RECEIPT_SECURITY_DIRECTORY = "receipt-security"


def verify_receipt_trust_and_path_security(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
) -> None:
    """Proves receipt trust reporting and hostile-path refusals preserve the attestation head."""
    print(f"{config.label}: verifying receipt trust and path security")
    head_before = verified_attestation_head(
        config,
        operation_ids,
        "before receipt trust and path security checks",
    )
    root = config.work_root / _RECEIPT_SECURITY_DIRECTORY
    require(
        not root.exists(),
        f"{config.label} receipt security root already exists: {root}",
    )
    prepare_owner_only_directory(root)
    physical_root = root / "physical-root"
    prepare_owner_only_directory(physical_root)
    real_parent = physical_root / "real-parent"
    prepare_owner_only_directory(real_parent)

    physical_receipt = verify_canonical_receipt_export(config, operation_ids, real_parent)
    verify_in_boundary_receipt(config, operation_ids)
    if os.name == "posix":
        verify_symlink_component_and_final_input_refusals(
            config,
            operation_ids,
            error_exit_codes,
            root,
            physical_root,
            real_parent,
            physical_receipt,
        )
        verify_output_path_refusals(
            config,
            operation_ids,
            error_exit_codes,
            root,
            real_parent,
        )
    else:
        print(f"{config.label}: skipping POSIX-only receipt symlink attacks")
    require(
        verified_attestation_head(
            config,
            operation_ids,
            "after receipt trust and path security checks",
        )
        == head_before,
        f"{config.label} receipt trust and path security checks changed the attestation head",
    )
