from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path
from typing import Any

from .fixture_payloads import (
    declare_account_request,
    expense_request,
    raw_transfer_request,
    sale_request,
)
from .models import ReleaseSmokeConfig, ReleaseSmokeFailure


def prepare_fixture_directories(config: ReleaseSmokeConfig) -> None:
    # The public key-generation contract requires the caller to supply safe parent directories.
    # The acceptance workflow therefore creates and secures its own artifact parents before it
    # asks the binary to create any secret or protected-book file.
    for path in [
        config.request_sale.local_path,
        config.request_expense.local_path,
        config.request_raw_journal.local_path,
        config.invalid_request.local_path,
        config.declare_bank_account.local_path,
        config.declare_expense_supplement.local_path,
        config.attestation_receipt.local_path,
        config.trial_balance_pdf.local_path,
        config.trial_balance_pdf_stderr_path,
    ]:
        path.parent.mkdir(parents=True, exist_ok=True)
    for directory in {
        config.book.local_path.parent,
        config.book_key.local_path.parent,
        config.attestation_founder_key.local_path.parent,
        config.backup_book.local_path.parent,
        config.backup_book_key.local_path.parent,
        config.restored_book.local_path.parent,
        config.restored_book_key.local_path.parent,
        config.replacement_book_key.local_path.parent,
    }:
        prepare_owner_only_directory(directory)


def prepare_owner_only_directory(directory: Path) -> None:
    checked_directory = Path(directory)
    checked_directory.mkdir(parents=True, exist_ok=True)
    if os.name == "posix":
        checked_directory.chmod(0o700)
    elif os.name == "nt":
        secure_windows_directory(checked_directory)


def secure_windows_directory(directory: Path) -> None:
    powershell = """
$directory = $args[0]
$owner = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = Get-Acl -LiteralPath $directory
$acl.SetAccessRuleProtection($true, $false)
$acl.Access | ForEach-Object { [void]$acl.RemoveAccessRuleSpecific($_) }
$acl.SetOwner($owner)
$acl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new($owner, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
Set-Acl -LiteralPath $directory -AclObject $acl
"""
    completed = subprocess.run(
        [
            "powershell.exe",
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            powershell,
            str(directory),
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    if completed.returncode != 0:
        details = completed.stderr.strip() or completed.stdout.strip() or "PowerShell failed"
        raise ReleaseSmokeFailure(
            f"could not prepare an owner-only release-smoke directory {directory}: {details}"
        )


def write_acceptance_fixtures(config: ReleaseSmokeConfig) -> None:
    request_prefix = config.request_prefix
    config.attestation_founder_passphrase.local_path.write_text(
        "release-smoke-founder-passphrase\n", encoding="utf-8"
    )
    if os.name == "posix":
        config.attestation_founder_passphrase.local_path.chmod(0o600)
    write_json(
        config.request_sale.local_path,
        sale_request(
            request_prefix=request_prefix,
            effective_date="2026-04-07",
            cash_account_code=config.starter_cash_account_code,
            revenue_account_code=config.starter_revenue_account_code,
            minor_units="1000",
            evidence_suffix="sale",
            command_suffix="sale",
            idempotency_suffix="idem-1",
            causation_suffix="cause-1",
        ),
    )
    write_json(
        config.request_expense.local_path,
        expense_request(
            request_prefix=request_prefix,
            effective_date="2026-04-08",
            expense_account_code=config.expense_supplement_account_code,
            cash_account_code=config.starter_cash_account_code,
            minor_units="400",
            evidence_suffix="expense",
            command_suffix="expense",
            idempotency_suffix="idem-2",
            causation_suffix="cause-2",
        ),
    )
    write_json(
        config.request_raw_journal.local_path,
        raw_transfer_request(
            request_prefix=request_prefix,
            effective_date="2026-04-08",
            source_account_code=config.starter_cash_account_code,
            destination_account_code=config.bank_account_code,
            minor_units="250",
            evidence_suffix="transfer",
            command_suffix="transfer",
            idempotency_suffix="idem-3",
            causation_suffix="cause-3",
        ),
    )
    write_json(
        config.invalid_request.local_path,
        declare_account_request(
            account_code="invalid-supplement",
            account_name="Invalid Supplement",
            account_type="ASSET",
            account_node_kind="POSTABLE",
            financial_position_line_classification="CURRENT_ASSET",
            cash_flow_asset_classification="CASH_AND_CASH_EQUIVALENT",
            nonsense_one="unexpected",
            nonsense_two="unexpected",
        ),
    )
    write_json(
        config.declare_bank_account.local_path,
        declare_account_request(
            account_code=config.bank_account_code,
            account_name=config.bank_account_name,
            account_type="ASSET",
            account_node_kind="POSTABLE",
            financial_position_line_classification="CURRENT_ASSET",
            cash_flow_asset_classification="CASH_AND_CASH_EQUIVALENT",
        ),
    )
    write_json(
        config.declare_expense_supplement.local_path,
        declare_account_request(
            account_code=config.expense_supplement_account_code,
            account_name=config.expense_supplement_account_name,
            account_type="EXPENSE",
            account_node_kind="POSTABLE",
            profit_and_loss_line_classification="OPERATING_EXPENSE",
        ),
    )


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
