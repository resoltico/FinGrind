from __future__ import annotations

import os
import subprocess
from pathlib import Path

from .models import ReleaseSmokeConfig, ReleaseSmokeFailure, ReleaseSmokeScenario
from .private_workspace_ancestry import private_workspace_ancestors


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
    private_artifact_parents = {
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
    }
    for directory in private_workspace_ancestors(config.work_root, private_artifact_parents):
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


def prepare_owner_only_file(file_path: Path) -> None:
    """Normalize one existing regular secret file to the owner-only artifact contract."""
    checked_file = Path(file_path)
    if not checked_file.is_file() or checked_file.is_symlink():
        raise ReleaseSmokeFailure(
            "owner-only release-smoke file must be one existing regular non-symlink file: "
            + str(checked_file)
        )
    if os.name == "posix":
        checked_file.chmod(0o600)
    elif os.name == "nt":
        secure_windows_file(checked_file)


def secure_windows_directory(directory: Path) -> None:
    power_shell_executable = _release_smoke_power_shell_executable(directory)
    security_script = _windows_owner_only_directory_script(directory)
    _run_windows_directory_security_command(
        [
            str(power_shell_executable),
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "RemoteSigned",
            "-File",
            str(security_script),
            str(directory),
        ],
        directory,
        "apply the owner-only Windows directory security descriptor",
    )


def secure_windows_file(file_path: Path) -> None:
    power_shell_executable = _release_smoke_power_shell_executable(file_path)
    security_script = _windows_owner_only_file_script(file_path)
    _run_windows_file_security_command(
        [
            str(power_shell_executable),
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "RemoteSigned",
            "-File",
            str(security_script),
            str(file_path),
        ],
        file_path,
        "apply the owner-only Windows file security descriptor",
    )


def _release_smoke_power_shell_executable(directory: Path) -> Path:
    configured_path = os.environ.get("FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE")
    if not configured_path:
        raise ReleaseSmokeFailure(
            "could not prepare an owner-only release-smoke directory "
            f"{directory}: FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE is not set"
        )
    executable = Path(configured_path)
    if not executable.is_absolute() or not executable.is_file():
        raise ReleaseSmokeFailure(
            "could not prepare an owner-only release-smoke directory "
            f"{directory}: FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE must name one "
            f"absolute executable file, got {executable}"
        )
    return executable


def _windows_owner_only_directory_script(directory: Path) -> Path:
    script = Path(__file__).resolve().parent.parent / "secure-windows-owner-only-directory.ps1"
    if not script.is_file():
        raise ReleaseSmokeFailure(
            "could not prepare an owner-only release-smoke directory "
            f"{directory}: missing Windows owner-only directory script {script}"
        )
    return script


def _windows_owner_only_file_script(file_path: Path) -> Path:
    script = Path(__file__).resolve().parent.parent / "secure-windows-owner-only-file.ps1"
    if not script.is_file():
        raise ReleaseSmokeFailure(
            "could not prepare an owner-only release-smoke file "
            f"{file_path}: missing Windows owner-only file script {script}"
        )
    return script


def _run_windows_directory_security_command(
    command: list[str], directory: Path, action: str
) -> subprocess.CompletedProcess[str]:
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="strict",
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


def _run_windows_file_security_command(
    command: list[str], file_path: Path, action: str
) -> subprocess.CompletedProcess[str]:
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="strict",
        )
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"could not {action} for owner-only release-smoke file {file_path}: {exc}"
        ) from exc
    if completed.returncode == 0:
        return completed
    details = completed.stderr.strip() or completed.stdout.strip() or "Windows command failed"
    raise ReleaseSmokeFailure(
        f"could not {action} for owner-only release-smoke file {file_path}: {details}"
    )
