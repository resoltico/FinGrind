from __future__ import annotations

from .cli import run_cli
from .models import ReleaseSmokeConfig
from .support import parse_json_output, payload_field, require, require_match


def verify_backup_restore_and_rollback_surfaces(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    print(f"{config.label}: verifying backup, restore, and rollback inspection")
    backup_payload = parse_json_output(
        run_cli(
            config,
            operation_ids["backupBook"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--backup-file-out",
            config.backup_book.argument,
            "--backup-book-key-file-out",
            config.backup_book_key.argument,
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
            "--backup-file",
            config.backup_book.argument,
            "--backup-book-key-file",
            config.backup_book_key.argument,
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

    restored_accounts_output = run_cli(
        config,
        operation_ids["listAccounts"],
        "--book-file",
        config.restored_book.argument,
        "--book-key-file",
        config.backup_book_key.argument,
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

    rollback_payload = parse_json_output(
        run_cli(
            config,
            operation_ids["inspectRekeyRollback"],
            "--book-file",
            config.book.argument,
            "--output",
            "json",
        ),
        f"{config.label} inspect-rekey-rollback output was not valid JSON",
    )
    require(
        rollback_payload.get("status") == "ok",
        f"{config.label} inspect-rekey-rollback did not report ok status",
    )
    require(
        payload_field(rollback_payload, "payload", "rollbackArtifacts") == [],
        f"{config.label} inspect-rekey-rollback reported unexpected rollback artifacts for a healthy book",
    )
