#!/usr/bin/env python3
"""Verify the extracted self-contained bundle against the canonical release contract."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

from contract_values import load_contract_values


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify one extracted FinGrind bundle root against the canonical release contract."
    )
    parser.add_argument(
        "--repo-root",
        required=True,
        type=Path,
        help="Repository root that owns the canonical contract resources.",
    )
    parser.add_argument(
        "--bundle-root",
        required=True,
        type=Path,
        help="Extracted FinGrind bundle root to verify.",
    )
    return parser.parse_args()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def require_match(text: str, pattern: str, message: str) -> None:
    if re.search(pattern, text, re.MULTILINE) is None:
        raise SystemExit(message)


def require_no_match(text: str, pattern: str, message: str) -> None:
    if re.search(pattern, text, re.MULTILINE) is not None:
        raise SystemExit(message)


def normalize_newlines(text: str) -> str:
    return text.replace("\r", "")


def joined_path(root: Path, relative_path: str) -> Path:
    segments = [segment for segment in relative_path.replace("\\", "/").split("/") if segment]
    return root.joinpath(*segments)


def bundled_java_command(bundle_root: Path) -> Path:
    candidates = [
        bundle_root / "runtime" / "bin" / "java",
        bundle_root / "runtime" / "bin" / "java.exe",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise SystemExit(f"missing bundled Java runtime under {bundle_root / 'runtime' / 'bin'}")


def normalized_command_output(command: list[str]) -> str:
    completed = subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
    )
    return normalize_newlines(completed.stdout + completed.stderr)


def verify_java_version(java_command: Path, expected_source_checkout_java: str) -> None:
    expected_feature_version = expected_source_checkout_java.rstrip("+")
    require(
        bool(expected_feature_version),
        "source-checkout Java contract must not be blank when verifying the bundled runtime",
    )

    version_output = normalized_command_output([str(java_command), "--version"])
    first_line = next((line for line in version_output.splitlines() if line.strip()), "")
    version_tokens = [token for token in first_line.split() if token]
    require(
        len(version_tokens) >= 2
        and (
            version_tokens[1] == expected_feature_version
            or version_tokens[1].startswith(expected_feature_version + ".")
        ),
        f"bundled Java runtime did not report Java {expected_feature_version}",
    )


def verify_bundle_root_files(bundle_root: Path, contract: dict[str, object]) -> None:
    host_bundle_target = contract["bundleLayout"]["hostBundleTarget"]
    assert isinstance(host_bundle_target, dict)
    launcher_path = joined_path(bundle_root, str(host_bundle_target["launcherPath"]))
    application_jar = bundle_root / "lib" / "app" / "fingrind.jar"
    native_library = bundle_root / "lib" / "native" / str(host_bundle_target["sqliteLibraryFileName"])
    native_library_checksum = (
        bundle_root
        / "lib"
        / "native"
        / (str(host_bundle_target["sqliteLibraryFileName"]) + ".sha256")
    )
    java_command = bundled_java_command(bundle_root)

    required_files = [
        launcher_path,
        application_jar,
        native_library,
        native_library_checksum,
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

    if os.name != "nt":
        require(os.access(launcher_path, os.X_OK), f"missing executable bundle launcher at {launcher_path}")
        require(os.access(java_command, os.X_OK), f"missing executable bundled Java runtime at {java_command}")


def verify_bundle_manifest(bundle_root: Path, contract: dict[str, object]) -> None:
    readme_text = normalize_newlines((bundle_root / "README.md").read_text(encoding="utf-8"))
    require_match(readme_text, r"^# FinGrind ", "bundle README did not start with the FinGrind title")
    require_match(
        readme_text,
        r"bundle-manifest\.json",
        "bundle README did not mention the machine-readable bundle manifest",
    )

    manifest_text = normalize_newlines((bundle_root / "bundle-manifest.json").read_text(encoding="utf-8"))
    compact_manifest_text = re.sub(r"\s+", "", manifest_text)
    for forbidden_key in ("discoveryCommands", "administrationCommands", "queryCommands", "writeCommands"):
        require_no_match(
            compact_manifest_text,
            '"' + forbidden_key + '":',
            f"bundle manifest reauthored static command-group array {forbidden_key} instead of pointing to the canonical contract",
        )

    manifest = json.loads(manifest_text)
    runtime_surface = contract["runtimeSurface"]
    public_distribution = contract["publicDistribution"]
    managed_sqlite = contract["managedSqlite"]
    operation_ids = contract["operationIds"]
    host_bundle_target = contract["bundleLayout"]["hostBundleTarget"]
    assert isinstance(manifest, dict)
    assert isinstance(runtime_surface, dict)
    assert isinstance(public_distribution, dict)
    assert isinstance(managed_sqlite, dict)
    assert isinstance(operation_ids, dict)
    assert isinstance(host_bundle_target, dict)

    checks = [
        (
            manifest["runtimeDistribution"] == runtime_surface["bundleRuntimeDistribution"],
            "bundle manifest did not report the self-contained runtime distribution",
        ),
        (
            manifest["publicCliDistribution"] == runtime_surface["publicCliDistribution"],
            "bundle manifest did not report the public bundle distribution contract",
        ),
        (
            manifest["managedSqlite"]["storageDriver"] == runtime_surface["storageDriver"],
            "bundle manifest did not report the canonical storage driver",
        ),
        (
            manifest["managedSqlite"]["storageEngine"] == runtime_surface["storageEngine"],
            "bundle manifest did not report the canonical storage engine",
        ),
        (
            manifest["managedSqlite"]["bookProtectionMode"] == runtime_surface["bookProtectionMode"],
            "bundle manifest did not report the canonical book protection mode",
        ),
        (
            manifest["managedSqlite"]["defaultBookCipher"] == runtime_surface["defaultBookCipher"],
            "bundle manifest did not report the canonical default book cipher",
        ),
        (
            manifest["managedSqlite"]["libraryMode"] == runtime_surface["sqliteLibraryMode"],
            "bundle manifest did not report the canonical SQLite library mode",
        ),
        (
            manifest["managedSqlite"]["requiredMinimumSqliteVersion"]
            == managed_sqlite["requiredMinimumSqliteVersion"],
            "bundle manifest did not report the canonical minimum SQLite version",
        ),
        (
            manifest["managedSqlite"]["requiredSqlite3mcVersion"]
            == managed_sqlite["requiredSqlite3mcVersion"],
            "bundle manifest did not report the canonical SQLite3 Multiple Ciphers version",
        ),
        (
            manifest["managedSqlite"]["requiredSqliteSourceId"]
            == managed_sqlite["requiredSqliteSourceId"],
            "bundle manifest did not report the canonical SQLite source id",
        ),
        (
            manifest["managedSqlite"]["requiredCompileOptions"]
            == managed_sqlite["requiredCompileOptions"],
            "bundle manifest did not report the canonical SQLite compile options",
        ),
        (
            manifest["managedSqlite"]["forbiddenCompileOptions"]
            == managed_sqlite["forbiddenCompileOptions"],
            "bundle manifest did not report the canonical forbidden SQLite compile options",
        ),
        (
            manifest["managedSqlite"]["requiresSecureMemorySupport"]
            == managed_sqlite["requiresSecureMemorySupport"],
            "bundle manifest did not report the canonical SQLite3MC secure-memory requirement",
        ),
        (
            manifest["bundleTarget"]["classifier"] == host_bundle_target["classifier"],
            "bundle manifest did not report the current host classifier",
        ),
        (
            manifest["archiveFormat"] == host_bundle_target["archiveFormat"],
            "bundle manifest did not report the platform-native archive format",
        ),
        (
            manifest["launcher"] == host_bundle_target["launcherPath"],
            "bundle manifest did not report the canonical launcher path",
        ),
        (
            manifest["supportedPublicCliBundleTargets"]
            == public_distribution["supportedPublicCliBundleTargets"],
            "bundle manifest did not report the supported public bundle targets",
        ),
        (
            manifest["unsupportedPublicCliBundleTargets"]
            == public_distribution["unsupportedPublicCliBundleTargets"],
            "bundle manifest did not report the current unsupported public bundle targets",
        ),
        (
            manifest["bootstrap"]["recommendedFirstCommand"][-1] == operation_ids["help"],
            "bundle manifest did not publish the canonical bootstrap help command",
        ),
        (
            manifest["bootstrap"]["machineReadableContractCommand"][-1] == operation_ids["capabilities"],
            "bundle manifest did not publish the canonical machine-readable contract command",
        ),
        (
            manifest["bootstrap"]["requestTemplateCommand"][-1] == operation_ids["printRequestTemplate"],
            "bundle manifest did not publish the canonical request-template bootstrap command",
        ),
        (
            manifest["bootstrap"]["planTemplateCommand"][-1] == operation_ids["printPlanTemplate"],
            "bundle manifest did not publish the canonical plan-template bootstrap command",
        ),
    ]
    for passed, message in checks:
        require(bool(passed), message)


def verify_bundled_runtime(bundle_root: Path, contract: dict[str, object]) -> None:
    java_command = bundled_java_command(bundle_root)
    runtime_environment = contract["runtimeEnvironment"]
    assert isinstance(runtime_environment, dict)
    verify_java_version(java_command, str(runtime_environment["sourceCheckoutJava"]))

    runtime_modules_output = normalized_command_output([str(java_command), "--list-modules"])
    require_no_match(
        runtime_modules_output,
        r"^jdk\.jlink@",
        "bundled Java runtime contains jdk.jlink",
    )
    require_no_match(
        runtime_modules_output,
        r"^jdk\.jpackage@",
        "bundled Java runtime contains jdk.jpackage",
    )
    require_no_match(
        runtime_modules_output,
        r"^jdk\.jdeps@",
        "bundled Java runtime contains jdk.jdeps",
    )
    require_match(
        runtime_modules_output,
        r"^jdk\.unsupported@",
        "bundled Java runtime omitted jdk.unsupported, which PDF export requires for a noise-free runtime",
    )


def main() -> int:
    args = parse_args()
    repo_root = args.repo_root.resolve()
    bundle_root = args.bundle_root.resolve()
    contract = load_contract_values(repo_root)

    verify_bundle_root_files(bundle_root, contract)
    verify_bundle_manifest(bundle_root, contract)
    verify_bundled_runtime(bundle_root, contract)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
