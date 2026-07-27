"""Raw protected-book byte-tamper rejection evidence."""

from __future__ import annotations

import hashlib
import shutil
from dataclasses import replace
from pathlib import Path

from .attestation_head_checks import verified_attestation_head
from .cli import run_cli, run_cli_allow_failure
from .fixtures import prepare_owner_only_directory
from .models import ReleaseSmokeConfig, ReleaseSmokeFailure
from .scenario_paths import smoke_path_from_local
from .support import parse_json_output, require, require_no_match

_TAMPER_DIRECTORY = "protected-book-byte-tamper"
_TAMPER_OFFSET = 8192
_TAMPER_CODE = "protected-book-verification-failed"


def verify_protected_book_byte_tamper_rejection(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
) -> None:
    """Prove raw book-byte corruption fails safely without storage leakage."""
    print(f"{config.label}: verifying protected-book byte tamper rejection")
    original_head = verified_attestation_head(
        config,
        operation_ids,
        "before protected-book byte tamper probe",
    )
    original_book_digest = _file_digest(config.book.local_path)
    copy_config = _copy_book_and_key(config)
    _require_copy_verifies_before_tamper(copy_config, operation_ids)
    _tamper_one_nonheader_byte(copy_config.book.local_path, config.label)

    for operation_key, purpose in (
        ("verifyBook", "verify-book"),
        ("listAccounts", "list-accounts"),
    ):
        output, exit_code = run_cli_allow_failure(
            copy_config,
            operation_ids[operation_key],
            "--book-file",
            copy_config.book.argument,
            "--book-key-file",
            copy_config.book_key.argument,
            "--output",
            "json",
        )
        _require_tamper_rejection(
            output,
            exit_code,
            error_exit_codes,
            config.label,
            purpose,
        )

    require(
        _file_digest(config.book.local_path) == original_book_digest,
        f"{config.label} protected-book byte tamper probe changed the source book bytes",
    )
    require(
        verified_attestation_head(config, operation_ids, "after protected-book byte tamper probe")
        == original_head,
        f"{config.label} protected-book byte tamper probe changed the live attestation head",
    )


def _copy_book_and_key(config: ReleaseSmokeConfig) -> ReleaseSmokeConfig:
    root = config.work_root / _TAMPER_DIRECTORY
    require(
        not root.exists(),
        f"{config.label} protected-book byte tamper root already exists: {root}",
    )
    prepare_owner_only_directory(root)
    copied_book = smoke_path_from_local(config, root / "book.sqlite")
    copied_key = smoke_path_from_local(config, root / "book.key")
    for source, target, purpose in (
        (config.book.local_path, copied_book.local_path, "protected book"),
        (config.book_key.local_path, copied_key.local_path, "protected book key"),
    ):
        require(
            source.is_file() and not source.is_symlink() and not target.exists(),
            f"{config.label} could not isolate the {purpose} for a byte tamper probe",
        )
        try:
            shutil.copy2(source, target)
        except OSError as exc:
            raise ReleaseSmokeFailure(
                f"{config.label} could not copy the {purpose} for a byte tamper probe"
            ) from exc
    return replace(
        config,
        label=f"{config.label} protected-book byte tamper",
        book=copied_book,
        book_key=copied_key,
    )


def _require_copy_verifies_before_tamper(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    envelope = parse_json_output(
        run_cli(
            config,
            operation_ids["verifyBook"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--output",
            "json",
        ),
        f"{config.label} untampered copy verify-book output was not valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} copied protected book was not valid before byte tampering",
    )


def _tamper_one_nonheader_byte(book_path: Path, label: str) -> None:
    try:
        original_bytes = bytearray(book_path.read_bytes())
    except OSError as exc:
        raise ReleaseSmokeFailure(f"{label} could not read the copied protected book") from exc
    require(
        len(original_bytes) > _TAMPER_OFFSET,
        f"{label} copied protected book was too short for a non-header byte tamper probe",
    )
    original_digest = _bytes_digest(original_bytes)
    original_bytes[_TAMPER_OFFSET] ^= 0x01
    try:
        book_path.write_bytes(original_bytes)
    except OSError as exc:
        raise ReleaseSmokeFailure(f"{label} could not tamper the copied protected book") from exc
    require(
        _file_digest(book_path) != original_digest,
        f"{label} protected-book byte tamper probe did not change the copied book bytes",
    )


def _require_tamper_rejection(
    output: str,
    exit_code: int,
    error_exit_codes: dict[str, int],
    label: str,
    purpose: str,
) -> None:
    envelope = parse_json_output(
        output,
        f"{label} byte-tampered {purpose} output was not valid JSON",
    )
    require(
        exit_code == error_exit_codes[_TAMPER_CODE]
        and envelope.get("status") == "error"
        and envelope.get("code") == _TAMPER_CODE,
        f"{label} byte-tampered {purpose} did not report the public protected-book verification failure",
    )
    require_no_match(
        output,
        r"SQLITE_NOTADB",
        f"{label} byte-tampered {purpose} leaked the SQLite NOTADB storage symptom",
    )


def _file_digest(path: Path) -> str:
    try:
        return _bytes_digest(path.read_bytes())
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"could not hash protected-book byte tamper artifact {path}"
        ) from exc


def _bytes_digest(contents: bytes | bytearray) -> str:
    return hashlib.sha256(contents).hexdigest()
