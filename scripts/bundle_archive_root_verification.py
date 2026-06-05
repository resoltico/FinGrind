from __future__ import annotations

import json
import os
import re
from pathlib import Path

from bundle_archive_contract_support import (
    bundled_java_command,
    joined_path,
    normalize_newlines,
    require,
    require_executable,
    require_match,
    require_no_match,
)


def verify_bundle_root_files(bundle_root: Path, contract: dict[str, object]) -> None:
    host_bundle_target = contract["bundleLayout"]["hostBundleTarget"]
    assert isinstance(host_bundle_target, dict)
    launcher_path = joined_path(bundle_root, str(host_bundle_target["launcherPath"]))
    application_jar = bundle_root / "lib" / "app" / "fingrind.jar"
    native_library = (
        bundle_root / "lib" / "native" / str(host_bundle_target["sqliteLibraryFileName"])
    )
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

    if os.name != "nt":
        require_executable(launcher_path, f"missing executable bundle launcher at {launcher_path}")
        require_executable(
            java_command, f"missing executable bundled Java runtime at {java_command}"
        )


def verify_bundle_manifest(bundle_root: Path, contract: dict[str, object]) -> None:
    readme_text = normalize_newlines((bundle_root / "README.md").read_text(encoding="utf-8"))
    require_match(
        readme_text, r"^# FinGrind ", "bundle README did not start with the FinGrind title"
    )
    require_match(
        readme_text,
        r"bundle-manifest\.json",
        "bundle README did not mention the machine-readable bundle manifest",
    )
    require_match(
        readme_text,
        r"quick-start-request\.json",
        "bundle README did not mention the bundled quick-start request example",
    )
    _verify_quick_start_request(bundle_root)
    _verify_manifest_contract(bundle_root, contract)


def _verify_quick_start_request(bundle_root: Path) -> None:
    quick_start_request = json.loads(
        (bundle_root / "quick-start-request.json").read_text(encoding="utf-8")
    )
    require(
        quick_start_request.get("entryKind") == "CASH_REVENUE",
        "bundled quick-start request did not publish the canonical first-post entry kind",
    )
    require(
        quick_start_request.get("cashAccountCode") == "cash",
        "bundled quick-start request did not use the seeded cash account",
    )
    require(
        quick_start_request.get("revenueAccountCode") == "service-revenue",
        "bundled quick-start request did not use the seeded service-revenue account",
    )
    provenance = quick_start_request.get("provenance")
    require(
        isinstance(provenance, dict) and provenance.get("idempotencyKey") == "quick-start-idem-1",
        "bundled quick-start request did not publish the canonical quick-start idempotency key placeholder",
    )


def _verify_manifest_contract(bundle_root: Path, contract: dict[str, object]) -> None:
    manifest_text = normalize_newlines(
        (bundle_root / "bundle-manifest.json").read_text(encoding="utf-8")
    )
    compact_manifest_text = re.sub(r"\s+", "", manifest_text)
    for forbidden_key in (
        "discoveryCommands",
        "administrationCommands",
        "queryCommands",
        "writeCommands",
    ):
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
            manifest["managedSqlite"]["bookProtectionMode"]
            == runtime_surface["bookProtectionMode"],
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
            manifest["bootstrap"]["machineReadableContractCommand"][-1]
            == operation_ids["capabilities"],
            "bundle manifest did not publish the canonical machine-readable contract command",
        ),
        (
            manifest["bootstrap"]["requestTemplateCommand"][-1]
            == operation_ids["printRequestTemplate"],
            "bundle manifest did not publish the canonical request-template bootstrap command",
        ),
        (
            manifest["bootstrap"]["planTemplateCommand"][-1] == operation_ids["printPlanTemplate"],
            "bundle manifest did not publish the canonical plan-template bootstrap command",
        ),
    ]
    for passed, message in checks:
        require(bool(passed), message)
