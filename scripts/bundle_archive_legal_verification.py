"""Verify the legal payload carried by one extracted public bundle."""

from __future__ import annotations

import hashlib
import re
import zipfile
from pathlib import Path

from bundle_archive_contract_support import require

CANONICAL_LEGAL_SHA256 = {
    "LICENSE-APACHE-2.0": "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
    "LICENSE-CC0-1.0": "a2010f343487d3f7618affe54f789f5487602331c0a8d03f49e9a7c547cf0499",
    "LICENSE-SIL-OFL-1.1": "0dab92d0544f7b233403f14b84a663bdbfa746982eda629e7f4f9ffe1b036feb",
    "LICENSE-SQLITE3MULTIPLECIPHERS": "39205ec18e0f25f56c62d3bda9768e7509b63a74ba58beb7442903dfc81c247a",
}
APPLICATION_JAR_LEGAL_ENTRIES = {
    "META-INF/LICENSE",
    "META-INF/NOTICE",
    "META-INF/NOTICE-ZULU-26.32.203",
    "META-INF/LICENSE-APACHE-2.0",
    "META-INF/LICENSE-CC0-1.0",
    "META-INF/LICENSE-SIL-OFL-1.1",
    "META-INF/LICENSE-SQLITE3MULTIPLECIPHERS",
    "META-INF/LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY",
    "META-INF/SOURCE_OFFER.md",
}
DEFAULT_RUNTIME_LEGAL_LOCK = (
    Path(__file__).resolve().parent.parent / "gradle" / "runtime-legal-resources.lock.tsv"
)


def verify_legal_payload(
    bundle_root: Path,
    application_jar: Path,
    *,
    verify_dependency_payload: bool,
    runtime_legal_lock_file: Path | None,
) -> None:
    for relative_name, expected_digest in CANONICAL_LEGAL_SHA256.items():
        legal_file = bundle_root / relative_name
        require(
            hashlib.sha256(legal_file.read_bytes()).hexdigest() == expected_digest,
            f"bundle legal text did not match its reviewed canonical bytes: {legal_file}",
        )
    _verify_legal_markers(bundle_root)
    _verify_application_jar(
        application_jar,
        verify_dependency_payload=verify_dependency_payload,
        runtime_legal_lock_file=runtime_legal_lock_file,
    )


def _verify_legal_markers(bundle_root: Path) -> None:
    marker_files = {
        "LICENSE": "MIT License",
        "LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY": "Olivier Gay",
        "NOTICE": "Third-party material remains under its own terms",
        "NOTICE-ZULU-26.32.203": "Zulu26.32+203-CA",
        "PATENTS.md": "not a patent search",
        "SOURCE_OFFER.md": "offers any third party a complete machine-readable copy",
        "runtime/provenance/requested-modules.txt": "java.base",
        "runtime/legal/java.base/LICENSE": "GNU General Public License",
        "runtime/legal/java.base/ADDITIONAL_LICENSE_INFO": "Classpath Exception",
        "runtime/legal/java.base/ASSEMBLY_EXCEPTION": "OPENJDK ASSEMBLY EXCEPTION",
    }
    for relative_name, marker in marker_files.items():
        legal_file = bundle_root / relative_name
        require(legal_file.stat().st_size > 0, f"bundle legal file was empty: {legal_file}")
        require(
            marker in legal_file.read_text(encoding="utf-8"),
            f"bundle legal file omitted its identity marker {marker!r}: {legal_file}",
        )


def _verify_application_jar(
    application_jar: Path,
    *,
    verify_dependency_payload: bool,
    runtime_legal_lock_file: Path | None,
) -> None:
    try:
        with zipfile.ZipFile(application_jar) as archive:
            entry_names = _read_unique_entry_names(archive)
            missing_entries = sorted(APPLICATION_JAR_LEGAL_ENTRIES - entry_names)
            require(
                not missing_entries,
                "application JAR omitted reviewed legal resources: " + ", ".join(missing_entries),
            )
            if verify_dependency_payload:
                _verify_runtime_dependency_tree(archive, entry_names, runtime_legal_lock_file)
    except (OSError, UnicodeDecodeError, zipfile.BadZipFile):
        require(
            False, f"application JAR was not readable for legal verification: {application_jar}"
        )


def _read_unique_entry_names(archive: zipfile.ZipFile) -> set[str]:
    file_entry_names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
    require(
        len(file_entry_names) == len(set(file_entry_names)),
        "application JAR contained duplicate ZIP entry names",
    )
    return set(file_entry_names)


def _verify_runtime_dependency_tree(
    archive: zipfile.ZipFile,
    entry_names: set[str],
    runtime_legal_lock_file: Path | None,
) -> None:
    index_path = "META-INF/third-party/INDEX.tsv"
    require(index_path in entry_names, "application JAR omitted its runtime legal index")
    index_text = archive.read(index_path).decode("utf-8")
    reviewed_lock = runtime_legal_lock_file or DEFAULT_RUNTIME_LEGAL_LOCK
    require(reviewed_lock.is_file(), f"missing reviewed runtime legal lock: {reviewed_lock}")
    require(
        index_text == reviewed_lock.read_text(encoding="utf-8"),
        "application JAR runtime legal index differed from the reviewed lock",
    )
    indexed_paths = _verify_runtime_legal_index(archive, entry_names, index_text)
    actual_staged_paths = {
        name
        for name in entry_names
        if name.startswith("META-INF/third-party/") and name != index_path
    }
    require(
        actual_staged_paths == indexed_paths,
        "application JAR third-party legal tree differed from its index",
    )
    require(
        b"EXTERNAL COMPONENTS" in archive.read("META-INF/third-party/pdfbox-3.0.8/LICENSE"),
        "application JAR omitted PDFBox external-component license terms",
    )


def _verify_runtime_legal_index(
    archive: zipfile.ZipFile, entry_names: set[str], index_text: str
) -> set[str]:
    index_lines = index_text.splitlines()
    require(
        index_lines and index_lines[0] == "artifact\tartifact-sha256\tresource\tresource-sha256",
        "application JAR runtime legal index had an invalid header",
    )
    indexed_paths: set[str] = set()
    for row in index_lines[1:]:
        artifact, _artifact_digest, resource, resource_digest = _parse_index_row(row)
        relative_resource = resource.removeprefix("META-INF/")
        staged_path = f"META-INF/third-party/{artifact.removesuffix('.jar')}/{relative_resource}"
        require(staged_path in entry_names, f"indexed legal resource was absent: {staged_path}")
        require(
            hashlib.sha256(archive.read(staged_path)).hexdigest() == resource_digest,
            f"indexed legal resource digest did not match: {staged_path}",
        )
        require(staged_path not in indexed_paths, f"duplicate legal index path: {staged_path}")
        indexed_paths.add(staged_path)
    return indexed_paths


def _parse_index_row(row: str) -> tuple[str, str, str, str]:
    fields = row.split("\t")
    require(len(fields) == 4, f"invalid application JAR legal index row: {row}")
    artifact, artifact_digest, resource, resource_digest = fields
    require(
        re.fullmatch(r"[0-9a-f]{64}", artifact_digest) is not None
        and re.fullmatch(r"[0-9a-f]{64}", resource_digest) is not None,
        f"invalid digest in application JAR legal index row: {row}",
    )
    return artifact, artifact_digest, resource, resource_digest
