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


def publication_entry(
    status: str,
    *,
    runner_label=None,
):
    payload = {"status": status}
    if runner_label is not None:
        payload["runnerLabel"] = runner_label
    return payload


def write_bundle_publication_contract(
    protocol_root: pathlib.Path, bundle_targets
):
    write_json(protocol_root / "bundle-publication-contract.json", {"bundleTargets": bundle_targets})


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
                "runnerLabel": "runnerLabel",
            },
            "releasePublication": {
                "requiredCiWorkflowName": "requiredCiWorkflowName",
                "requiredCiWorkflowPath": "requiredCiWorkflowPath",
                "requiredCiGateJobName": "requiredCiGateJobName",
                "requiredCiJobNames": "requiredCiJobNames",
                "containerRegistry": "containerRegistry",
                "containerImageName": "containerImageName",
                "containerStagingImageName": "containerStagingImageName",
                "containerRunnerLabel": "containerRunnerLabel",
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
            "requiredMinimumSqliteVersion": "3.53.2",
            "requiredSqlite3mcVersion": "2.3.5",
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
                    "compatibilityLabel": "glibc 2.34+ Linux x86_64",
                    "minimumGlibcVersion": "2.34",
                    "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof",
                },
                "windows-aarch64": {
                    "operatingSystemId": "windows",
                    "architectureId": "aarch64",
                    "archiveFormat": "zip",
                    "launcherPath": "bin/fingrind.ps1",
                    "launcherCommand": ".\\bin\\fingrind.ps1",
                    "sqliteLibraryFileName": "sqlite3.dll",
                    "compatibilityLabel": "Windows aarch64",
                },
            }
        },
    )
    write_bundle_publication_contract(
        protocol_root,
        {
            "linux-x86_64": publication_entry(
                "published",
                runner_label="ubuntu-24.04",
            ),
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
            "containerRunnerLabel": "ubuntu-24.04",
            "containerPlatforms": ["linux/amd64"],
            "latestPublicationPolicy": "newest-stable-release-only",
        },
    )
    write_json(
        protocol_root / "operation-id-contract.json",
        {
            "HELP": "help",
            "VERSION": "version",
            "CAPABILITIES": "capabilities",
            "ENVIRONMENT": "environment",
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
            "DECLARE_TAX_REGISTRATION": "declare-tax-registration",
            "INTERIM_RESULT_SWEEP": "interim-result-sweep",
            "FISCAL_YEAR_CLOSE": "fiscal-year-close",
            "INSPECT_BOOK": "inspect-book",
            "LIST_ACCOUNTS": "list-accounts",
            "GET_POSTING": "get-posting",
            "LIST_POSTINGS": "list-postings",
            "LIST_TAX_REGISTRATIONS": "list-tax-registrations",
            "TAX_OBLIGATION": "tax-obligation",
            "ACCOUNT_BALANCE": "account-balance",
            "TRIAL_BALANCE": "trial-balance",
            "ACCOUNT_LEDGER": "account-ledger",
            "PERIOD_SUMMARY": "period-summary",
            "FINANCIAL_POSITION": "financial-position",
            "INCOME_STATEMENT": "income-statement",
            "CASH_FLOW_STATEMENT": "cash-flow-statement",
            "CHANGES_IN_EQUITY": "changes-in-equity",
            "EXECUTE_PLAN": "execute-plan",
            "PREFLIGHT_ENTRY": "preflight-entry",
            "POST_ENTRY": "post-entry",
            "RECORD_SALE_SETTLED": "record-sale-settled",
            "RECORD_SALE_ON_CREDIT": "record-sale-on-credit",
            "RECORD_EXPENSE_SETTLED": "record-expense-settled",
            "RECORD_EXPENSE_ON_CREDIT": "record-expense-on-credit",
            "RECORD_RECEIPT": "record-receipt",
            "RECORD_PAYMENT": "record-payment",
            "RECORD_OWNER_CONTRIBUTION": "record-owner-contribution",
            "RECORD_OWNER_WITHDRAWAL": "record-owner-withdrawal",
            "RECORD_OPENING_POSITION": "record-opening-position",
            "RECORD_REVERSAL": "record-reversal",
        },
    )

    loaded = contract_values.load_contract_values(
        fixture_root, os_name="Windows 11", architecture="ARM64"
    )
    assert loaded["managedSqlite"]["requiredMinimumSqliteVersion"] == "3.53.2"
    assert loaded["protectedBookFormat"]["cipher"] == "chacha20"
    assert loaded["protectedBookFormat"]["legacyMode"] is False
    assert loaded["protectedBookFormat"]["pageSize"] == 4096
    assert loaded["protectedBookFormat"]["reservedBytes"] == 32
    assert loaded["managedSqlite"]["requiredSqlite3mcVersion"] == "2.3.5"
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
    assert loaded["bundleLayout"]["targets"]["linux-x86_64"]["compatibilityLabel"] == "glibc 2.34+ Linux x86_64"
    assert loaded["bundleLayout"]["targets"]["linux-x86_64"]["minimumGlibcVersion"] == "2.34"
    assert (
        loaded["bundleLayout"]["targets"]["linux-x86_64"]["compatibilitySmokeContainerImage"]
        == "rockylinux:9@sha256:floor-proof"
    )
    assert loaded["publicDistribution"]["unsupportedPublicCliBundleTargets"] == [
        "windows-aarch64"
    ]
    assert loaded["releasePublication"]["publicBundleBuildTargets"] == {
        "linux-x86_64": {
            "runnerLabel": "ubuntu-24.04",
        }
    }
    assert loaded["releasePublication"]["requiredCiWorkflowName"] == "CI"
    assert loaded["releasePublication"]["containerStagingImageName"] == "fingrind-publication-staging"
    assert loaded["releasePublication"]["containerPlatforms"] == ["linux/amd64"]
    assert loaded["operationIds"]["version"] == "version"
    assert loaded["operationIds"]["environment"] == "environment"
    assert loaded["operationIds"]["generateBookKeyFile"] == "generate-book-key-file"
    assert loaded["operationIds"]["declareTaxRegistration"] == "declare-tax-registration"
    assert loaded["operationIds"]["listTaxRegistrations"] == "list-tax-registrations"
    assert loaded["operationIds"]["taxObligation"] == "tax-obligation"
    assert loaded["operationIds"]["cashFlowStatement"] == "cash-flow-statement"

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
                    "compatibilityLabel": "glibc 2.34+ Linux x86_64",
                    "minimumGlibcVersion": "2.34",
                    "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof",
                },
                "windows-aarch64": {
                    "operatingSystemId": "windows",
                    "architectureId": "aarch64",
                    "archiveFormat": "zip",
                    "launcherPath": "bin/fingrind.ps1",
                    "launcherCommand": ".\\bin\\fingrind.ps1",
                    "sqliteLibraryFileName": "sqlite3.dll",
                    "compatibilityLabel": "Windows aarch64",
                },
            }
        },
    )
    write_bundle_publication_contract(
        protocol_root,
        {
            "linux-x86_64": publication_entry(
                "published",
                runner_label="ubuntu-24.04",
            ),
            "windows-aarch64": publication_entry("experimental"),
        },
    )
    try:
        contract_values.load_contract_values(
            fixture_root, os_name="Linux", architecture="x86_64"
        )
    except ValueError as exc:
        assert "unsupported publication status" in str(exc)
    else:
        raise AssertionError("expected unsupported publication status validation failure")

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
                    "compatibilityLabel": "glibc 2.34+ Linux x86_64",
                    "minimumGlibcVersion": "2.34",
                    "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof",
                },
                "linux-aarch64": {
                    "operatingSystemId": "linux",
                    "architectureId": "aarch64",
                    "archiveFormat": "tar.gz",
                    "launcherPath": "bin/fingrind",
                    "launcherCommand": "./bin/fingrind",
                    "sqliteLibraryFileName": "libsqlite3.so.0",
                    "compatibilityLabel": "glibc 2.34+ Linux aarch64",
                    "minimumGlibcVersion": "2.34",
                    "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof",
                },
                "windows-aarch64": {
                    "operatingSystemId": "windows",
                    "architectureId": "aarch64",
                    "archiveFormat": "zip",
                    "launcherPath": "bin/fingrind.ps1",
                    "launcherCommand": ".\\bin\\fingrind.ps1",
                    "sqliteLibraryFileName": "sqlite3.dll",
                    "compatibilityLabel": "Windows aarch64",
                },
            }
        },
    )
    write_bundle_publication_contract(
        protocol_root,
        {
            "linux-x86_64": publication_entry(
                "published",
                runner_label="ubuntu-24.04",
            ),
            "linux-aarch64": publication_entry(
                "published",
                runner_label="ubuntu-24.04-arm",
            ),
            "windows-aarch64": publication_entry("not-published"),
        },
    )
    write_json(
        protocol_root / "release-publication-contract.json",
        {
            **read_json(protocol_root / "release-publication-contract.json"),
            "containerPlatforms": ["linux/amd64"],
        },
    )
    try:
        contract_values.load_contract_values(
            fixture_root, os_name="Linux", architecture="x86_64"
        )
    except ValueError as exc:
        assert "containerPlatforms must match the supported Linux public bundle targets" in str(exc)
    else:
        raise AssertionError("expected release container-platform validation failure")

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
            "containerRunnerLabel": "ubuntu-24.04",
            "containerPlatforms": ["linux/amd64", "linux/arm64"],
            "latestPublicationPolicy": "newest-stable-release-only",
        },
    )
    write_json(
        protocol_root / "operation-id-contract.json",
        {
            **read_json(protocol_root / "operation-id-contract.json"),
            "SURPRISE__OPERATION": "surprise-operation",
        },
    )
    try:
        contract_values.load_contract_values(
            fixture_root, os_name="Linux", architecture="x86_64"
        )
    except ValueError as exc:
        assert "must be one non-blank upper-snake enum name" in str(exc)
    else:
        raise AssertionError("expected malformed operation-id enum-key validation failure")

    write_json(
        protocol_root / "operation-id-contract.json",
        {
            "HELP": "help",
            "VERSION": "version",
            "CAPABILITIES": "capabilities",
            "ENVIRONMENT": "environment",
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
            "DECLARE_TAX_REGISTRATION": "declare-tax-registration",
            "INTERIM_RESULT_SWEEP": "interim-result-sweep",
            "FISCAL_YEAR_CLOSE": "fiscal-year-close",
            "INSPECT_BOOK": "inspect-book",
            "LIST_ACCOUNTS": "list-accounts",
            "GET_POSTING": "get-posting",
            "LIST_POSTINGS": "list-postings",
            "LIST_TAX_REGISTRATIONS": "list-tax-registrations",
            "TAX_OBLIGATION": "tax-obligation",
            "ACCOUNT_BALANCE": "account-balance",
            "TRIAL_BALANCE": "trial-balance",
            "ACCOUNT_LEDGER": "account-ledger",
            "PERIOD_SUMMARY": "period-summary",
            "FINANCIAL_POSITION": "financial-position",
            "INCOME_STATEMENT": "income-statement",
            "CASH_FLOW_STATEMENT": "cash-flow-statement",
            "CHANGES_IN_EQUITY": "changes-in-equity",
            "EXECUTE_PLAN": "execute-plan",
            "PREFLIGHT_ENTRY": "preflight-entry",
            "POST_ENTRY": "post-entry",
            "RECORD_SALE_SETTLED": "record-sale-settled",
            "RECORD_SALE_ON_CREDIT": "record-sale-on-credit",
            "RECORD_EXPENSE_SETTLED": "record-expense-settled",
            "RECORD_EXPENSE_ON_CREDIT": "record-expense-on-credit",
            "RECORD_RECEIPT": "record-receipt",
            "RECORD_PAYMENT": "record-payment",
            "RECORD_OWNER_CONTRIBUTION": "record-owner-contribution",
            "RECORD_OWNER_WITHDRAWAL": "record-owner-withdrawal",
            "RECORD_OPENING_POSITION": "record-opening-position",
            "RECORD_REVERSAL": "record-reversal",
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
