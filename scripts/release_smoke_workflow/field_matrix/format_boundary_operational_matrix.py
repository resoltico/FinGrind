"""Operational command matrix that must reject noncurrent protected-book formats."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_arguments import signing_credential_arguments
from ..cli import run_cli_allow_failure
from ..models import ReleaseSmokeConfig, SmokePath
from ..support import require
from .format_boundary_probe_execution import required_operation_id
from .format_boundary_refusals import (
    _require_unsupported_format_envelope,
    _write_operational_boundary_requests,
)


def require_operational_format_refusals(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, object],
    boundary_book: SmokePath,
    boundary_key: SmokePath,
    boundary_name: str,
    boundary_format: int,
    supported_format: int,
    error_exit_codes: Mapping[str, int],
) -> None:
    """Exercise read, report, verify, write, and plan paths against one rejected book."""
    expected_exit_code = error_exit_codes.get("unsupported-book-format-version")
    require(
        isinstance(expected_exit_code, int),
        f"{config.label} runtime contract did not publish unsupported-book-format-version exit semantics",
    )
    request_paths = _write_operational_boundary_requests(config, boundary_name)
    command_arguments = (
        ("list-accounts", required_operation_id(operation_ids, "listAccounts", config), ()),
        (
            "trial-balance",
            required_operation_id(operation_ids, "trialBalance", config),
            ("--effective-date-as-of", "2026-04-08"),
        ),
        ("verify-book", required_operation_id(operation_ids, "verifyBook", config), ()),
        (
            "preflight-entry",
            required_operation_id(operation_ids, "preflightEntry", config),
            ("--request-file", config.request_sale.argument),
        ),
        (
            "record-sale-settled",
            required_operation_id(operation_ids, "recordSaleSettled", config),
            ("--request-file", config.request_sale.argument, *signing_credential_arguments(config)),
        ),
        (
            "declare-account",
            required_operation_id(operation_ids, "declareAccount", config),
            (
                "--request-file",
                request_paths["declare-account"].argument,
                *signing_credential_arguments(config),
            ),
        ),
        (
            "execute-plan read-only",
            required_operation_id(operation_ids, "executePlan", config),
            ("--request-file", request_paths["read-only-plan"].argument),
        ),
        (
            "execute-plan mutating",
            required_operation_id(operation_ids, "executePlan", config),
            (
                "--request-file",
                request_paths["mutating-plan"].argument,
                *signing_credential_arguments(config),
            ),
        ),
    )
    for command_name, operation_id, extra_arguments in command_arguments:
        output, exit_code = run_cli_allow_failure(
            config,
            operation_id,
            "--book-file",
            boundary_book.argument,
            "--book-key-file",
            boundary_key.argument,
            *extra_arguments,
            "--output",
            "json",
        )
        _require_unsupported_format_envelope(
            config,
            output,
            exit_code,
            expected_exit_code,
            command_name,
            boundary_name,
            boundary_format,
            supported_format,
        )
