#!/usr/bin/env bash
# Reproduce and guard the shell-side contract reader against drift away from the canonical JSON owners.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

resolve_script_dir() {
    local source_path="${BASH_SOURCE[0]}"
    while [[ -h "${source_path}" ]]; do
        local source_dir
        source_dir="$(cd -P -- "$(dirname -- "${source_path}")" && pwd)"
        source_path="$(readlink "${source_path}")"
        if [[ "${source_path}" != /* ]]; then
            source_path="${source_dir}/${source_path}"
        fi
    done
    cd -P -- "$(dirname -- "${source_path}")" && pwd
}

readonly script_dir="$(resolve_script_dir)"

python3 - <<'PY' "${script_dir}"
import json
import pathlib
import sys
import tempfile

sys.path.insert(0, sys.argv[1])
import contract_values  # noqa: E402


def write_json(path: pathlib.Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


with tempfile.TemporaryDirectory(prefix="fingrind-contract-values-") as fixture_root_raw:
    fixture_root = pathlib.Path(fixture_root_raw)
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
                "sqliteLibraryEnvironmentVariable": "sqliteLibraryEnvironmentVariable",
                "sqliteBundleHomeSystemProperty": "sqliteBundleHomeSystemProperty",
            },
            "publicDistribution": {
                "supportedPublicCliBundleTargets": "supportedPublicCliBundleTargets",
                "unsupportedPublicCliBundleTargets": "unsupportedPublicCliBundleTargets",
            },
            "managedSqlite": {
                "requiredMinimumSqliteVersion": "requiredMinimumSqliteVersion",
                "requiredSqlite3mcVersion": "requiredSqlite3mcVersion",
            },
            "bundleLayout": {
                "bundleTargets": "bundleTargets",
                "operatingSystemId": "operatingSystemId",
                "architectureId": "architectureId",
                "archiveFormat": "archiveFormat",
                "launcherPath": "launcherPath",
                "launcherCommand": "launcherCommand",
                "sqliteLibraryFileName": "sqliteLibraryFileName",
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
            "sqliteLibraryEnvironmentVariable": "FINGRIND_SQLITE_LIBRARY",
            "sqliteBundleHomeSystemProperty": "fingrind.bundle.home",
        },
    )
    write_json(
        protocol_root / "managed-sqlite-contract.json",
        {
            "requiredMinimumSqliteVersion": "3.53.0",
            "requiredSqlite3mcVersion": "2.3.3",
        },
    )
    write_json(
        protocol_root / "bundle-layout-contract.json",
        {
            "bundleTargets": {
                "linux-x86_64": {
                    "operatingSystemId": "linux",
                    "architectureId": "x86_64",
                    "archiveFormat": "tar.gz",
                    "launcherPath": "bin/fingrind",
                    "launcherCommand": "./bin/fingrind",
                    "sqliteLibraryFileName": "libsqlite3.so.0",
                },
                "windows-aarch64": {
                    "operatingSystemId": "windows",
                    "architectureId": "aarch64",
                    "archiveFormat": "zip",
                    "launcherPath": "bin/fingrind.ps1",
                    "launcherCommand": ".\\bin\\fingrind.ps1",
                    "sqliteLibraryFileName": "sqlite3.dll",
                },
            }
        },
    )
    write_json(
        protocol_root / "public-distribution-contract.json",
        {
            "supportedPublicCliBundleTargets": ["linux-x86_64"],
            "unsupportedPublicCliBundleTargets": ["windows-aarch64"],
        },
    )
    write_json(
        protocol_root / "operation-id-contract.json",
        {
            "HELP": "help",
            "CAPABILITIES": "capabilities",
            "PRINT_REQUEST_TEMPLATE": "print-request-template",
            "PRINT_PLAN_TEMPLATE": "print-plan-template",
        },
    )

    loaded = contract_values.load_contract_values(
        fixture_root, os_name="Windows 11", architecture="ARM64"
    )
    assert loaded["managedSqlite"]["requiredMinimumSqliteVersion"] == "3.53.0"
    assert loaded["managedSqlite"]["requiredSqlite3mcVersion"] == "2.3.3"
    assert loaded["bundleLayout"]["hostBundleTarget"]["classifier"] == "windows-aarch64"
    assert loaded["bundleLayout"]["hostBundleTarget"]["archiveFormat"] == "zip"
    assert loaded["bundleLayout"]["hostBundleTarget"]["launcherPath"] == "bin/fingrind.ps1"
    assert loaded["publicDistribution"]["unsupportedPublicCliBundleTargets"] == [
        "windows-aarch64"
    ]

    write_json(
        protocol_root / "public-distribution-contract.json",
        {
            "supportedPublicCliBundleTargets": ["linux-x86_64", "plan9-x86"],
            "unsupportedPublicCliBundleTargets": [],
        },
    )
    try:
        contract_values.load_contract_values(
            fixture_root, os_name="Linux", architecture="x86_64"
        )
    except ValueError as exc:
        assert "undeclared bundle target" in str(exc)
    else:
        raise AssertionError("expected undeclared bundle target validation failure")
PY

printf 'contract values reader regression: success\n'
