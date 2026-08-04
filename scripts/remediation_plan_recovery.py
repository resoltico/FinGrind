"""Recovery owner for an interrupted Ledger-1 public-projection installation."""

from __future__ import annotations

import fcntl
import os
import shutil
from contextlib import contextmanager
from pathlib import Path

from remediation_plan_support import RemediationError, canonical_json, relative_path
from remediation_plan_validation import validate_public_projection

STAGE_PREFIX = "remediation-plan-"
TARGETS = ("remediation", "requirements-remediation-plan.txt")


@contextmanager
def _exclusive_lock(root: Path):
    lock_path = root / "tmp" / "remediation-plan.lock"
    lock_path.parent.mkdir(exist_ok=True)
    with lock_path.open("a+b") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def _fsync_path(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _stage_root(root: Path, stage: str) -> Path:
    safe_stage = relative_path(stage)
    if not safe_stage.startswith("tmp/"):
        raise RemediationError("recovery journal has an unsafe staging path")
    stage_root = root / safe_stage
    if stage_root.parent != root / "tmp":
        raise RemediationError("recovery journal staging path has an unexpected parent")
    return stage_root


def recover(root: Path, operation_id: str) -> None:
    """Resume the only safe outcome for a durable journal left by an interrupted operation."""
    journal = root / "tmp" / f"{STAGE_PREFIX}{operation_id}.json"
    value = canonical_json(journal)
    if not isinstance(value, dict) or value.get("operationId") != operation_id:
        raise RemediationError("recovery journal identity does not match the requested operation")
    stage = value.get("stage")
    if not isinstance(stage, str):
        raise RemediationError("recovery journal has an unsafe staging path")
    stage_root = _stage_root(root, stage)
    backups = stage_root / "backups"
    targets = value.get("targets")
    if not isinstance(targets, list) or tuple(targets) != TARGETS:
        raise RemediationError("recovery journal has an unexpected target set")
    with _exclusive_lock(root):
        for name in TARGETS:
            target = root / name
            backup = backups / name
            staged = stage_root / "targets" / name
            if target.exists() and backup.exists():
                if backup.is_dir():
                    shutil.rmtree(backup)
                else:
                    backup.unlink()
            elif not target.exists() and staged.exists():
                os.replace(staged, target)
            elif not target.exists() and backup.exists():
                os.replace(backup, target)
            elif not target.exists():
                raise RemediationError(f"recovery has no safe source for {name}")
        if backups.exists():
            shutil.rmtree(backups)
        if stage_root.exists():
            shutil.rmtree(stage_root)
        journal.unlink()
        _fsync_path(root / "tmp")
    validate_public_projection(root)
