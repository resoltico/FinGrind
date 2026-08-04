"""Projection rendering and crash-recoverable installation for Ledger-1 public artifacts."""

from __future__ import annotations

import fcntl
import os
import shutil
import tempfile
import uuid
from contextlib import contextmanager
from pathlib import Path

from remediation_plan_support import (
    JsonValue,
    RemediationError,
    canonical_bytes,
    canonical_json,
    manifest_index,
    write_json,
)
from remediation_plan_validation import (
    PLAN_CATEGORIES,
    validate_authority,
    validate_public_projection,
)

TOOL_VERSION = "1.0.0"
STAGE_PREFIX = "remediation-plan-"


def _write_stage_json(path: Path, value: JsonValue) -> None:
    write_json(path, value)
    path.chmod(0o600)


def _fsync_path(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _fsync_tree(root: Path) -> None:
    for path in sorted(root.rglob("*")):
        if path.is_file():
            _fsync_path(path)
    for path in sorted((item for item in root.rglob("*") if item.is_dir()), reverse=True):
        _fsync_path(path)
    _fsync_path(root)


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


def _source_public_tree(authority_root: Path, stage_root: Path) -> None:
    ledger = authority_root / "ledger" / "v10.8.2"
    source = ledger / "reference" / "public"
    if not source.is_dir():
        raise RemediationError("private authority lacks the sealed public reference tree")
    destination = stage_root / "remediation"
    for source_path in sorted(source.rglob("*")):
        if source_path.is_dir():
            continue
        relative = source_path.relative_to(source)
        target = destination / relative
        if source_path.suffix == ".json":
            value = canonical_json(source_path)
            write_json(target, _remap_source_value(value))
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source_path.read_bytes())
            target.chmod(0o644)
    receipt = canonical_json(authority_root / "authority" / "p0-projection-receipt.json")
    write_json(destination / "projection-receipt.json", receipt)
    key = authority_root / "authority" / "trust-root-public.pem"
    target_key = destination / "projection-receipt-public.pem"
    target_key.write_bytes(key.read_bytes())
    target_key.chmod(0o644)
    write_json(
        destination / "index.json",
        {
            "generatorVersion": "10.8.2",
            "plan": "remediation/plan/index.json",
            "projectionReceipt": "remediation/projection-receipt.json",
            "projectionReceiptPublicKey": "remediation/projection-receipt-public.pem",
            "projectorVersion": TOOL_VERSION,
            "schema": "remediation/schema/index.json",
            "schemaVersion": "10.8.2",
        },
    )


def _remap_source_value(value: JsonValue) -> JsonValue:
    if isinstance(value, dict):
        return {key: _remap_source_value(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_remap_source_value(item) for item in value]
    if isinstance(value, str) and value.startswith("reference/public/"):
        return "remediation/" + value.removeprefix("reference/public/")
    return value


def _expected_tree(root: Path) -> dict[str, bytes]:
    remediation = root / "remediation"
    if not remediation.is_dir():
        raise RemediationError("remediation tree is absent")
    expected: dict[str, bytes] = {}
    for path in sorted(remediation.rglob("*.json")):
        relative = path.relative_to(root).as_posix()
        if relative.endswith("/index.json") or relative in {
            "remediation/index.json",
            "remediation/projection-receipt.json",
        }:
            continue
        expected[relative] = canonical_bytes(canonical_json(path))
    for category in PLAN_CATEGORIES:
        category_root = remediation / "plan" / category
        leaves = [
            path.relative_to(root).as_posix()
            for path in category_root.glob("*.json")
            if path.name != "index.json"
        ]
        expected[f"remediation/plan/{category}/index.json"] = canonical_bytes(
            manifest_index(leaves)
        )
    contract_root = remediation / "contracts"
    contract_leaves = [
        path.relative_to(root).as_posix()
        for path in contract_root.glob("*.json")
        if path.name != "index.json"
    ]
    expected["remediation/contracts/index.json"] = canonical_bytes(manifest_index(contract_leaves))
    schema_root = remediation / "schema"
    schema_leaves = [
        path.relative_to(root).as_posix() for path in schema_root.glob("*.schema.json")
    ]
    expected["remediation/schema/index.json"] = canonical_bytes(manifest_index(schema_leaves))
    expected["remediation/plan/index.json"] = canonical_bytes(
        {
            **{category: f"remediation/plan/{category}/index.json" for category in PLAN_CATEGORIES},
            "schemaVersion": "10.8.2",
        }
    )
    expected["remediation/index.json"] = canonical_bytes(
        {
            "generatorVersion": "10.8.2",
            "plan": "remediation/plan/index.json",
            "projectionReceipt": "remediation/projection-receipt.json",
            "projectionReceiptPublicKey": "remediation/projection-receipt-public.pem",
            "projectorVersion": TOOL_VERSION,
            "schema": "remediation/schema/index.json",
            "schemaVersion": "10.8.2",
        }
    )
    return expected


def _write_tree(root: Path, stage_root: Path, expected: dict[str, bytes]) -> None:
    for relative, content in expected.items():
        target = stage_root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)
        target.chmod(0o644)
    for extra in ("projection-receipt.json", "projection-receipt-public.pem"):
        source = root / "remediation" / extra
        target = stage_root / "remediation" / extra
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
        target.chmod(0o644)


def _journal(root: Path, operation_id: str, stage_root: Path, state: str) -> Path:
    journal = root / "tmp" / f"{STAGE_PREFIX}{operation_id}.json"
    _write_stage_json(
        journal,
        {
            "operationId": operation_id,
            "schema": "urn:fingrind:remediation:journal:v1",
            "stage": stage_root.relative_to(root).as_posix(),
            "state": state,
            "targets": ["remediation", "requirements-remediation-plan.txt"],
        },
    )
    _fsync_path(journal)
    _fsync_path(journal.parent)
    return journal


def _replace(root: Path, stage_root: Path, remediation_source: Path, requirements: bytes) -> str:
    operation_id = uuid.uuid4().hex
    target_root = stage_root / "targets"
    target_remediation = target_root / "remediation"
    shutil.copytree(remediation_source, target_remediation)
    target_requirements = target_root / "requirements-remediation-plan.txt"
    target_requirements.write_bytes(requirements)
    target_requirements.chmod(0o644)
    backups = stage_root / "backups"
    backups.mkdir()
    _fsync_tree(target_root)
    journal = _journal(root, operation_id, stage_root, "prepared")
    for name in ("remediation", "requirements-remediation-plan.txt"):
        target = root / name
        backup = backups / name
        staged = target_root / name
        if target.exists():
            os.replace(target, backup)
        os.replace(staged, target)
        _journal(root, operation_id, stage_root, f"installed-{name}")
    _fsync_path(root)
    shutil.rmtree(backups)
    shutil.rmtree(stage_root)
    journal.unlink()
    _fsync_path(root / "tmp")
    return operation_id


def project(root: Path, authority_root: Path) -> str:
    """Validate private authority and install an exact public projection transactionally."""
    validate_authority(authority_root)
    with _exclusive_lock(root):
        temporary = Path(tempfile.mkdtemp(prefix=STAGE_PREFIX, dir=root / "tmp"))
        temporary.chmod(0o700)
        _source_public_tree(authority_root, temporary / "source")
        source_requirements = (
            authority_root / "ledger" / "v10.8.2" / "requirements-remediation-plan.txt"
        )
        operation_id = _replace(
            root, temporary, temporary / "source" / "remediation", source_requirements.read_bytes()
        )
    validate_public_projection(root)
    return operation_id


def check(root: Path) -> None:
    """Compare every generated byte against the deterministic public rendering."""
    validate_public_projection(root)
    expected = _expected_tree(root)
    actual = {
        path.relative_to(root).as_posix(): path.read_bytes()
        for path in (root / "remediation").rglob("*")
        if path.is_file() and path.name != "projection-receipt-public.pem"
    }
    actual.pop("remediation/projection-receipt.json", None)
    if set(expected) != set(actual):
        raise RemediationError(
            "generated remediation inventory differs from the fixed production outputs"
        )
    for relative, content in expected.items():
        if actual[relative] != content:
            raise RemediationError(f"generated remediation bytes drifted: {relative}")


def generate(root: Path) -> str:
    """Reinstall already validated canonical output through the durable transaction path."""
    check(root)
    with _exclusive_lock(root):
        temporary = Path(tempfile.mkdtemp(prefix=STAGE_PREFIX, dir=root / "tmp"))
        temporary.chmod(0o700)
        expected = _expected_tree(root)
        _write_tree(root, temporary / "source", expected)
        requirements = (root / "requirements-remediation-plan.txt").read_bytes()
        operation_id = _replace(root, temporary, temporary / "source" / "remediation", requirements)
    validate_public_projection(root)
    return operation_id
