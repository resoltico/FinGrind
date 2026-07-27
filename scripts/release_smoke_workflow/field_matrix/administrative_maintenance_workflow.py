"""Backup, standalone restore, and sequential rekey capability workflow."""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig
from .administrative_maintenance_backup_restore import _backup_and_restore
from .administrative_maintenance_rekey import _rekey_restored_book
from .administrative_modes import _modes_for, _supported_mode
from .administrative_world_bootstrap import _new_world
from .capabilities import OperationCapability


def _verify_maintenance_modes(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
    operations: Mapping[str, OperationCapability],
) -> None:
    group = (
        operations["backup-book"],
        operations["restore-book"],
        operations["rekey-book"],
    )
    for output_mode in _modes_for(*group):
        world = _new_world(config, operation_ids, operations, "maintenance", output_mode)
        backup_mode = _supported_mode(operations["backup-book"], output_mode)
        restore_mode = _supported_mode(operations["restore-book"], output_mode)
        rekey_mode = _supported_mode(operations["rekey-book"], output_mode)
        restored_book, restored_key, restored_config = _backup_and_restore(
            world,
            operations,
            backup_mode,
            restore_mode,
        )
        _rekey_restored_book(
            world,
            operations,
            rekey_mode,
            restored_book,
            restored_key,
            restored_config,
        )
