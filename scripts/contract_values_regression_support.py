from __future__ import annotations

import json
from pathlib import Path


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def read_json(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def assert_declared_fixture_formats_are_current(
    fixtures_root: Path, expected_format_version: int
) -> None:
    for metadata_path in sorted(fixtures_root.glob("*.metadata.json")):
        document = read_json(metadata_path)
        if not isinstance(document, dict):
            raise TypeError(
                f"protected-book fixture metadata must be a JSON object: {metadata_path}"
            )
        declared_format_version = document.get("bookFormatVersion")
        if declared_format_version is None:
            continue
        if type(declared_format_version) is not int:
            raise AssertionError(
                f"protected-book fixture {metadata_path.name} must declare one integer "
                "bookFormatVersion"
            )
        if declared_format_version != expected_format_version:
            raise AssertionError(
                f"protected-book fixture {metadata_path.name} declares retired format "
                f"{declared_format_version}; current hard-break format is "
                f"{expected_format_version}"
            )


def publication_entry(status: str) -> dict[str, str]:
    return {"status": status}


def write_bundle_publication_contract(protocol_root: Path, bundle_targets: object) -> None:
    write_json(
        protocol_root / "bundle-publication-contract.json",
        {"bundleTargets": bundle_targets},
    )


def bundle_layout_targets(*, include_linux_aarch64: bool = False) -> dict[str, dict[str, str]]:
    targets = {
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
    if include_linux_aarch64:
        targets["linux-aarch64"] = {
            "operatingSystemId": "linux",
            "architectureId": "aarch64",
            "archiveFormat": "tar.gz",
            "launcherPath": "bin/fingrind",
            "launcherCommand": "./bin/fingrind",
            "sqliteLibraryFileName": "libsqlite3.so.0",
            "compatibilityLabel": "glibc 2.34+ Linux aarch64",
            "minimumGlibcVersion": "2.34",
            "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof",
        }
    return targets


def operation_id_contract_payload() -> dict[str, str]:
    payload = json.loads(
        """
        {
          "HELP": "help", "VERSION": "version",
          "CAPABILITIES": "capabilities", "ENVIRONMENT": "environment",
          "PRINT_REQUEST_TEMPLATE": "print-request-template", "PRINT_PLAN_TEMPLATE": "print-plan-template",
          "GENERATE_BOOK_KEY_FILE": "generate-book-key-file", "OPEN_BOOK": "open-book",
          "REKEY_BOOK": "rekey-book", "BACKUP_BOOK": "backup-book",
          "RESTORE_BOOK": "restore-book", "DECLARE_ACCOUNT": "declare-account",
          "DECLARE_TAX_REGISTRATION": "declare-tax-registration", "INTERIM_RESULT_SWEEP": "interim-result-sweep",
          "FISCAL_YEAR_CLOSE": "fiscal-year-close", "INSPECT_BOOK": "inspect-book",
          "LIST_ACCOUNTS": "list-accounts", "GET_POSTING": "get-posting",
          "LIST_POSTINGS": "list-postings", "LIST_TAX_REGISTRATIONS": "list-tax-registrations",
          "TAX_OBLIGATION": "tax-obligation", "ACCOUNT_BALANCE": "account-balance",
          "TRIAL_BALANCE": "trial-balance", "ACCOUNT_LEDGER": "account-ledger",
          "PERIOD_SUMMARY": "period-summary", "FINANCIAL_POSITION": "financial-position",
          "INCOME_STATEMENT": "income-statement", "CASH_FLOW_STATEMENT": "cash-flow-statement",
          "CHANGES_IN_EQUITY": "changes-in-equity", "EXECUTE_PLAN": "execute-plan",
          "PREFLIGHT_ENTRY": "preflight-entry", "POST_ENTRY": "post-entry",
          "RECORD_SALE_SETTLED": "record-sale-settled", "RECORD_SALE_ON_CREDIT": "record-sale-on-credit",
          "RECORD_EXPENSE_SETTLED": "record-expense-settled", "RECORD_EXPENSE_ON_CREDIT": "record-expense-on-credit",
          "RECORD_RECEIPT": "record-receipt", "RECORD_PAYMENT": "record-payment",
          "RECORD_OWNER_CONTRIBUTION": "record-owner-contribution", "RECORD_OWNER_WITHDRAWAL": "record-owner-withdrawal",
          "RECORD_OPENING_POSITION": "record-opening-position", "RECORD_REVERSAL": "record-reversal"
        }
        """
    )
    if not isinstance(payload, dict):
        raise TypeError("operation-id contract fixture payload must be a JSON object")
    return {str(key): str(value) for key, value in payload.items()}
