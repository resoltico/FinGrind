"""Safely verify and extract immutable PowerShell distribution archives."""

from __future__ import annotations

import hashlib
import stat
import tarfile
import zipfile
from collections.abc import Iterable
from pathlib import Path, PurePosixPath
from typing import BinaryIO

from powershell_runtime_models import (
    MAX_ARCHIVE_BYTES,
    MAX_ARCHIVE_MEMBERS,
    MAX_EXTRACTED_BYTES,
    PowerShellArtifact,
    ProvisioningError,
)


def verify_archive_file(archive_path: Path, artifact: PowerShellArtifact) -> None:
    """Require a regular archive with the selected immutable name and checksum."""

    if archive_path.name != artifact.archive_name:
        raise ProvisioningError(
            f"refusing unexpected PowerShell archive name: {archive_path.name!r}"
        )
    if not archive_path.is_file() or archive_path.is_symlink():
        raise ProvisioningError(f"PowerShell archive is not a regular file: {archive_path}")
    if archive_path.stat().st_size > MAX_ARCHIVE_BYTES:
        raise ProvisioningError(
            f"PowerShell archive exceeds the {MAX_ARCHIVE_BYTES}-byte safety limit: {archive_path}"
        )
    digest = hashlib.sha256()
    with archive_path.open("rb") as archive:
        for chunk in iter(lambda: archive.read(1024 * 1024), b""):
            digest.update(chunk)
    actual_checksum = digest.hexdigest()
    if actual_checksum != artifact.sha256:
        raise ProvisioningError(
            "PowerShell archive SHA-256 mismatch: "
            f"expected {artifact.sha256}, observed {actual_checksum}"
        )


def extract_archive(
    archive_path: Path,
    artifact: PowerShellArtifact,
    destination: Path,
) -> None:
    """Extract an admitted archive into an owned fresh staging directory."""

    if destination.exists() or destination.is_symlink():
        raise ProvisioningError(f"PowerShell extraction destination is not fresh: {destination}")
    destination.mkdir(mode=0o700)
    if artifact.archive_kind == "tar.gz":
        _extract_tar_archive(archive_path, destination)
    elif artifact.archive_kind == "zip":
        _extract_zip_archive(archive_path, destination)
    else:
        raise ProvisioningError(f"unsupported PowerShell archive format: {artifact.archive_kind!r}")


def _extract_tar_archive(archive_path: Path, destination: Path) -> None:
    try:
        with tarfile.open(archive_path, mode="r:*") as archive:
            entries: list[tuple[tarfile.TarInfo, PurePosixPath, bool]] = []
            for member in archive.getmembers():
                relative_path = _normal_archive_path(member.name, member.isdir())
                if relative_path is None:
                    continue
                if not (member.isdir() or member.isfile()):
                    raise ProvisioningError(
                        f"PowerShell archive contains a non-regular or link member: {member.name!r}"
                    )
                entries.append((member, relative_path, member.isdir()))
            _validate_archive_entries(
                (relative_path, is_directory, member.size)
                for member, relative_path, is_directory in entries
            )
            for member, relative_path, is_directory in entries:
                target = _destination_path(destination, relative_path)
                if is_directory:
                    target.mkdir(parents=True, exist_ok=False)
                    _apply_permissions(target, member.mode)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                source = archive.extractfile(member)
                if source is None:
                    raise ProvisioningError(
                        f"PowerShell archive could not open regular member: {member.name!r}"
                    )
                with source, target.open("xb") as output:
                    copy_stream(source, output, expected_bytes=member.size)
                _apply_permissions(target, member.mode)
    except (OSError, tarfile.TarError) as error:
        raise ProvisioningError(
            f"could not safely extract PowerShell tar archive: {error}"
        ) from error


def _extract_zip_archive(archive_path: Path, destination: Path) -> None:
    try:
        with zipfile.ZipFile(archive_path) as archive:
            entries: list[tuple[zipfile.ZipInfo, PurePosixPath, bool]] = []
            for member in archive.infolist():
                if member.flag_bits & 0x1:
                    raise ProvisioningError(
                        f"PowerShell archive contains an encrypted member: {member.filename!r}"
                    )
                is_directory = member.is_dir()
                relative_path = _normal_archive_path(member.filename, is_directory)
                if relative_path is None:
                    continue
                _validate_zip_member_kind(member, is_directory)
                entries.append((member, relative_path, is_directory))
            _validate_archive_entries(
                (relative_path, is_directory, member.file_size)
                for member, relative_path, is_directory in entries
            )
            for member, relative_path, is_directory in entries:
                target = _destination_path(destination, relative_path)
                if is_directory:
                    target.mkdir(parents=True, exist_ok=False)
                    _apply_permissions(target, member.external_attr >> 16)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(member, mode="r") as source, target.open("xb") as output:
                    copy_stream(source, output, expected_bytes=member.file_size)
                _apply_permissions(target, member.external_attr >> 16)
    except (OSError, zipfile.BadZipFile) as error:
        raise ProvisioningError(
            f"could not safely extract PowerShell zip archive: {error}"
        ) from error


def _normal_archive_path(value: str, is_directory: bool) -> PurePosixPath | None:
    if not value or "\x00" in value or "\\" in value:
        raise ProvisioningError(f"PowerShell archive contains an unsafe member path: {value!r}")
    candidate = PurePosixPath(value)
    if candidate.is_absolute() or value.startswith("/"):
        raise ProvisioningError(f"PowerShell archive contains an absolute member path: {value!r}")

    normalized_parts: list[str] = []
    for part in value.split("/"):
        if part in ("", "."):
            continue
        if part == "..":
            raise ProvisioningError(f"PowerShell archive contains parent traversal: {value!r}")
        if ":" in part:
            raise ProvisioningError(
                f"PowerShell archive contains a drive-qualified member path: {value!r}"
            )
        normalized_parts.append(part)
    if normalized_parts:
        return PurePosixPath(*normalized_parts)
    if is_directory:
        return None
    raise ProvisioningError("PowerShell archive contains an empty regular-file path")


def _validate_archive_entries(entries: Iterable[tuple[PurePosixPath, bool, int]]) -> None:
    destinations: set[PurePosixPath] = set()
    regular_files: set[PurePosixPath] = set()
    total_size = 0
    materialized_entries = list(entries)
    for entry_count, (relative_path, is_directory, size) in enumerate(
        materialized_entries, start=1
    ):
        if entry_count > MAX_ARCHIVE_MEMBERS:
            raise ProvisioningError(
                f"PowerShell archive exceeds the {MAX_ARCHIVE_MEMBERS}-member safety limit"
            )
        if relative_path in destinations:
            raise ProvisioningError(
                f"PowerShell archive contains duplicate normalized destination: {relative_path}"
            )
        destinations.add(relative_path)
        if size < 0:
            raise ProvisioningError(
                f"PowerShell archive contains a negative member size: {relative_path}"
            )
        if not is_directory:
            total_size += size
            if total_size > MAX_EXTRACTED_BYTES:
                raise ProvisioningError(
                    f"PowerShell archive exceeds the {MAX_EXTRACTED_BYTES}-byte extraction safety limit"
                )
            regular_files.add(relative_path)

    for relative_path, _is_directory, _size in materialized_entries:
        if any(parent in regular_files for parent in relative_path.parents):
            raise ProvisioningError(
                f"PowerShell archive places an entry below a regular file: {relative_path}"
            )


def _validate_zip_member_kind(member: zipfile.ZipInfo, is_directory: bool) -> None:
    mode = member.external_attr >> 16
    member_type = stat.S_IFMT(mode)
    if member_type not in (0, stat.S_IFREG, stat.S_IFDIR):
        raise ProvisioningError(
            f"PowerShell archive contains a non-regular or link member: {member.filename!r}"
        )
    if (is_directory and member_type == stat.S_IFREG) or (
        not is_directory and member_type == stat.S_IFDIR
    ):
        raise ProvisioningError(
            f"PowerShell archive has a file/directory type mismatch: {member.filename!r}"
        )


def _destination_path(destination: Path, relative_path: PurePosixPath) -> Path:
    return destination.joinpath(*relative_path.parts)


def _apply_permissions(path: Path, archive_mode: int) -> None:
    permissions = archive_mode & 0o777
    if permissions:
        path.chmod(permissions)


def copy_stream(
    source: BinaryIO,
    destination: BinaryIO,
    *,
    expected_bytes: int | None = None,
    maximum_bytes: int | None = None,
) -> None:
    """Copy a bounded binary stream and enforce any exact member size."""

    copied_bytes = 0
    while chunk := source.read(1024 * 1024):
        copied_bytes += len(chunk)
        if maximum_bytes is not None and copied_bytes > maximum_bytes:
            raise ProvisioningError(
                f"PowerShell archive exceeds the {maximum_bytes}-byte download safety limit"
            )
        destination.write(chunk)
    if expected_bytes is not None and copied_bytes != expected_bytes:
        raise ProvisioningError(
            "PowerShell archive member size changed while extracting: "
            f"expected {expected_bytes}, copied {copied_bytes}"
        )
