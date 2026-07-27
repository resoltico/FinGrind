from __future__ import annotations

import os
import subprocess
from pathlib import Path

from .models import ReleaseSmokeConfig, ReleaseSmokeFailure, ReleaseSmokeScenario


def prepare_fixture_directories(config: ReleaseSmokeConfig | ReleaseSmokeScenario) -> None:
    # Public artifact publication requires caller-supplied safe parents. The acceptance workflow
    # therefore creates and secures its own key, protected-book, receipt, and report parents
    # before it asks the binary to publish any artifact.
    require_fresh_work_root(config.work_root)
    for path in [
        config.request_sale.local_path,
        config.request_expense.local_path,
        config.request_taxed_sale.local_path,
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
        config.attestation_receipt.local_path.parent,
        config.trial_balance_pdf.local_path.parent,
    }:
        prepare_owner_only_directory(directory)


def require_fresh_work_root(work_root: Path) -> None:
    """Reject a reused release-smoke root before it can overwrite a fixture or artifact."""
    checked_work_root = Path(work_root)
    if not checked_work_root.is_absolute() or not checked_work_root.is_dir():
        raise ReleaseSmokeFailure(
            "release-smoke work root must be an existing absolute directory before fixture creation: "
            + str(checked_work_root)
        )
    try:
        entries = tuple(checked_work_root.iterdir())
    except OSError as exc:
        raise ReleaseSmokeFailure(
            "could not inspect release-smoke work root before fixture creation: "
            + str(checked_work_root)
        ) from exc
    if entries:
        entry_names = ", ".join(sorted(entry.name for entry in entries)[:5])
        suffix = "" if len(entries) <= 5 else ", ..."
        raise ReleaseSmokeFailure(
            "release-smoke work root must be fresh and empty before fixture creation; "
            f"found {entry_names}{suffix} in {checked_work_root}"
        )


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
