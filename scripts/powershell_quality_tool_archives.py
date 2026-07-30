"""Verify, extract, and validate fixed PowerShell Gallery module archives."""

from __future__ import annotations

import hashlib
import re
import stat
import zipfile
from collections.abc import Iterable
from pathlib import Path, PurePosixPath

from powershell_quality_tool_filesystem import (
    assert_regular_unlinked_file,
    assert_safe_tree,
    copy_stream,
    is_link_or_reparse_point,
    restrict_tree_to_owner,
)
from powershell_quality_tool_models import (
    MAX_ARCHIVE_BYTES,
    MAX_ARCHIVE_MEMBERS,
    MAX_EXTRACTED_BYTES,
    ProvisioningError,
    QualityToolArtifact,
)

_MANIFEST_VERSION_PATTERN = re.compile(r"(?m)^\s*ModuleVersion\s*=\s*['\"]([^'\"]+)['\"]\s*$")
_MANIFEST_ROOT_MODULE_PATTERN = re.compile(r"(?m)^\s*RootModule\s*=\s*['\"]([^'\"]+)['\"]\s*$")


def verify_archive_file(archive_path: Path, artifact: QualityToolArtifact) -> None:
    """Require the selected archive filename, regularity, size limit, and checksum."""

    if archive_path.name != artifact.archive_name:
        raise ProvisioningError(
            f"refusing unexpected PowerShell quality-tool archive name: {archive_path.name!r}"
        )
    assert_regular_unlinked_file(archive_path, "PowerShell quality-tool archive")
    if archive_path.stat().st_size > MAX_ARCHIVE_BYTES:
        raise ProvisioningError(
            f"PowerShell quality-tool archive exceeds the admitted byte limit: {archive_path}"
        )
    digest = hashlib.sha256()
    with archive_path.open("rb") as archive:
        for chunk in iter(lambda: archive.read(1024 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != artifact.sha256:
        raise ProvisioningError(
            "PowerShell quality-tool archive SHA-256 mismatch: "
            f"expected {artifact.sha256}, observed {digest.hexdigest()}"
        )


def extract_module_archive(
    archive_path: Path,
    artifact: QualityToolArtifact,
    destination: Path,
) -> None:
    """Extract one admitted ZIP archive into a fresh private staging directory."""

    if destination.exists() or is_link_or_reparse_point(destination):
        raise ProvisioningError(
            f"PowerShell quality-tool extraction destination is not fresh: {destination}"
        )
    destination.mkdir(parents=True, mode=0o700)
    try:
        with zipfile.ZipFile(archive_path) as archive:
            for member, relative_path, is_directory in validated_zip_entries(archive.infolist()):
                target = destination.joinpath(*relative_path.parts)
                if is_directory:
                    target.mkdir(parents=True, exist_ok=False)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(member, mode="r") as source, target.open("xb") as output:
                    copy_stream(source, output, expected_bytes=member.file_size)
        restrict_tree_to_owner(destination)
    except (OSError, zipfile.BadZipFile) as error:
        raise ProvisioningError(
            f"could not safely extract PowerShell quality-tool archive {artifact.module_name}: {error}"
        ) from error


def validated_zip_entries(
    members: Iterable[zipfile.ZipInfo],
) -> list[tuple[zipfile.ZipInfo, PurePosixPath, bool]]:
    """Return only regular archive members with non-overlapping safe destinations."""

    entries: list[tuple[zipfile.ZipInfo, PurePosixPath, bool]] = []
    destinations: set[PurePosixPath] = set()
    regular_files: set[PurePosixPath] = set()
    total_bytes = 0
    for count, member in enumerate(members, start=1):
        if count > MAX_ARCHIVE_MEMBERS:
            raise ProvisioningError(
                f"PowerShell quality-tool archive exceeds the {MAX_ARCHIVE_MEMBERS}-member safety limit"
            )
        if member.flag_bits & 0x1:
            raise ProvisioningError(
                f"PowerShell quality-tool archive contains an encrypted member: {member.filename!r}"
            )
        is_directory = member.is_dir()
        relative_path = normal_archive_path(member.filename, is_directory)
        if relative_path is None:
            continue
        validate_zip_member_kind(member, is_directory)
        if relative_path in destinations:
            raise ProvisioningError(
                "PowerShell quality-tool archive contains duplicate normalized destination: "
                f"{relative_path}"
            )
        destinations.add(relative_path)
        if not is_directory:
            total_bytes += member.file_size
            if total_bytes > MAX_EXTRACTED_BYTES:
                raise ProvisioningError(
                    "PowerShell quality-tool archive exceeds the admitted extracted byte limit"
                )
            regular_files.add(relative_path)
        entries.append((member, relative_path, is_directory))
    for _member, relative_path, _is_directory in entries:
        if any(parent in regular_files for parent in relative_path.parents):
            raise ProvisioningError(
                "PowerShell quality-tool archive places an entry below a regular file: "
                f"{relative_path}"
            )
    return entries


def normal_archive_path(value: str, is_directory: bool) -> PurePosixPath | None:
    """Canonicalize a ZIP member path without accepting traversal or drive syntax."""

    if not value or "\x00" in value or "\\" in value:
        raise ProvisioningError(
            f"PowerShell quality-tool archive contains an unsafe member path: {value!r}"
        )
    candidate = PurePosixPath(value)
    if candidate.is_absolute() or value.startswith("/"):
        raise ProvisioningError(
            f"PowerShell quality-tool archive contains an absolute member path: {value!r}"
        )
    normalized_parts: list[str] = []
    for part in value.split("/"):
        if part in ("", "."):
            continue
        if part == "..":
            raise ProvisioningError(
                f"PowerShell quality-tool archive contains parent traversal: {value!r}"
            )
        if ":" in part:
            raise ProvisioningError(
                f"PowerShell quality-tool archive contains a drive-qualified member path: {value!r}"
            )
        normalized_parts.append(part)
    if normalized_parts:
        return PurePosixPath(*normalized_parts)
    if is_directory:
        return None
    raise ProvisioningError("PowerShell quality-tool archive contains an empty regular-file path")


def validate_module_tree(staged_module: Path, artifact: QualityToolArtifact) -> Path:
    """Require the expected top-level manifest and module entrypoint after extraction."""

    assert_safe_tree(staged_module, "staged PowerShell quality-tool module")
    manifest_paths = sorted(staged_module.rglob(artifact.manifest_name))
    expected_manifest = staged_module / artifact.manifest_name
    if manifest_paths != [expected_manifest]:
        raise ProvisioningError(
            f"PowerShell quality-tool module {artifact.module_name} has an ambiguous manifest layout"
        )
    assert_regular_unlinked_file(expected_manifest, "PowerShell quality-tool module manifest")
    try:
        content = expected_manifest.read_text(encoding="utf-8-sig")
    except UnicodeDecodeError as error:
        raise ProvisioningError(
            f"PowerShell quality-tool module manifest is not UTF-8 text: {expected_manifest}"
        ) from error
    versions = _MANIFEST_VERSION_PATTERN.findall(content)
    roots = _MANIFEST_ROOT_MODULE_PATTERN.findall(content)
    if versions != [artifact.version] or roots != [artifact.root_module_name]:
        raise ProvisioningError(
            f"PowerShell quality-tool module manifest does not match {artifact.module_name} {artifact.version}"
        )
    root_module = staged_module / artifact.root_module_name
    assert_regular_unlinked_file(root_module, "PowerShell quality-tool root module")
    return expected_manifest


def validate_zip_member_kind(member: zipfile.ZipInfo, is_directory: bool) -> None:
    """Reject symbolic links, devices, and file/directory declaration mismatches."""

    mode = member.external_attr >> 16
    member_type = stat.S_IFMT(mode)
    if member_type not in (0, stat.S_IFREG, stat.S_IFDIR):
        raise ProvisioningError(
            "PowerShell quality-tool archive contains a non-regular or link member: "
            f"{member.filename!r}"
        )
    if (is_directory and member_type == stat.S_IFREG) or (
        not is_directory and member_type == stat.S_IFDIR
    ):
        raise ProvisioningError(
            "PowerShell quality-tool archive has a file/directory type mismatch: "
            f"{member.filename!r}"
        )
