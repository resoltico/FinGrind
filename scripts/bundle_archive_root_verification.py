from __future__ import annotations

import os
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
    native_library = bundle_root / "lib" / "native" / str(bundle_target["sqliteLibraryFileName"])
    native_library_checksum = (
        bundle_root / "lib" / "native" / (str(bundle_target["sqliteLibraryFileName"]) + ".sha256")
    )
    java_command = bundled_java_command(bundle_root)

    required_files = [
        launcher_path,
        application_jar,
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
