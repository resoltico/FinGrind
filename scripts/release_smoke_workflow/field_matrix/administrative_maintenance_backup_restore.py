"""Backup-to-standalone-restore maintenance workflow."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import replace

from ..attestation_arguments import signing_credential_arguments
from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeConfig, SmokePath
from .administrative_chain_state import (
    _require_restored_snapshot_branch,
    _verified_head,
    _verify_book,
)
from .administrative_maintenance_artifacts import _record_maintenance_artifact_response
from .administrative_models import AdministrativeWorld, JsonObject
from .administrative_operation_runner import _run_arguments_mutation, _run_operation
from .administrative_output import _artifact
from .administrative_paths import (
    _remove_source_book_for_standalone_restore,
    _require_absent,
    _require_nonempty_file,
    _validate_book_key_file,
    _world_path,
)
from .administrative_requests import _stable_uuid
from .capabilities import ArtifactCapability, OperationCapability


def _backup_and_restore(
    world: AdministrativeWorld,
    operations: Mapping[str, OperationCapability],
    backup_mode: str,
    restore_mode: str,
) -> tuple[SmokePath, SmokePath, ReleaseSmokeConfig]:
    backup_file = _world_path(world, world.artifact_directory / "retained-backup.fgat")
    backup_key = _world_path(world, world.artifact_directory / "retained-backup.key")
    _require_absent(backup_file.local_path, world.config, "backup file")
    _require_absent(backup_key.local_path, world.config, "backup key")
    backup_operation = operations["backup-book"]
    backup_artifacts = {
        _artifact(backup_operation, "backup-file"): backup_file,
        _artifact(backup_operation, "backup-key-file"): backup_key,
    }
    snapshot_head = _verified_head(world, "backup-book snapshot predecessor")

    def validate_backup_output(
        envelope: JsonObject | None,
        output: str,
        *,
        operation: OperationCapability = backup_operation,
        artifacts: Mapping[ArtifactCapability, SmokePath] = backup_artifacts,
        config: ReleaseSmokeConfig = world.config,
    ) -> None:
        _require_nonempty_file(backup_file.local_path, config, "backup-book artifact")
        _validate_book_key_file(backup_key.local_path, config, "backup-book key artifact")
        _record_maintenance_artifact_response(
            backup_mode,
            output,
            envelope,
            operation,
            artifacts,
            config,
            "backup-book capability mode",
        )

    _run_arguments_mutation(
        world,
        backup_operation,
        (
            "--backup-file",
            backup_file.argument,
            "--new-backup-key-file",
            backup_key.argument,
            "--backup-id",
            _stable_uuid(world, "backup"),
        ),
        backup_mode,
        "backup-book capability mode",
        post_output_assertion=validate_backup_output,
    )

    _remove_source_book_for_standalone_restore(world)
    restored_book = _world_path(world, world.artifact_directory / "restored.sqlite")
    restored_key = _world_path(world, world.artifact_directory / "restored.key")
    _require_absent(restored_book.local_path, world.config, "restored book")
    _require_absent(restored_key.local_path, world.config, "restored key")
    restore_operation = operations["restore-book"]
    restored_artifacts = {
        _artifact(restore_operation, "book-file"): restored_book,
        _artifact(restore_operation, "book-key-file"): restored_key,
    }
    restored_config = replace(world.config, book=restored_book, book_key=restored_key)

    def validate_restore_output(
        envelope: JsonObject | None,
        output: str,
        *,
        operation: OperationCapability = restore_operation,
        artifacts: Mapping[ArtifactCapability, SmokePath] = restored_artifacts,
        predecessor: VerifiedAttestationHead = snapshot_head,
        config: ReleaseSmokeConfig = world.config,
    ) -> None:
        _require_nonempty_file(restored_book.local_path, config, "restore-book artifact")
        _validate_book_key_file(restored_key.local_path, config, "restore-book key artifact")
        restored_head = _verify_book(
            world,
            restored_book,
            restored_key,
            "restore-book artifact verification",
        )
        _require_restored_snapshot_branch(predecessor, restored_head, config)
        _record_maintenance_artifact_response(
            restore_mode,
            output,
            envelope,
            operation,
            artifacts,
            config,
            "restore-book capability mode",
        )

    _run_operation(
        world,
        restore_operation,
        (
            "--book-file",
            restored_book.argument,
            "--new-book-key-file",
            restored_key.argument,
            "--backup-file",
            backup_file.argument,
            "--backup-key-file",
            backup_key.argument,
            *signing_credential_arguments(world.config),
        ),
        restore_mode,
        "restore-book capability mode",
        branch_predecessor=snapshot_head,
        after_head_config=restored_config,
        post_output_assertion=validate_restore_output,
    )
    return restored_book, restored_key, restored_config
