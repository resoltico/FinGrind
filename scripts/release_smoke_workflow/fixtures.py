from __future__ import annotations

import csv
import os
import re
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
    system_directory = _windows_system_directory(directory)
    owner_sid = _current_windows_token_sid(system_directory, directory)
    _run_windows_directory_security_command(
        [
            str(system_directory / "icacls.exe"),
            str(directory),
            "/inheritance:r",
            "/grant:r",
            f"*{owner_sid}:(OI)(CI)F",
            "/c",
        ],
        directory,
        "grant the current Windows owner full control",
    )


def _windows_system_directory(directory: Path) -> Path:
    system_root = os.environ.get("SystemRoot")
    if not system_root:
        raise ReleaseSmokeFailure(
            "could not prepare an owner-only release-smoke directory "
            f"{directory}: Windows SystemRoot is not set"
        )
    return Path(system_root) / "System32"


def _current_windows_token_sid(system_directory: Path, directory: Path) -> str:
    completed = _run_windows_directory_security_command(
        [str(system_directory / "whoami.exe"), "/user", "/fo", "csv", "/nh"],
        directory,
        "resolve the current Windows owner",
    )
    records = list(csv.reader(completed.stdout.splitlines()))
    if len(records) != 1 or len(records[0]) != 2:
        raise ReleaseSmokeFailure(
            "could not resolve the current Windows owner for owner-only release-smoke directory "
            f"{directory}: whoami returned an unexpected user record"
        )
    owner_sid = records[0][1].strip()
    if re.fullmatch(r"S-\d+(?:-\d+)+", owner_sid) is None:
        raise ReleaseSmokeFailure(
            "could not resolve the current Windows owner for owner-only release-smoke directory "
            f"{directory}: whoami returned an invalid SID"
        )
    return owner_sid


def _run_windows_directory_security_command(
    command: list[str], directory: Path, action: str
) -> subprocess.CompletedProcess[str]:
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"could not {action} for owner-only release-smoke directory {directory}: {exc}"
        ) from exc
    if completed.returncode == 0:
        return completed
    details = completed.stderr.strip() or completed.stdout.strip() or "Windows command failed"
    raise ReleaseSmokeFailure(
        f"could not {action} for owner-only release-smoke directory {directory}: {details}"
    )
