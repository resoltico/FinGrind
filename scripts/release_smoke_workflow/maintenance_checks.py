from __future__ import annotations

from .attestation_arguments import signing_credential_arguments
from .cli import run_cli, run_cli_allow_failure
from .maintenance_source_identity_checks import verify_source_artifact_identity_duplicate_refusal
from .models import ReleaseSmokeConfig
from .support import parse_json_output, require, require_match, require_no_match


def verify_backup_restore_surfaces(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
) -> None:
    print(f"{config.label}: verifying backup and restore")
    verify_source_artifact_identity_duplicate_refusal(config, operation_ids, error_exit_codes)
    backup_payload = parse_json_output(
        run_cli(
            config,
            operation_ids["backupBook"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--backup-file",
            config.backup_book.argument,
            "--new-backup-key-file",
            config.backup_book_key.argument,
            "--backup-id",
            config.backup_id,
            *signing_credential_arguments(config),
            "--output",
            "json",
        ),
        f"{config.label} backup-book output was not valid JSON",
    )
    require(
        backup_payload.get("status") == "ok",
        f"{config.label} backup-book did not report ok status",
    )
    require(
        config.backup_book.local_path.is_file(),
        f"{config.label} backup-book did not create the requested backup file",
    )
    require(
        config.backup_book_key.local_path.is_file(),
        f"{config.label} backup-book did not create the requested backup key file",
    )

    restore_payload = parse_json_output(
        run_cli(
            config,
            operation_ids["restoreBook"],
            "--book-file",
            config.restored_book.argument,
            "--new-book-key-file",
            config.restored_book_key.argument,
            "--backup-file",
            config.backup_book.argument,
            "--backup-key-file",
            config.backup_book_key.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
        ),
        f"{config.label} restore-book output was not valid JSON",
    )
    require(
        restore_payload.get("status") == "ok",
        f"{config.label} restore-book did not report ok status",
    )
    require(
        config.restored_book.local_path.is_file(),
        f"{config.label} restore-book did not create the requested live book file",
    )
    require(
        config.restored_book_key.local_path.is_file(),
        f"{config.label} restore-book did not create the requested live book key file",
    )

    restored_accounts_output = run_cli(
        config,
        operation_ids["listAccounts"],
        "--book-file",
        config.restored_book.argument,
        "--book-key-file",
        config.restored_book_key.argument,
        "--output",
        "json",
    )
    require_match(
        restored_accounts_output,
        rf'"accountCode"[[:space:]]*:[[:space:]]*"{config.starter_cash_account_code}"',
        f"{config.label} restored book did not expose the seeded cash account",
    )
    require_match(
        restored_accounts_output,
        rf'"accountCode"[[:space:]]*:[[:space:]]*"{config.expense_supplement_account_code}"',
        f"{config.label} restored book did not preserve the declared expense supplement",
    )
    wrong_key_output, wrong_key_status = run_cli_allow_failure(
        config,
        operation_ids["listAccounts"],
        "--book-file",
        config.restored_book.argument,
        "--book-key-file",
        config.backup_book_key.argument,
        "--output",
        "json",
    )
    require(
        wrong_key_status == error_exit_codes["protected-book-verification-failed"],
        f"{config.label} restored book accepted the backup key or exited with the wrong code",
    )
    require_match(
        wrong_key_output,
        r'"code"[[:space:]]*:[[:space:]]*"protected-book-verification-failed"',
        f"{config.label} restored book wrong-key path did not report protected-book-verification-failed",
    )
    require_no_match(
        wrong_key_output,
        r"SQLITE_NOTADB",
        f"{config.label} restored book wrong-key path leaked the SQLite NOTADB storage symptom",
    )
