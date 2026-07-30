"""Revalidate and publish the cache derived from a pinned PowerShell archive."""

from __future__ import annotations

import os
import shutil
import stat
import uuid
from pathlib import Path

from powershell_provisioning_tree import require_regular_tree_members
from powershell_runtime_archives import copy_stream, verify_archive_file
from powershell_runtime_models import (
    MAX_ARCHIVE_BYTES,
    MAX_ARCHIVE_MEMBERS,
    MAX_EXTRACTED_BYTES,
    Downloader,
    PowerShellArtifact,
    ProvisioningError,
)


def acquire_verified_archive(
    artifact: PowerShellArtifact,
    target_parent: Path,
    work_directory: Path,
    *,
    download_url: str,
    downloader: Downloader,
) -> Path:
    """Return a private, checksum-verified archive snapshot for one provisioning attempt."""

    archive_directory = work_directory / "archive"
    archive_directory.mkdir(mode=0o700)
    private_archive = archive_directory / artifact.archive_name
    cached_archive = target_parent / artifact.archive_name
    if cached_archive.exists() or is_link_or_reparse_point(cached_archive):
        _assert_regular_unlinked_file(cached_archive, "cached PowerShell archive")
        try:
            _copy_regular_file(cached_archive, private_archive)
            verify_archive_file(private_archive, artifact)
            return private_archive
        except ProvisioningError:
            private_archive.unlink(missing_ok=True)

    downloader(download_url, private_archive)
    verify_archive_file(private_archive, artifact)
    _publish_archive_cache(private_archive, cached_archive, artifact, work_directory)
    return private_archive


def publish_staged_runtime(
    staged_runtime_directory: Path,
    target_directory: Path,
) -> None:
    """Atomically replace only a safely auditable runtime tree with a verified staged tree."""

    if target_directory.exists() or is_link_or_reparse_point(target_directory):
        if is_link_or_reparse_point(target_directory):
            raise ProvisioningError(
                "PowerShell runtime directory must not be a symlink or reparse point: "
                f"{target_directory}"
            )
        _assert_safe_runtime_tree(target_directory)
        retired_runtime_directory = _new_retired_runtime_directory(target_directory)
        try:
            os.replace(target_directory, retired_runtime_directory)
        except OSError as error:
            raise ProvisioningError(
                "could not retire the existing PowerShell runtime before replacement: "
                f"{target_directory}: {error}"
            ) from error
        try:
            os.replace(staged_runtime_directory, target_directory)
        except OSError as error:
            _restore_retired_runtime(retired_runtime_directory, target_directory)
            raise ProvisioningError(
                "could not atomically publish the verified PowerShell runtime: "
                f"{target_directory}: {error}"
            ) from error
        _remove_retired_runtime(retired_runtime_directory)
        return

    try:
        os.replace(staged_runtime_directory, target_directory)
    except OSError as error:
        raise ProvisioningError(
            "could not atomically publish the verified PowerShell runtime: "
            f"{target_directory}: {error}"
        ) from error


def is_link_or_reparse_point(path: Path) -> bool:
    """Return whether a path is a symbolic link or a Windows reparse point."""

    if path.is_symlink():
        return True
    try:
        attributes = path.lstat().st_file_attributes
    except (AttributeError, OSError):
        return False
    return bool(attributes & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0))


def _assert_regular_unlinked_file(path: Path, description: str) -> None:
    if is_link_or_reparse_point(path):
        raise ProvisioningError(f"{description} must not be a symlink or reparse point: {path}")
    try:
        metadata = path.lstat()
    except OSError as error:
        raise ProvisioningError(f"could not inspect {description}: {path}: {error}") from error
    if not stat.S_ISREG(metadata.st_mode):
        raise ProvisioningError(f"{description} is not a regular file: {path}")
    if metadata.st_nlink != 1:
        raise ProvisioningError(f"{description} must not be hard linked: {path}")


def _copy_regular_file(source: Path, destination: Path) -> None:
    _assert_regular_unlinked_file(source, "PowerShell archive")
    try:
        with source.open("rb") as input_stream, destination.open("xb") as output_stream:
            copy_stream(input_stream, output_stream, maximum_bytes=MAX_ARCHIVE_BYTES)
    except OSError as error:
        raise ProvisioningError(
            f"could not snapshot the cached PowerShell archive: {error}"
        ) from error


def _publish_archive_cache(
    private_archive: Path,
    cached_archive: Path,
    artifact: PowerShellArtifact,
    work_directory: Path,
) -> None:
    candidate_directory = work_directory / "archive-cache"
    candidate_directory.mkdir(mode=0o700)
    candidate_archive = candidate_directory / artifact.archive_name
    _copy_regular_file(private_archive, candidate_archive)
    verify_archive_file(candidate_archive, artifact)
    if cached_archive.exists() or is_link_or_reparse_point(cached_archive):
        _assert_regular_unlinked_file(cached_archive, "cached PowerShell archive")
    try:
        os.replace(candidate_archive, cached_archive)
    except OSError as error:
        raise ProvisioningError(
            "could not atomically publish the verified PowerShell archive cache: "
            f"{cached_archive}: {error}"
        ) from error


def _assert_safe_runtime_tree(runtime_directory: Path) -> None:
    if is_link_or_reparse_point(runtime_directory) or not runtime_directory.is_dir():
        raise ProvisioningError(f"PowerShell runtime is not a real directory: {runtime_directory}")
    require_regular_tree_members(
        runtime_directory,
        "cached PowerShell runtime",
        is_link_or_reparse_point=is_link_or_reparse_point,
        maximum_members=MAX_ARCHIVE_MEMBERS,
        maximum_bytes=MAX_EXTRACTED_BYTES,
        error_type=ProvisioningError,
    )


def _restore_retired_runtime(retired_runtime_directory: Path, target_directory: Path) -> None:
    try:
        if not target_directory.exists() and not is_link_or_reparse_point(target_directory):
            os.replace(retired_runtime_directory, target_directory)
    except OSError as error:
        raise ProvisioningError(
            "could not restore the existing PowerShell runtime after publication failed: "
            f"{target_directory}: {error}"
        ) from error


def _new_retired_runtime_directory(target_directory: Path) -> Path:
    for _ in range(32):
        candidate = target_directory.parent / (
            f".fingrind-powershell-retired-{target_directory.name}-{uuid.uuid4().hex}"
        )
        if not candidate.exists() and not is_link_or_reparse_point(candidate):
            return candidate
    raise ProvisioningError(
        f"could not allocate a fresh PowerShell runtime retirement path beside {target_directory}"
    )


def _remove_retired_runtime(retired_runtime_directory: Path) -> None:
    _assert_safe_runtime_tree(retired_runtime_directory)
    try:
        shutil.rmtree(retired_runtime_directory)
    except OSError as error:
        raise ProvisioningError(
            "could not remove the replaced PowerShell runtime after atomic publication: "
            f"{retired_runtime_directory}: {error}"
        ) from error
