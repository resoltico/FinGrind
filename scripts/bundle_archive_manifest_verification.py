from __future__ import annotations

import json
import re
from pathlib import Path

from bundle_archive_contract_support import (
    load_bundle_manifest,
    normalize_newlines,
    require,
    require_match,
    require_no_match,
    resolve_bundle_target,
)

_GLIBC_VERSION_PATTERN = re.compile(rb"GLIBC_(\d+)\.(\d+)")


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
        quick_start_request.get("entryKind") == "SALE",
        "bundled quick-start request did not publish the canonical sale entry kind",
    )
    require(
        quick_start_request.get("recipeKind") is None,
        "bundled quick-start request leaked one retired recipe field into the canonical first-post sample",
    )
    require(
        quick_start_request.get("cashAccountCode") == "cash",
        "bundled quick-start request did not seed the canonical cash account",
    )
    require(
        quick_start_request.get("revenueAccountCode") == "service-revenue",
        "bundled quick-start request did not seed the canonical revenue account",
    )
    require(
        quick_start_request.get("amount") == {"currencyCode": "EUR", "minorUnits": "1000"},
        "bundled quick-start request did not publish the canonical sale amount",
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
    manifest = load_bundle_manifest(bundle_root)
    runtime_surface = contract["runtimeSurface"]
    public_distribution = contract["publicDistribution"]
    managed_sqlite = contract["managedSqlite"]
    operation_ids = contract["operationIds"]
    classifier, bundle_target = resolve_bundle_target(contract, manifest)
    assert isinstance(manifest, dict)
    assert isinstance(runtime_surface, dict)
    assert isinstance(public_distribution, dict)
    assert isinstance(managed_sqlite, dict)
    assert isinstance(operation_ids, dict)
    assert isinstance(bundle_target, dict)

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
            manifest["bundleTarget"]["classifier"] == classifier,
            "bundle manifest did not report the declared bundle classifier",
        ),
        (
            manifest["bundleTarget"]["compatibilityLabel"] == bundle_target["compatibilityLabel"],
            "bundle manifest did not report the canonical bundle compatibility label",
        ),
        (
            manifest["archiveFormat"] == bundle_target["archiveFormat"],
            "bundle manifest did not report the platform-native archive format",
        ),
        (
            manifest["launcher"] == bundle_target["launcherPath"],
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
    if bundle_target["operatingSystemId"] == "linux":
        checks.extend(_linux_glibc_floor_checks(bundle_root, manifest, bundle_target))
    for passed, message in checks:
        require(bool(passed), message)


def _linux_glibc_floor_checks(
    bundle_root: Path,
    manifest: dict[str, object],
    bundle_target: dict[str, object],
) -> list[tuple[bool, str]]:
    detected_glibc_floor, detected_glibc_floor_path = _detect_bundle_glibc_floor(bundle_root)
    return [
        (
            manifest["bundleTarget"]["minimumGlibcVersion"] == bundle_target["minimumGlibcVersion"],
            "bundle manifest did not report the canonical minimum glibc version",
        ),
        (
            detected_glibc_floor == bundle_target["minimumGlibcVersion"],
            "bundle ELF payload required glibc "
            + str(detected_glibc_floor)
            + " via "
            + str(detected_glibc_floor_path)
            + " instead of the declared floor "
            + str(bundle_target["minimumGlibcVersion"]),
        ),
    ]


def _detect_bundle_glibc_floor(bundle_root: Path) -> tuple[str | None, Path | None]:
    highest_version: tuple[int, int] | None = None
    highest_path: Path | None = None
    for path in bundle_root.rglob("*"):
        if not path.is_file():
            continue
        data = path.read_bytes()
        if not data.startswith(b"\x7fELF"):
            continue
        for match in _GLIBC_VERSION_PATTERN.finditer(data):
            candidate = (int(match.group(1)), int(match.group(2)))
            if highest_version is None or candidate > highest_version:
                highest_version = candidate
                highest_path = path.relative_to(bundle_root)
    if highest_version is None:
        return None, None
    return f"{highest_version[0]}.{highest_version[1]}", highest_path
