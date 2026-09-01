"""Verify the extracted public bundle's root layout and cross-cutting invariants."""

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
from bundle_archive_legal_verification import verify_legal_payload
from bundle_archive_native_verification import (
    verify_native_format_boundary_probe,
    verify_native_library_checksum,
)
from bundle_archive_runtime_legal_verification import verify_runtime_legal_payload


def verify_bundle_root_files(
    bundle_root: Path,
    contract: dict[str, object],
    *,
    require_host_executability: bool = True,
    runtime_legal_lock_file: Path | None = None,
) -> None:
    manifest = load_bundle_manifest(bundle_root)
    normalized_artifact_epoch_seconds = manifest_normalized_artifact_epoch_seconds(manifest)
    _, bundle_target = resolve_bundle_target(contract, manifest)
    launcher_path = joined_path(bundle_root, str(bundle_target["launcherPath"]))
    application_jar = bundle_root / "lib" / "app" / "fingrind.jar"
    native_probe = bundle_root / "lib" / "release-smoke" / "native-sqlite-format-boundary-probe.jar"
    native_library = bundle_root / "lib" / "native" / str(bundle_target["sqliteLibraryFileName"])
    native_checksum = native_library.with_name(native_library.name + ".sha256")
    _require_bundle_files(
        bundle_root,
        launcher_path,
        application_jar,
        native_probe,
        native_library,
        native_checksum,
    )
    verify_legal_payload(
        bundle_root,
        application_jar,
        verify_dependency_payload=require_host_executability,
        runtime_legal_lock_file=runtime_legal_lock_file,
    )
    verify_runtime_legal_payload(
        bundle_root,
        expected_operating_system_id=str(bundle_target["operatingSystemId"]),
        expected_architecture_id=str(bundle_target["architectureId"]),
    )
    verify_native_format_boundary_probe(native_probe)
    verify_native_library_checksum(native_library, native_checksum)
    _verify_normalized_timestamps(bundle_root, normalized_artifact_epoch_seconds)
    _verify_host_executability(bundle_root, launcher_path, require_host_executability)


def _require_bundle_files(
    bundle_root: Path,
    launcher_path: Path,
    application_jar: Path,
    native_probe: Path,
    native_library: Path,
    native_checksum: Path,
) -> None:
    required_files = [
        launcher_path,
        application_jar,
        native_probe,
        native_library,
        native_checksum,
        bundle_root / "lib" / "native" / "toolchain-fingerprint.json",
        bundle_root / "lib" / "native" / "build-contract.json",
        bundle_root / "quick-start-request.json",
        bundle_root / "LICENSE",
        bundle_root / "LICENSE-APACHE-2.0",
        bundle_root / "LICENSE-CC0-1.0",
        bundle_root / "LICENSE-SIL-OFL-1.1",
        bundle_root / "LICENSE-SQLITE3MULTIPLECIPHERS",
        bundle_root / "LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY",
        bundle_root / "NOTICE",
        bundle_root / "NOTICE-ZULU-26.32.203",
        bundle_root / "PATENTS.md",
        bundle_root / "SOURCE_OFFER.md",
        bundle_root / "README.md",
        bundle_root / "bundle-manifest.json",
        bundle_root / "runtime" / "release",
        bundle_root / "runtime" / "provenance" / "source-jdk-release",
        bundle_root / "runtime" / "provenance" / "requested-modules.txt",
        bundle_root / "runtime" / "legal" / "java.base" / "LICENSE",
        bundle_root / "runtime" / "legal" / "java.base" / "ADDITIONAL_LICENSE_INFO",
        bundle_root / "runtime" / "legal" / "java.base" / "ASSEMBLY_EXCEPTION",
        bundle_root / "runtime" / "legal" / "INDEX.sha256",
    ]
    for required_file in required_files:
        require(required_file.is_file(), f"missing bundle file at {required_file}")


def _verify_normalized_timestamps(bundle_root: Path, normalized_epoch_seconds: int) -> None:
    for bundled_path in [bundle_root, *bundle_root.rglob("*")]:
        if not bundled_path.is_dir():
            require(
                int(bundled_path.stat().st_mtime) == normalized_epoch_seconds,
                "bundle path did not preserve the manifest-declared normalized artifact timestamp: "
                f"{bundled_path}",
            )


def _verify_host_executability(
    bundle_root: Path, launcher_path: Path, require_host_executability: bool
) -> None:
    if require_host_executability and os.name != "nt":
        require_executable(launcher_path, f"missing executable bundle launcher at {launcher_path}")
        java_command = bundled_java_command(bundle_root)
        require_executable(
            java_command,
            f"missing executable bundled Java runtime at {java_command}",
        )
