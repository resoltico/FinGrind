"""Verify the managed native SQLite artifacts carried by a public bundle."""

from __future__ import annotations

import hashlib
import re
import zipfile
from pathlib import Path

from bundle_archive_contract_support import require


def verify_native_format_boundary_probe(probe_jar: Path) -> None:
    expected_class_entries = {
        "NativeSqliteFormatBoundaryProbe.class",
        "NativeSqliteFormatBoundaryProbe$Arguments.class",
        "NativeSqliteFormatBoundaryProbe$ProbeFailure.class",
        "NativeSqliteFormatBoundaryProbe$Sqlite.class",
    }
    try:
        with zipfile.ZipFile(probe_jar) as archive:
            class_entries = {entry.filename for entry in archive.infolist() if not entry.is_dir()}
    except (OSError, zipfile.BadZipFile):
        require(
            False,
            f"packaged native SQLite format-boundary probe was not one readable classpath JAR: {probe_jar}",
        )
        return
    require(
        class_entries == expected_class_entries,
        "packaged native SQLite format-boundary probe did not contain exactly its required classes: "
        f"{probe_jar}",
    )


def verify_native_library_checksum(native_library: Path, checksum_file: Path) -> None:
    checksum_lines = checksum_file.read_text(encoding="utf-8").splitlines()
    require(
        len(checksum_lines) == 1,
        f"native SQLite checksum did not contain exactly one checksum record: {checksum_file}",
    )
    digest_text, separator, recorded_file_name = checksum_lines[0].partition("  ")
    require(
        separator == "  " and re.fullmatch(r"[0-9a-f]{64}", digest_text) is not None,
        f"native SQLite checksum did not use the canonical SHA-256 record format: {checksum_file}",
    )
    require(
        recorded_file_name == native_library.name,
        "native SQLite checksum did not name its sibling native library: " + str(checksum_file),
    )
    require(
        digest_text == hashlib.sha256(native_library.read_bytes()).hexdigest(),
        "native SQLite checksum did not match its sibling native library: " + str(checksum_file),
    )
