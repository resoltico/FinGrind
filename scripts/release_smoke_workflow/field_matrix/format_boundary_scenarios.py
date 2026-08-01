"""Fresh-archive orchestration for adjacent protected-book format rejections."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ..models import ReleaseSmokeConfig
from ..support import require
from .format_boundary_artifacts import _copy_fresh_book, _file_digest, _require_file_digest
from .format_boundary_inspection import assert_inspection_rejection
from .format_boundary_operational_matrix import require_operational_format_refusals
from .format_boundary_probe_execution import (
    loaded_sqlite_library_path,
    require_persisted_user_version,
    required_operation_id,
    write_user_version,
)
from .format_boundary_refusals import _require_open_book_does_not_replace_boundary

_MAX_SQLITE_USER_VERSION = 2_147_483_647


def verify_protected_book_format_boundary_rejections(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, object],
    protected_book_format: Mapping[str, Any],
    error_exit_codes: Mapping[str, int],
) -> None:
    """Prove the archive refuses both immediately adjacent physical format lines."""
    print(f"{config.label}: verifying protected-book format boundary rejections")
    supported_format, retired_format, future_format = _format_boundary_versions(
        protected_book_format,
        config.label,
    )
    native_library = loaded_sqlite_library_path(config, operation_ids)
    for boundary_name, boundary_format in (("retired", retired_format), ("future", future_format)):
        _verify_format_boundary_rejection(
            config,
            operation_ids,
            native_library,
            supported_format,
            boundary_name,
            boundary_format,
            error_exit_codes,
        )


def _verify_format_boundary_rejection(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, object],
    native_library: str,
    supported_format: int,
    boundary_name: str,
    boundary_format: int,
    error_exit_codes: Mapping[str, int],
) -> None:
    """Exercise inspection and operational refusals without changing either boundary artifact."""
    print(f"{config.label}: verifying {boundary_name} protected-book format rejection")
    boundary_book, boundary_key = _copy_fresh_book(config, boundary_name)
    write_user_version(config, native_library, boundary_book, boundary_key, boundary_format)
    require_persisted_user_version(
        config,
        native_library,
        boundary_book,
        boundary_key,
        boundary_format,
        "before public inspection",
    )
    original_book_digest = _file_digest(boundary_book.local_path)
    original_key_digest = _file_digest(boundary_key.local_path)
    assert_inspection_rejection(
        config,
        required_operation_id(operation_ids, "inspectBook", config),
        boundary_book,
        boundary_key,
        boundary_name,
        boundary_format,
        supported_format,
    )
    require_operational_format_refusals(
        config,
        operation_ids,
        boundary_book,
        boundary_key,
        boundary_name,
        boundary_format,
        supported_format,
        error_exit_codes,
    )
    _require_open_book_does_not_replace_boundary(
        config,
        boundary_book,
        boundary_key,
        boundary_name,
        error_exit_codes,
        required_operation_id(operation_ids, "openBook", config),
    )
    require_persisted_user_version(
        config,
        native_library,
        boundary_book,
        boundary_key,
        boundary_format,
        "after public inspection",
    )
    _require_file_digest(
        boundary_book.local_path,
        original_book_digest,
        f"{config.label} {boundary_name}-format operational checks rewrote the protected book",
    )
    _require_file_digest(
        boundary_key.local_path,
        original_key_digest,
        f"{config.label} {boundary_name}-format operational checks rewrote the book key",
    )


def _format_boundary_versions(
    protected_book_format: Mapping[str, Any],
    label: str,
) -> tuple[int, int, int]:
    """Derive the two directly adjacent SQLite user-version values from live discovery."""
    version = protected_book_format.get("formatVersion")
    require(
        isinstance(version, int)
        and not isinstance(version, bool)
        and 2 <= version < _MAX_SQLITE_USER_VERSION,
        f"{label} runtime contract did not expose one protected-book format version "
        "with immediately adjacent SQLite format lines",
    )
    if not isinstance(version, int) or isinstance(version, bool):
        raise TypeError("protected-book format version must be an integer")
    return version, version - 1, version + 1
