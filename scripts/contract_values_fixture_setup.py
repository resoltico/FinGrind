from __future__ import annotations

import pathlib

from contract_values_regression_support import (
    bundle_layout_targets,
    operation_id_contract_payload,
    publication_entry,
    write_bundle_publication_contract,
    write_json,
)


def create_fixture(fixture_root: pathlib.Path) -> tuple[pathlib.Path, pathlib.Path]:
    protocol_root = fixture_root / "contract/src/main/resources/dev/erst/fingrind/contract/protocol"

    write_json(
        protocol_root / "contract-schema-keys.json",
        {
            "runtimeSurface": {
                "directJavaRuntimeDistribution": "directJavaRuntimeDistribution",
                "sourceCheckoutRuntimeDistribution": "sourceCheckoutRuntimeDistribution",
                "containerRuntimeDistribution": "containerRuntimeDistribution",
                "bundleRuntimeDistribution": "bundleRuntimeDistribution",
                "publicCliDistribution": "publicCliDistribution",
                "storageDriver": "storageDriver",
                "storageEngine": "storageEngine",
                "bookProtectionMode": "bookProtectionMode",
                "defaultBookCipher": "defaultBookCipher",
                "sqliteLibraryMode": "sqliteLibraryMode",
                "sqliteBundleHomeSystemProperty": "sqliteBundleHomeSystemProperty",
            },
            "protectedBookFormat": {
                "applicationId": "bookApplicationId",
                "formatVersion": "bookFormatVersion",
                "cipher": "cipher",
                "legacyMode": "legacyMode",
                "pageSize": "pageSize",
                "reservedBytes": "reservedBytes",
                "legacyPageSize": "legacyPageSize",
                "kdfIter": "kdfIter",
                "plaintextHeaderSize": "plaintextHeaderSize",
            },
            "managedSqlite": {
                "requiredMinimumSqliteVersion": "requiredMinimumSqliteVersion",
                "requiredSqlite3mcVersion": "requiredSqlite3mcVersion",
                "requiredSqliteSourceId": "requiredSqliteSourceId",
                "requiredCompileOptions": "requiredCompileOptions",
                "forbiddenCompileOptions": "forbiddenCompileOptions",
                "requiresSecureMemorySupport": "requiresSecureMemorySupport",
            },
            "runtimeEnvironment": {
                "sourceCheckoutJava": "sourceCheckoutJava",
            },
            "bundleLayout": {
                "bundleTargets": "bundleTargets",
                "operatingSystemId": "operatingSystemId",
                "architectureId": "architectureId",
                "archiveFormat": "archiveFormat",
                "launcherPath": "launcherPath",
                "launcherCommand": "launcherCommand",
                "sqliteLibraryFileName": "sqliteLibraryFileName",
                "compatibilityLabel": "compatibilityLabel",
                "minimumGlibcVersion": "minimumGlibcVersion",
                "compatibilitySmokeContainerImage": "compatibilitySmokeContainerImage",
            },
            "bundlePublication": {
                "bundleTargets": "bundleTargets",
                "status": "status",
            },
            "releasePublication": {
                "requiredCiWorkflowName": "requiredCiWorkflowName",
                "requiredCiWorkflowPath": "requiredCiWorkflowPath",
                "requiredCiGateJobName": "requiredCiGateJobName",
                "requiredCiJobNames": "requiredCiJobNames",
                "containerRegistry": "containerRegistry",
                "containerImageName": "containerImageName",
                "containerStagingImageName": "containerStagingImageName",
                "containerPlatforms": "containerPlatforms",
                "latestPublicationPolicy": "latestPublicationPolicy",
            },
            "operationIdContract": {
                "help": "HELP",
                "capabilities": "CAPABILITIES",
                "printRequestTemplate": "PRINT_REQUEST_TEMPLATE",
                "printPlanTemplate": "PRINT_PLAN_TEMPLATE",
            },
        },
    )
    write_json(
        protocol_root / "runtime-surface-contract.json",
        {
            "directJavaRuntimeDistribution": "direct-java-invocation",
            "sourceCheckoutRuntimeDistribution": "source-checkout-gradle",
            "containerRuntimeDistribution": "container-image",
            "bundleRuntimeDistribution": "self-contained-bundle",
            "publicCliDistribution": "self-contained-bundle",
            "storageDriver": "sqlite-ffm-sqlite3mc",
            "storageEngine": "sqlite",
            "bookProtectionMode": "required",
            "defaultBookCipher": "chacha20",
            "sqliteLibraryMode": "managed-only",
            "sqliteBundleHomeSystemProperty": "fingrind.bundle.home",
        },
    )
    write_json(
        protocol_root / "protected-book-format-contract.json",
        {
            "bookApplicationId": 1_179_079_236,
            "bookFormatVersion": 17,
            "cipher": "chacha20",
            "legacyMode": False,
            "pageSize": 4096,
            "reservedBytes": 32,
            "legacyPageSize": 4096,
            "kdfIter": 64007,
            "plaintextHeaderSize": 0,
        },
    )
    write_json(
        protocol_root / "managed-sqlite-contract.json",
        {
            "requiredMinimumSqliteVersion": "3.53.4",
            "requiredSqlite3mcVersion": "2.5.1",
            "requiredSqliteSourceId": "2026-04-09 sqlite-source-id",
            "requiredCompileOptions": [
                "THREADSAFE=1",
                "OMIT_LOAD_EXTENSION",
                "TEMP_STORE=3",
                "SECURE_DELETE",
            ],
            "forbiddenCompileOptions": ["USE_URI"],
            "requiresSecureMemorySupport": True,
        },
    )
    write_json(
        fixture_root
        / "contract/build/generated-resources/protocol/dev/erst/fingrind/contract/protocol/runtime-environment-contract.json",
        {
            "sourceCheckoutJava": "26+",
        },
    )
    build_properties_path = fixture_root / "gradle/fingrind-build.properties"
    build_properties_path.parent.mkdir(parents=True, exist_ok=True)
    build_properties_path.write_text("fingrindJavaVersion=26\n", encoding="utf-8")
    write_json(
        protocol_root / "bundle-layout-contract.json",
        {"bundleTargets": bundle_layout_targets()},
    )
    write_bundle_publication_contract(
        protocol_root,
        {
            "linux-x86_64": publication_entry("published"),
            "windows-aarch64": publication_entry("not-published"),
        },
    )
    write_json(
        protocol_root / "release-publication-contract.json",
        {
            "requiredCiWorkflowName": "CI",
            "requiredCiWorkflowPath": ".github/workflows/ci.yml",
            "requiredCiGateJobName": "Gate",
            "requiredCiJobNames": ["Check", "Gate"],
            "containerRegistry": "ghcr.io",
            "containerImageName": "fingrind",
            "containerStagingImageName": "fingrind-publication-staging",
            "containerPlatforms": ["linux/amd64"],
            "latestPublicationPolicy": "newest-stable-release-only",
        },
    )
    write_json(
        protocol_root / "operation-id-contract.json",
        operation_id_contract_payload(),
    )
    return fixture_root, protocol_root
