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


def read_json(path: pathlib.Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


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
                "sqliteBundleHomeSystemProperty": "sqliteBundleHomeSystemProperty",
            },
            "protectedBookFormat": {
                "cipher": "cipher",
                "legacyMode": "legacyMode",
                "pageSize": "pageSize",
                "reservedBytes": "reservedBytes",
                "legacyPageSize": "legacyPageSize",
                "kdfIter": "kdfIter",
                "plaintextHeaderSize": "plaintextHeaderSize",
            },
            "publicDistribution": {
                "supportedPublicCliBundleTargets": "supportedPublicCliBundleTargets",
                "unsupportedPublicCliBundleTargets": "unsupportedPublicCliBundleTargets",
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
            },
            "releasePublication": {
                "workflowDispatchHelperRef": "workflowDispatchHelperRef",
                "bundleOutputArchivePrefixes": "bundleOutputArchivePrefixes",
                "bundleOutputChecksumPrefixes": "bundleOutputChecksumPrefixes",
                "publicBundleBuildTargets": "publicBundleBuildTargets",
                "runnerLabel": "runnerLabel",
                "expectedRunnerOs": "expectedRunnerOs",
                "expectedRunnerArch": "expectedRunnerArch",
                "requiredCiWorkflowName": "requiredCiWorkflowName",
                "requiredCiWorkflowPath": "requiredCiWorkflowPath",
                "requiredCiGateJobName": "requiredCiGateJobName",
                "requiredCiJobNames": "requiredCiJobNames",
                "containerRegistry": "containerRegistry",
                "containerImageName": "containerImageName",
                "containerRunnerLabel": "containerRunnerLabel",
                "containerPlatforms": "containerPlatforms",
                "latestPublicationPolicy": "latestPublicationPolicy",
            },
            "operationIdContract": {
                "help": "HELP",
                "version": "VERSION",
                "capabilities": "CAPABILITIES",
                "printRequestTemplate": "PRINT_REQUEST_TEMPLATE",
                "printPlanTemplate": "PRINT_PLAN_TEMPLATE",
                "generateBookKeyFile": "GENERATE_BOOK_KEY_FILE",
                "openBook": "OPEN_BOOK",
                "rekeyBook": "REKEY_BOOK",
                "backupBook": "BACKUP_BOOK",
                "restoreBook": "RESTORE_BOOK",
                "inspectRekeyRollback": "INSPECT_REKEY_ROLLBACK",
                "deleteRekeyRollback": "DELETE_REKEY_ROLLBACK",
                "restoreRekeyRollback": "RESTORE_REKEY_ROLLBACK",
                "declareAccount": "DECLARE_ACCOUNT",
                "transferPeriodResult": "TRANSFER_PERIOD_RESULT",
                "inspectBook": "INSPECT_BOOK",
                "listAccounts": "LIST_ACCOUNTS",
                "getPosting": "GET_POSTING",
                "listPostings": "LIST_POSTINGS",
                "accountBalance": "ACCOUNT_BALANCE",
                "trialBalance": "TRIAL_BALANCE",
                "accountLedger": "ACCOUNT_LEDGER",
                "periodSummary": "PERIOD_SUMMARY",
                "financialPosition": "FINANCIAL_POSITION",
                "incomeStatement": "INCOME_STATEMENT",
                "changesInEquity": "CHANGES_IN_EQUITY",
                "executePlan": "EXECUTE_PLAN",
                "preflightEntry": "PREFLIGHT_ENTRY",
                "postEntry": "POST_ENTRY",
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
            "requiredMinimumSqliteVersion": "3.53.1",
            "requiredSqlite3mcVersion": "2.3.4",
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
        protocol_root / "release-publication-contract.json",
        {
            "workflowDispatchHelperRef": "main",
            "bundleOutputArchivePrefixes": [
                "FINGRIND_BUNDLE_ARCHIVE=",
                "FinGrind bundle archive: ",
            ],
            "bundleOutputChecksumPrefixes": [
                "FINGRIND_BUNDLE_CHECKSUM=",
                "FinGrind bundle checksum: ",
            ],
            "publicBundleBuildTargets": {
                "linux-x86_64": {
                    "runnerLabel": "ubuntu-24.04",
                    "expectedRunnerOs": "Linux",
                    "expectedRunnerArch": "x86_64",
                }
            },
            "requiredCiWorkflowName": "CI",
            "requiredCiWorkflowPath": ".github/workflows/ci.yml",
            "requiredCiGateJobName": "Gate",
            "requiredCiJobNames": ["Check", "Gate"],
            "containerRegistry": "ghcr.io",
            "containerImageName": "fingrind",
            "containerRunnerLabel": "ubuntu-24.04",
            "containerPlatforms": ["linux/amd64", "linux/arm64"],
            "latestPublicationPolicy": "newest-stable-release-only",
        },
    )
    write_json(
        protocol_root / "operation-id-contract.json",
        {
            "HELP": "help",
            "VERSION": "version",
            "CAPABILITIES": "capabilities",
            "PRINT_REQUEST_TEMPLATE": "print-request-template",
            "PRINT_PLAN_TEMPLATE": "print-plan-template",
            "GENERATE_BOOK_KEY_FILE": "generate-book-key-file",
            "OPEN_BOOK": "open-book",
            "REKEY_BOOK": "rekey-book",
            "BACKUP_BOOK": "backup-book",
            "RESTORE_BOOK": "restore-book",
            "INSPECT_REKEY_ROLLBACK": "inspect-rekey-rollback",
            "DELETE_REKEY_ROLLBACK": "delete-rekey-rollback",
            "RESTORE_REKEY_ROLLBACK": "restore-rekey-rollback",
            "DECLARE_ACCOUNT": "declare-account",
            "TRANSFER_PERIOD_RESULT": "transfer-period-result",
            "INSPECT_BOOK": "inspect-book",
            "LIST_ACCOUNTS": "list-accounts",
            "GET_POSTING": "get-posting",
            "LIST_POSTINGS": "list-postings",
            "ACCOUNT_BALANCE": "account-balance",
            "TRIAL_BALANCE": "trial-balance",
            "ACCOUNT_LEDGER": "account-ledger",
            "PERIOD_SUMMARY": "period-summary",
            "FINANCIAL_POSITION": "financial-position",
            "INCOME_STATEMENT": "income-statement",
            "CHANGES_IN_EQUITY": "changes-in-equity",
            "EXECUTE_PLAN": "execute-plan",
            "PREFLIGHT_ENTRY": "preflight-entry",
            "POST_ENTRY": "post-entry",
        },
    )

    loaded = contract_values.load_contract_values(
        fixture_root, os_name="Windows 11", architecture="ARM64"
    )
    assert loaded["managedSqlite"]["requiredMinimumSqliteVersion"] == "3.53.1"
    assert loaded["protectedBookFormat"]["cipher"] == "chacha20"
    assert loaded["protectedBookFormat"]["legacyMode"] is False
    assert loaded["protectedBookFormat"]["pageSize"] == 4096
    assert loaded["protectedBookFormat"]["reservedBytes"] == 32
    assert loaded["managedSqlite"]["requiredSqlite3mcVersion"] == "2.3.4"
    assert (
        loaded["managedSqlite"]["requiredSqliteSourceId"]
        == "2026-04-09 sqlite-source-id"
    )
    assert loaded["managedSqlite"]["requiredCompileOptions"] == [
        "THREADSAFE=1",
        "OMIT_LOAD_EXTENSION",
        "TEMP_STORE=3",
        "SECURE_DELETE",
    ]
    assert loaded["managedSqlite"]["forbiddenCompileOptions"] == ["USE_URI"]
    assert loaded["managedSqlite"]["requiresSecureMemorySupport"] is True
    assert loaded["runtimeEnvironment"]["sourceCheckoutJava"] == "26+"
    assert loaded["bundleLayout"]["hostBundleTarget"]["classifier"] == "windows-aarch64"
    assert loaded["bundleLayout"]["hostBundleTarget"]["archiveFormat"] == "zip"
    assert loaded["bundleLayout"]["hostBundleTarget"]["launcherPath"] == "bin/fingrind.ps1"
    assert loaded["publicDistribution"]["unsupportedPublicCliBundleTargets"] == [
        "windows-aarch64"
    ]
    assert loaded["releasePublication"]["workflowDispatchHelperRef"] == "main"
    assert loaded["releasePublication"]["publicBundleBuildTargets"] == {
        "linux-x86_64": {
            "runnerLabel": "ubuntu-24.04",
            "expectedRunnerOs": "Linux",
            "expectedRunnerArch": "x86_64",
        }
    }
    assert loaded["releasePublication"]["requiredCiWorkflowName"] == "CI"
    assert loaded["releasePublication"]["containerPlatforms"] == [
        "linux/amd64",
        "linux/arm64",
    ]
    assert loaded["operationIds"]["version"] == "version"
    assert loaded["operationIds"]["generateBookKeyFile"] == "generate-book-key-file"

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

    write_json(
        protocol_root / "public-distribution-contract.json",
        {
            "supportedPublicCliBundleTargets": ["linux-x86_64"],
            "unsupportedPublicCliBundleTargets": ["windows-aarch64"],
        },
    )
    write_json(
        protocol_root / "release-publication-contract.json",
        {
            **read_json(protocol_root / "release-publication-contract.json"),
            "publicBundleBuildTargets": {
                "linux-x86_64": {
                    "runnerLabel": "ubuntu-24.04",
                    "expectedRunnerOs": "Linux",
                    "expectedRunnerArch": "x86_64",
                },
                "windows-aarch64": {
                    "runnerLabel": "windows-2022",
                    "expectedRunnerOs": "Windows",
                    "expectedRunnerArch": "ARM64",
                },
            },
        },
    )
    try:
        contract_values.load_contract_values(
            fixture_root, os_name="Linux", architecture="x86_64"
        )
    except ValueError as exc:
        assert "unsupported build targets" in str(exc)
    else:
        raise AssertionError("expected unsupported release build-target validation failure")

    write_json(
        protocol_root / "release-publication-contract.json",
        {
            "workflowDispatchHelperRef": "main",
            "bundleOutputArchivePrefixes": [
                "FINGRIND_BUNDLE_ARCHIVE=",
                "FinGrind bundle archive: ",
            ],
            "bundleOutputChecksumPrefixes": [
                "FINGRIND_BUNDLE_CHECKSUM=",
                "FinGrind bundle checksum: ",
            ],
            "publicBundleBuildTargets": {
                "linux-x86_64": {
                    "runnerLabel": "ubuntu-24.04",
                    "expectedRunnerOs": "Linux",
                    "expectedRunnerArch": "x86_64",
                }
            },
            "requiredCiWorkflowName": "CI",
            "requiredCiWorkflowPath": ".github/workflows/ci.yml",
            "requiredCiGateJobName": "Gate",
            "requiredCiJobNames": ["Check", "Gate"],
            "containerRegistry": "ghcr.io",
            "containerImageName": "fingrind",
            "containerRunnerLabel": "ubuntu-24.04",
            "containerPlatforms": ["linux/amd64", "linux/arm64"],
            "latestPublicationPolicy": "newest-stable-release-only",
        },
    )
    write_json(
        protocol_root / "operation-id-contract.json",
        {
            **read_json(protocol_root / "operation-id-contract.json"),
            "SURPRISE_OPERATION": "surprise-operation",
        },
    )
    try:
        contract_values.load_contract_values(
            fixture_root, os_name="Linux", architecture="x86_64"
        )
    except ValueError as exc:
        assert "declared an enum without one canonical semantic key" in str(exc)
    else:
        raise AssertionError("expected undeclared operation-id semantic-key validation failure")

    write_json(
        protocol_root / "operation-id-contract.json",
        {
            "HELP": "help",
            "VERSION": "version",
            "CAPABILITIES": "capabilities",
            "PRINT_REQUEST_TEMPLATE": "print-request-template",
            "PRINT_PLAN_TEMPLATE": "print-plan-template",
            "GENERATE_BOOK_KEY_FILE": "generate-book-key-file",
            "OPEN_BOOK": "open-book",
            "REKEY_BOOK": "rekey-book",
            "BACKUP_BOOK": "backup-book",
            "RESTORE_BOOK": "restore-book",
            "INSPECT_REKEY_ROLLBACK": "inspect-rekey-rollback",
            "DELETE_REKEY_ROLLBACK": "delete-rekey-rollback",
            "RESTORE_REKEY_ROLLBACK": "restore-rekey-rollback",
            "DECLARE_ACCOUNT": "declare-account",
            "TRANSFER_PERIOD_RESULT": "transfer-period-result",
            "INSPECT_BOOK": "inspect-book",
            "LIST_ACCOUNTS": "list-accounts",
            "GET_POSTING": "get-posting",
            "LIST_POSTINGS": "list-postings",
            "ACCOUNT_BALANCE": "account-balance",
            "TRIAL_BALANCE": "trial-balance",
            "ACCOUNT_LEDGER": "account-ledger",
            "PERIOD_SUMMARY": "period-summary",
            "FINANCIAL_POSITION": "financial-position",
            "INCOME_STATEMENT": "income-statement",
            "CHANGES_IN_EQUITY": "changes-in-equity",
            "EXECUTE_PLAN": "execute-plan",
            "PREFLIGHT_ENTRY": "preflight-entry",
            "POST_ENTRY": "post-entry",
        },
    )
    (
        fixture_root
        / "contract/build/generated-resources/protocol/dev/erst/fingrind/contract/protocol/runtime-environment-contract.json"
    ).unlink()
    loaded_from_build_metadata = contract_values.load_contract_values(
        fixture_root, os_name="Linux", architecture="x86_64"
    )
    assert loaded_from_build_metadata["runtimeEnvironment"]["sourceCheckoutJava"] == "26+"
PY

printf 'contract values reader regression: success\n'
