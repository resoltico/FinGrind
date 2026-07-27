from __future__ import annotations

import os
import zipfile
from pathlib import Path

from bundle_archive_contract_support import (
    bundled_java_command,
    joined_path,
    load_bundle_manifest,
    manifest_normalized_artifact_epoch_seconds,
    require,
    require_executable,
    resolve_bundle_target,
)


def verify_bundle_root_files(bundle_root: Path, contract: dict[str, object]) -> None:
    manifest = load_bundle_manifest(bundle_root)
    normalized_artifact_epoch_seconds = manifest_normalized_artifact_epoch_seconds(manifest)
    _, bundle_target = resolve_bundle_target(contract, manifest)
    launcher_path = joined_path(bundle_root, str(bundle_target["launcherPath"]))
    application_jar = bundle_root / "lib" / "app" / "fingrind.jar"
    native_format_boundary_probe = (
        bundle_root / "lib" / "release-smoke" / "native-sqlite-format-boundary-probe.jar"
    )
    native_library = bundle_root / "lib" / "native" / str(bundle_target["sqliteLibraryFileName"])
    native_library_checksum = (
        bundle_root / "lib" / "native" / (str(bundle_target["sqliteLibraryFileName"]) + ".sha256")
    )
    java_command = bundled_java_command(bundle_root)

    required_files = [
        launcher_path,
        application_jar,
        native_format_boundary_probe,
        native_library,
        native_library_checksum,
        bundle_root / "quick-start-request.json",
        bundle_root / "LICENSE",
        bundle_root / "LICENSE-APACHE-2.0",
        bundle_root / "LICENSE-SIL-OFL-1.1",
        bundle_root / "LICENSE-SQLITE3MULTIPLECIPHERS",
        bundle_root / "NOTICE",
        bundle_root / "PATENTS.md",
        bundle_root / "README.md",
        bundle_root / "bundle-manifest.json",
    ]
    for required_file in required_files:
        require(required_file.is_file(), f"missing bundle file at {required_file}")
    _verify_native_format_boundary_probe(native_format_boundary_probe)

    # Cross-platform extractors do not consistently preserve directory mtimes, so the public
    # reproducibility contract is defined on extracted files only.
    for bundled_path in [bundle_root, *bundle_root.rglob("*")]:
        if bundled_path.is_dir():
            continue
        require(
            int(bundled_path.stat().st_mtime) == normalized_artifact_epoch_seconds,
            "bundle path did not preserve the manifest-declared normalized artifact timestamp: "
            f"{bundled_path}",
        )

    if os.name != "nt":
        require_executable(launcher_path, f"missing executable bundle launcher at {launcher_path}")
        require_executable(
            java_command, f"missing executable bundled Java runtime at {java_command}"
        )


def _verify_native_format_boundary_probe(probe_jar: Path) -> None:
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
