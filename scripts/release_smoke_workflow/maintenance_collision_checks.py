"""No-clobber maintenance collision evidence for release smoke."""

from __future__ import annotations

from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

from .attestation_arguments import signing_credential_arguments
from .attestation_head_checks import verified_attestation_head
from .cli import run_cli_allow_failure
from .fixtures import prepare_owner_only_directory
from .maintenance_collision_assertions import require_maintenance_collision_rejection
from .models import ReleaseSmokeConfig
from .scenario_paths import smoke_path_from_local
from .support import require

_COLLISION_DIRECTORY = "maintenance-collisions"


def verify_maintenance_collision_refusals(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    """Prove maintenance commands refuse occupied targets without side effects."""
    print(f"{config.label}: verifying maintenance target collision refusals")
    root = config.work_root / _COLLISION_DIRECTORY
    require(
        not root.exists(),
        f"{config.label} maintenance collision root already exists: {root}",
    )
    prepare_owner_only_directory(root)
    head_before = verified_attestation_head(
        config,
        operation_ids,
        "before maintenance target collision refusals",
    )
    _verify_backup_destination_collision(config, operation_ids, root)
    _verify_backup_key_collision(config, operation_ids, root)
    _verify_restore_destination_collision(config, operation_ids, root)
    _verify_rekey_key_collision(config, operation_ids, root)
    require(
        verified_attestation_head(
            config,
            operation_ids,
            "after maintenance target collision refusals",
        )
        == head_before,
        f"{config.label} maintenance target collision refusal changed the attestation head",
    )


def _verify_backup_destination_collision(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    root: Path,
) -> None:
    occupied_backup = smoke_path_from_local(config, root / "occupied-backup.sqlite")
    absent_backup_key = smoke_path_from_local(config, root / "absent-backup.key")
    sentinel = b"occupied backup destination sentinel\n"
    occupied_backup.local_path.write_bytes(sentinel)
    output, exit_code = run_cli_allow_failure(
        config,
        operation_ids["backupBook"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--backup-file",
        occupied_backup.argument,
        "--new-backup-key-file",
        absent_backup_key.argument,
        "--backup-id",
        _backup_id(config, "occupied-backup-file"),
        *signing_credential_arguments(config),
        "--output",
        "json",
    )
    require_maintenance_collision_rejection(
        output,
        exit_code,
        "backup-destination-already-exists",
        config.label,
        "backup-book occupied backup destination",
    )
    require(
        occupied_backup.local_path.read_bytes() == sentinel
        and not absent_backup_key.local_path.exists(),
        f"{config.label} backup-book collision rewrote a destination or created a key",
    )


def _verify_backup_key_collision(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    root: Path,
) -> None:
    absent_backup = smoke_path_from_local(config, root / "absent-backup.sqlite")
    occupied_backup_key = smoke_path_from_local(config, root / "occupied-backup.key")
    sentinel = b"occupied backup key sentinel\n"
    occupied_backup_key.local_path.write_bytes(sentinel)
    output, exit_code = run_cli_allow_failure(
        config,
        operation_ids["backupBook"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--backup-file",
        absent_backup.argument,
        "--new-backup-key-file",
        occupied_backup_key.argument,
        "--backup-id",
        _backup_id(config, "occupied-backup-key"),
        *signing_credential_arguments(config),
        "--output",
        "json",
    )
    require_maintenance_collision_rejection(
        output,
        exit_code,
        "secret-target-occupied",
        config.label,
        "backup-book occupied backup key",
    )
    require(
        occupied_backup_key.local_path.read_bytes() == sentinel
        and not absent_backup.local_path.exists(),
        f"{config.label} backup-book key collision rewrote a key or created a backup",
    )


def _verify_restore_destination_collision(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    root: Path,
) -> None:
    occupied_book = smoke_path_from_local(config, root / "occupied-restored.sqlite")
    absent_book_key = smoke_path_from_local(config, root / "absent-restored.key")
    sentinel = b"occupied restore destination sentinel\n"
    occupied_book.local_path.write_bytes(sentinel)
    output, exit_code = run_cli_allow_failure(
        config,
        operation_ids["restoreBook"],
        "--book-file",
        occupied_book.argument,
        "--new-book-key-file",
        absent_book_key.argument,
        "--backup-file",
        config.backup_book.argument,
        "--backup-key-file",
        config.backup_book_key.argument,
        *signing_credential_arguments(config),
        "--output",
        "json",
    )
    require_maintenance_collision_rejection(
        output,
        exit_code,
        "book-destination-occupied",
        config.label,
        "restore-book occupied destination",
    )
    require(
        occupied_book.local_path.read_bytes() == sentinel
        and not absent_book_key.local_path.exists(),
        f"{config.label} restore-book collision rewrote a destination or created a key",
    )


def _verify_rekey_key_collision(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    root: Path,
) -> None:
    occupied_key = smoke_path_from_local(config, root / "occupied-rekey.key")
    sentinel = b"occupied rekey destination sentinel\n"
    occupied_key.local_path.write_bytes(sentinel)
    output, exit_code = run_cli_allow_failure(
        config,
        operation_ids["rekeyBook"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--new-book-key-file",
        occupied_key.argument,
        *signing_credential_arguments(config),
        "--output",
        "json",
    )
    require_maintenance_collision_rejection(
        output,
        exit_code,
        "secret-target-occupied",
        config.label,
        "rekey-book occupied replacement key",
    )
    require(
        occupied_key.local_path.read_bytes() == sentinel,
        f"{config.label} rekey-book collision rewrote the existing replacement key",
    )


def _backup_id(config: ReleaseSmokeConfig, suffix: str) -> str:
    return str(uuid5(NAMESPACE_URL, f"fingrind-release-smoke:{config.request_prefix}:{suffix}"))
