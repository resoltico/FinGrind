"""Download, validate, and atomically publish the selected PowerShell runtime."""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

from powershell_runtime_archives import extract_archive
from powershell_runtime_cache import (
    acquire_verified_archive,
    is_link_or_reparse_point,
    publish_staged_runtime,
)
from powershell_runtime_download import download_artifact
from powershell_runtime_metadata import (
    artifact_download_url,
    select_artifact,
)
from powershell_runtime_models import (
    Downloader,
    PowerShellMetadata,
    ProvisioningError,
)


def provision_runtime(
    metadata: PowerShellMetadata,
    install_root: Path,
    *,
    operating_system: str | None = None,
    architecture: str | None = None,
    downloader: Downloader | None = None,
) -> Path:
    """Publish a verified runtime atomically and return its exact executable path."""

    artifact = select_artifact(
        metadata,
        operating_system=operating_system,
        architecture=architecture,
    )
    install_root = _prepare_install_root(install_root)
    target_parent = _prepare_runtime_parent(install_root, metadata.version)
    target_directory = target_parent / artifact.platform_id
    executable_path = target_directory / artifact.executable_name

    work_directory = Path(tempfile.mkdtemp(prefix=".fingrind-powershell-", dir=target_parent))
    try:
        selected_downloader = downloader or download_artifact
        archive_path = acquire_verified_archive(
            artifact,
            target_parent,
            work_directory,
            download_url=artifact_download_url(metadata, artifact),
            downloader=selected_downloader,
        )

        staged_runtime_directory = work_directory / "runtime"
        extract_archive(archive_path, artifact, staged_runtime_directory)
        staged_executable = staged_runtime_directory / artifact.executable_name
        _prepare_executable(staged_executable)
        validate_powershell_executable(staged_executable, metadata.version)
        _make_runtime_directory_executable(staged_runtime_directory)

        publish_staged_runtime(staged_runtime_directory, target_directory)
    finally:
        shutil.rmtree(work_directory, ignore_errors=True)
    return executable_path


def validate_powershell_executable(executable_path: Path, expected_version: str) -> None:
    """Require a regular executable to report exactly the pinned PowerShell version."""

    if not executable_path.is_file() or executable_path.is_symlink():
        raise ProvisioningError(f"PowerShell executable is not a regular file: {executable_path}")
    try:
        completed = subprocess.run(
            [
                str(executable_path),
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "$PSVersionTable.PSVersion.ToString()",
            ],
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=30,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise ProvisioningError(
            f"could not execute PowerShell runtime at {executable_path}: {error}"
        ) from error
    if completed.returncode != 0:
        diagnostic = completed.stderr.strip() or completed.stdout.strip() or "no diagnostic output"
        raise ProvisioningError(
            f"PowerShell runtime at {executable_path} failed version validation: {diagnostic}"
        )
    actual_version = completed.stdout.strip()
    if actual_version != expected_version:
        raise ProvisioningError(
            "PowerShell runtime version mismatch: "
            f"expected {expected_version}, observed {actual_version or '<empty>'}"
        )


def _prepare_install_root(install_root: Path) -> Path:
    expanded_root = install_root.expanduser().absolute()
    for ancestor in (expanded_root, *expanded_root.parents):
        if is_link_or_reparse_point(ancestor):
            raise ProvisioningError(
                "PowerShell install root must not descend from a symlink or reparse-point ancestor: "
                f"{ancestor}"
            )
    resolved_root = expanded_root.resolve(strict=False)
    if resolved_root == resolved_root.parent:
        raise ProvisioningError("PowerShell install root must not be a filesystem root")
    resolved_root.mkdir(parents=True, exist_ok=True)
    if not resolved_root.is_dir() or is_link_or_reparse_point(resolved_root):
        raise ProvisioningError(f"PowerShell install root is not a real directory: {resolved_root}")
    return resolved_root


def _prepare_runtime_parent(install_root: Path, version: str) -> Path:
    target_parent = install_root / version
    if target_parent.exists() or is_link_or_reparse_point(target_parent):
        if not target_parent.is_dir() or is_link_or_reparse_point(target_parent):
            raise ProvisioningError(
                f"PowerShell runtime version directory is not a real directory: {target_parent}"
            )
        _make_runtime_directory_executable(target_parent)
        return target_parent
    target_parent.mkdir(mode=0o755)
    if not target_parent.is_dir() or is_link_or_reparse_point(target_parent):
        raise ProvisioningError(
            "PowerShell runtime version directory became unsafe while provisioning: "
            f"{target_parent}"
        )
    return target_parent


def _prepare_executable(executable_path: Path) -> None:
    if not executable_path.is_file() or executable_path.is_symlink():
        raise ProvisioningError(
            "PowerShell archive does not provide the expected regular executable: "
            f"{executable_path}"
        )
    if os.name != "nt":
        executable_path.chmod(executable_path.stat().st_mode | 0o100)


def _make_runtime_directory_executable(directory: Path) -> None:
    directory.chmod(0o755)
