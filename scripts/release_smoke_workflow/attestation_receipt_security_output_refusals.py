"""Hostile receipt output-path refusal scenarios."""

from __future__ import annotations

import stat
from pathlib import Path

from .attestation_receipt_security_assertions import (
    require_error,
    require_invalid_output_directory_refusal,
)
from .attestation_receipt_security_commands import export_receipt_allow_failure
from .attestation_receipt_security_symlinks import make_directory_symlink, make_file_symlink
from .fixtures import prepare_owner_only_directory
from .models import ReleaseSmokeConfig, ReleaseSmokeFailure
from .scenario_paths import smoke_path_from_local
from .support import require


def verify_output_path_refusals(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
    root: Path,
    real_parent: Path,
) -> None:
    """Requires every hostile receipt output path to fail before publication."""
    _verify_final_output_symlink_refusal(config, operation_ids, error_exit_codes, root)
    _verify_final_output_parent_symlink_refusal(
        config,
        operation_ids,
        error_exit_codes,
        root,
        real_parent,
    )
    _verify_nonprivate_output_parent_refusal(config, operation_ids, error_exit_codes, root)


def _verify_final_output_symlink_refusal(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
    root: Path,
) -> None:
    target = root / "existing-output-target.fgar"
    output_link = root / "existing-output-link.fgar"
    sentinel = b"existing receipt output sentinel\n"
    target.write_bytes(sentinel)
    make_file_symlink(output_link, target, config.label)
    output, exit_code = export_receipt_allow_failure(
        config,
        operation_ids,
        smoke_path_from_local(config, output_link),
    )
    require_error(
        output,
        exit_code,
        error_exit_codes["artifact-output-already-exists"],
        "artifact-output-already-exists",
        config.label,
        "receipt output symlink refusal",
    )
    require(
        output_link.is_symlink() and target.read_bytes() == sentinel,
        f"{config.label} receipt output symlink refusal changed the selected target",
    )


def _verify_final_output_parent_symlink_refusal(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
    root: Path,
    real_parent: Path,
) -> None:
    output_parent_link = root / "output-parent-link"
    make_directory_symlink(output_parent_link, real_parent, config.label)
    refused_path = output_parent_link / "must-not-publish.fgar"
    refused_receipt = smoke_path_from_local(config, refused_path)
    output, exit_code = export_receipt_allow_failure(config, operation_ids, refused_receipt)
    require_invalid_output_directory_refusal(
        output,
        exit_code,
        config,
        error_exit_codes,
        refused_receipt,
        config.label,
        "receipt output-parent symlink refusal",
    )
    require(
        not (real_parent / refused_path.name).exists(),
        f"{config.label} receipt output-parent symlink refusal published an artifact",
    )


def _verify_nonprivate_output_parent_refusal(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
    root: Path,
) -> None:
    """Proves staging never begins in a caller-selected nonprivate parent directory."""
    unsafe_parent = root / "group-or-other-accessible-output-parent"
    prepare_owner_only_directory(unsafe_parent)
    try:
        unsafe_parent.chmod(0o755)
        effective_mode = stat.S_IMODE(unsafe_parent.stat().st_mode)
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} could not prepare a nonprivate receipt output parent"
        ) from exc
    require(
        effective_mode & 0o077,
        f"{config.label} nonprivate receipt output parent did not retain group or other access",
    )
    refused_receipt = smoke_path_from_local(config, unsafe_parent / "must-not-publish.fgar")
    output, exit_code = export_receipt_allow_failure(config, operation_ids, refused_receipt)
    require_invalid_output_directory_refusal(
        output,
        exit_code,
        config,
        error_exit_codes,
        refused_receipt,
        config.label,
        "receipt nonprivate output-parent refusal",
    )
    require(
        not tuple(unsafe_parent.iterdir()),
        f"{config.label} receipt nonprivate output-parent refusal created a staged or final artifact",
    )
