"""Own filesystem admission and private-tree handling for quality-tool provisioning."""

from __future__ import annotations

import os
import shutil
import stat
from pathlib import Path
from typing import BinaryIO

from powershell_provisioning_tree import require_regular_tree_members
from powershell_quality_tool_models import (
    MAX_ARCHIVE_BYTES,
    MAX_ARCHIVE_MEMBERS,
    MAX_EXTRACTED_BYTES,
    ProvisioningError,
)


def prepare_install_root(install_root: Path) -> Path:
    """Return a real, private root that no symbolic or reparse path can redirect."""

    expanded_root = install_root.expanduser().absolute()
    for ancestor in (expanded_root, *expanded_root.parents):
        if is_link_or_reparse_point(ancestor):
            raise ProvisioningError(
                "PowerShell quality-tool install root must not descend from a symlink or reparse-point "
                f"ancestor: {ancestor}"
            )
    resolved_root = expanded_root.resolve(strict=False)
    if resolved_root == resolved_root.parent:
        raise ProvisioningError(
            "PowerShell quality-tool install root must not be a filesystem root"
        )
    resolved_root.mkdir(parents=True, mode=0o700, exist_ok=True)
    if not resolved_root.is_dir() or is_link_or_reparse_point(resolved_root):
        raise ProvisioningError(
            f"PowerShell quality-tool install root is not a real directory: {resolved_root}"
        )
    assert_private_directory(resolved_root, "PowerShell quality-tool install root")
    return resolved_root


def prepare_directory(directory: Path, description: str) -> Path:
    """Return an owned private directory, rejecting a link or an unsafe replacement."""

    if directory.exists() or is_link_or_reparse_point(directory):
        if not directory.is_dir() or is_link_or_reparse_point(directory):
            raise ProvisioningError(
                f"PowerShell quality-tool {description} is not a real directory: {directory}"
            )
        assert_private_directory(directory, f"PowerShell quality-tool {description}")
        return directory
    try:
        directory.mkdir(parents=True, mode=0o700)
    except OSError as error:
        raise ProvisioningError(
            f"could not create PowerShell quality-tool {description}: {directory}: {error}"
        ) from error
    if not directory.is_dir() or is_link_or_reparse_point(directory):
        raise ProvisioningError(f"PowerShell quality-tool {description} became unsafe: {directory}")
    assert_private_directory(directory, f"PowerShell quality-tool {description}")
    return directory


def assert_regular_unlinked_file(path: Path, description: str) -> None:
    """Require a regular, single-link, non-reparse file before copying or trusting it."""

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


def copy_regular_file(source: Path, destination: Path, description: str) -> None:
    """Copy a verified regular file into a fresh private destination."""

    assert_regular_unlinked_file(source, description)
    try:
        with source.open("rb") as input_stream, destination.open("xb") as output_stream:
            copy_stream(input_stream, output_stream, maximum_bytes=MAX_ARCHIVE_BYTES)
    except OSError as error:
        raise ProvisioningError(f"could not snapshot {description}: {error}") from error


def copy_stream(
    source: BinaryIO,
    destination: BinaryIO,
    *,
    expected_bytes: int | None = None,
    maximum_bytes: int | None = None,
) -> None:
    """Copy a bounded binary stream and enforce an optional exact member length."""

    copied_bytes = 0
    while chunk := source.read(1024 * 1024):
        copied_bytes += len(chunk)
        if maximum_bytes is not None and copied_bytes > maximum_bytes:
            raise ProvisioningError(
                f"PowerShell quality-tool archive exceeds the {maximum_bytes}-byte safety limit"
            )
        destination.write(chunk)
    if expected_bytes is not None and copied_bytes != expected_bytes:
        raise ProvisioningError(
            "PowerShell quality-tool archive member size changed while extracting: "
            f"expected {expected_bytes}, copied {copied_bytes}"
        )


def assert_safe_tree(tree: Path, description: str) -> None:
    """Audit a private tree before it becomes input to atomic replacement or deletion."""

    if is_link_or_reparse_point(tree) or not tree.is_dir():
        raise ProvisioningError(f"{description} is not a real directory: {tree}")
    assert_private_directory(tree, description)
    require_regular_tree_members(
        tree,
        description,
        is_link_or_reparse_point=is_link_or_reparse_point,
        maximum_members=MAX_ARCHIVE_MEMBERS,
        maximum_bytes=MAX_EXTRACTED_BYTES,
        error_type=ProvisioningError,
    )


def remove_safe_tree(tree: Path, description: str) -> None:
    """Delete only a freshly audited private tree."""

    if not tree.exists() and not is_link_or_reparse_point(tree):
        return
    assert_safe_tree(tree, description)
    try:
        shutil.rmtree(tree)
    except OSError as error:
        raise ProvisioningError(f"could not remove {description}: {tree}: {error}") from error


def restrict_tree_to_owner(tree: Path) -> None:
    """Set extracted POSIX directories private before publishing their verified tree."""

    if os.name != "posix":
        return
    try:
        for current_root, directories, _files in os.walk(tree, topdown=True, followlinks=False):
            os.chmod(current_root, 0o700)
            for directory_name in directories:
                os.chmod(Path(current_root, directory_name), 0o700)
    except OSError as error:
        raise ProvisioningError(
            f"could not restrict PowerShell quality-tool extraction tree to its owner: {tree}: {error}"
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


def assert_private_directory(directory: Path, description: str) -> None:
    """Reject POSIX cache trees that another account can inspect or replace."""

    if os.name != "posix":
        return
    try:
        mode = directory.lstat().st_mode
    except OSError as error:
        raise ProvisioningError(f"could not inspect {description}: {directory}: {error}") from error
    if mode & 0o077:
        raise ProvisioningError(
            f"{description} must be private to its owner (mode 0700 or stricter): {directory}"
        )
