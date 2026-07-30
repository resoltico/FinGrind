"""Revalidate and atomically publish cached PowerShell quality-tool artifacts."""

from __future__ import annotations

import os
import uuid
from pathlib import Path

from powershell_quality_tool_archives import verify_archive_file
from powershell_quality_tool_filesystem import (
    assert_regular_unlinked_file,
    assert_safe_tree,
    copy_regular_file,
    is_link_or_reparse_point,
    remove_safe_tree,
)
from powershell_quality_tool_models import Downloader, ProvisioningError, QualityToolArtifact


def acquire_verified_archive(
    artifact: QualityToolArtifact,
    archive_cache_directory: Path,
    work_directory: Path,
    downloader: Downloader,
) -> Path:
    """Return a private checksum-verified archive, rebuilding stale cache entries."""

    private_archive = work_directory / "archive" / artifact.archive_name
    cached_archive = archive_cache_directory / artifact.archive_name
    if cached_archive.exists() or is_link_or_reparse_point(cached_archive):
        assert_regular_unlinked_file(cached_archive, "cached PowerShell quality-tool archive")
        copy_regular_file(cached_archive, private_archive, "cached PowerShell quality-tool archive")
        try:
            verify_archive_file(private_archive, artifact)
            return private_archive
        except ProvisioningError:
            private_archive.unlink(missing_ok=True)
    downloader(artifact, private_archive)
    verify_archive_file(private_archive, artifact)
    publish_archive_cache(private_archive, cached_archive, artifact, work_directory)
    return private_archive


def publish_archive_cache(
    private_archive: Path,
    cached_archive: Path,
    artifact: QualityToolArtifact,
    work_directory: Path,
) -> None:
    """Replace the one cache entry only after writing and re-verifying a fresh snapshot."""

    candidate_directory = work_directory / f"archive-cache-{artifact.module_name}"
    candidate_directory.mkdir(mode=0o700)
    candidate_archive = candidate_directory / artifact.archive_name
    copy_regular_file(
        private_archive, candidate_archive, "verified PowerShell quality-tool archive"
    )
    verify_archive_file(candidate_archive, artifact)
    if cached_archive.exists() or is_link_or_reparse_point(cached_archive):
        assert_regular_unlinked_file(cached_archive, "cached PowerShell quality-tool archive")
    try:
        os.replace(candidate_archive, cached_archive)
    except OSError as error:
        raise ProvisioningError(
            "could not atomically publish the verified PowerShell quality-tool archive cache: "
            f"{cached_archive}: {error}"
        ) from error


def publish_staged_tree(staged_tree: Path, target_tree: Path, module_name: str) -> None:
    """Atomically replace an auditable module tree with the verified staged module."""

    if target_tree.exists() or is_link_or_reparse_point(target_tree):
        if is_link_or_reparse_point(target_tree):
            raise ProvisioningError(
                "PowerShell quality-tool module target must not be a symlink or reparse point: "
                f"{target_tree}"
            )
        assert_safe_tree(target_tree, "cached PowerShell quality-tool module")
        retired_tree = new_retired_tree(target_tree, module_name)
        try:
            os.replace(target_tree, retired_tree)
        except OSError as error:
            raise ProvisioningError(
                "could not retire the existing PowerShell quality-tool module before replacement: "
                f"{target_tree}: {error}"
            ) from error
        try:
            os.replace(staged_tree, target_tree)
        except OSError as error:
            restore_retired_tree(retired_tree, target_tree)
            raise ProvisioningError(
                "could not atomically publish the verified PowerShell quality-tool module: "
                f"{target_tree}: {error}"
            ) from error
        remove_safe_tree(retired_tree, "retired PowerShell quality-tool module")
        return
    try:
        os.replace(staged_tree, target_tree)
    except OSError as error:
        raise ProvisioningError(
            "could not atomically publish the verified PowerShell quality-tool module: "
            f"{target_tree}: {error}"
        ) from error


def restore_retired_tree(retired_tree: Path, target_tree: Path) -> None:
    """Restore the prior module when its replacement could not be atomically installed."""

    try:
        if not target_tree.exists() and not is_link_or_reparse_point(target_tree):
            os.replace(retired_tree, target_tree)
    except OSError as error:
        raise ProvisioningError(
            "could not restore the previous PowerShell quality-tool module after publication failed: "
            f"{target_tree}: {error}"
        ) from error


def new_retired_tree(target_tree: Path, module_name: str) -> Path:
    """Allocate a fresh sibling location for the prior verified tree."""

    for _ in range(32):
        candidate = target_tree.parent / (
            f".fingrind-retired-{module_name}-{target_tree.name}-{uuid.uuid4().hex}"
        )
        if not candidate.exists() and not is_link_or_reparse_point(candidate):
            return candidate
    raise ProvisioningError(
        f"could not allocate a fresh PowerShell quality-tool retirement path beside {target_tree}"
    )
