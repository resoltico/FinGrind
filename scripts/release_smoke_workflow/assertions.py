from __future__ import annotations

import csv
import re
from hashlib import sha256
from io import StringIO
from typing import Any

from .models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from .support import (
    extract_pdf_exported_path,
    normalize_reported_path,
    normalized_path_components,
    require,
    require_bool,
    require_match,
    require_string,
    required_list,
    required_mapping,
)


def parse_csv_rows(csv_output: str, surface_name: str) -> tuple[list[str], list[dict[str, str]]]:
    try:
        reader = csv.DictReader(StringIO(csv_output))
        fieldnames = reader.fieldnames
        if fieldnames is None:
            raise ReleaseSmokeFailure(f"{surface_name} did not render a CSV header")
        rows = list(reader)
    except csv.Error as exc:
        raise ReleaseSmokeFailure(f"{surface_name} was not valid CSV") from exc
    return list(fieldnames), rows


def expected_source_document(
    actor_prefix: str, evidence_suffix: str, document_date: str
) -> dict[str, str]:
    return {
        "sourceDocumentId": f"{actor_prefix}-{evidence_suffix}-document-1",
        "sourceDocumentType": "cash-receipt",
        "documentDate": document_date,
        "capturedAt": f"{document_date}T10:15:30Z",
        "storageLocator": f"vault://release-smoke/{actor_prefix}/{evidence_suffix}/document-1",
        "contentSha256": sha256(
            f"sha256-{actor_prefix}-{evidence_suffix}".encode("utf-8")
        ).hexdigest(),
    }


def assert_discovery_payloads(
    config: ReleaseSmokeConfig,
    contract: dict[str, object],
    capabilities_payload: dict[str, Any],
    environment_payload: dict[str, Any],
) -> dict[str, int]:
    payload = required_mapping(capabilities_payload, "payload")
    environment = required_mapping(environment_payload, "payload")
    full_contract = required_mapping(payload, "fullContract")
    distribution = required_mapping(environment, "distribution")
    storage = required_mapping(environment, "storage")
    sqlite = required_mapping(environment, "sqlite")
    runtime = required_mapping(sqlite, "runtime")
    request_input = required_mapping(payload, "requestInput")
    commands = required_mapping(payload, "commands")
    response_model = required_mapping(full_contract, "responseModel")
    query_commands = required_list(commands, "query")
    query_commands_by_name = {
        require_string(command, "name"): command
        for command in query_commands
        if isinstance(command, dict)
    }
    error_descriptors = required_list(response_model, "errorDescriptors")
    error_descriptor_exit_codes: dict[str, int] = {}
    for descriptor in error_descriptors:
        if not isinstance(descriptor, dict):
            continue
        code = require_string(descriptor, "code")
        exit_code = descriptor.get("exitCode")
        require(
            isinstance(exit_code, int) and not isinstance(exit_code, bool) and exit_code >= 0,
            f"{config.label} capabilities output did not publish one non-negative exitCode for {code}",
        )
        error_descriptor_exit_codes[code] = exit_code
    error_codes = set(error_descriptor_exit_codes)
    runtime_surface = required_mapping(contract, "runtimeSurface")
    protected_book_format = required_mapping(contract, "protectedBookFormat")
    public_distribution = required_mapping(contract, "publicDistribution")
    managed_sqlite = required_mapping(contract, "managedSqlite")
    operation_ids = required_mapping(contract, "operationIds")
    runtime_distribution = require_string(runtime_surface, config.runtime_distribution_key)

    require(
        require_string(payload, "detail") == "full",
        f"{config.label} capabilities output did not expose the exhaustive full discovery contract",
    )
    require(
        require_string(distribution, "runtimeDistribution") == runtime_distribution,
        f"{config.label} environment output did not report the canonical runtime distribution",
    )
    require(
        require_string(distribution, "publicCliDistribution")
        == require_string(runtime_surface, "publicCliDistribution"),
        f"{config.label} environment output did not report the public CLI distribution contract",
    )
    require(
        required_list(distribution, "supportedPublicCliBundleTargets")
        == required_list(public_distribution, "supportedPublicCliBundleTargets"),
        f"{config.label} environment output did not report the supported public bundle targets",
    )
    require(
        required_list(distribution, "unsupportedPublicCliBundleTargets")
        == required_list(public_distribution, "unsupportedPublicCliBundleTargets"),
        f"{config.label} environment output did not report the current unsupported public bundle targets",
    )
    require(
        require_string(storage, "storageDriver")
        == require_string(runtime_surface, "storageDriver"),
        f"{config.label} environment output did not report the SQLite3 Multiple Ciphers storage driver",
    )
    require(
        require_string(storage, "bookProtectionMode")
        == require_string(runtime_surface, "bookProtectionMode"),
        f"{config.label} environment output did not report required book protection",
    )
    storage_format = required_mapping(storage, "defaultProtectedBookFormat")
    require(
        require_string(storage_format, "cipher") == require_string(protected_book_format, "cipher"),
        f"{config.label} environment output did not report the canonical default book cipher",
    )
    require(
        storage_format.get("legacyMode") == protected_book_format.get("legacyMode"),
        f"{config.label} environment output did not report the canonical legacy-mode flag",
    )
    require(
        storage_format.get("pageSize") == protected_book_format.get("pageSize"),
        f"{config.label} environment output did not report the canonical protected-book page size",
    )
    require(
        storage_format.get("reservedBytes") == protected_book_format.get("reservedBytes"),
        f"{config.label} environment output did not report the canonical protected-book reserved bytes",
    )
    require(
        require_string(sqlite, "libraryMode")
        == require_string(runtime_surface, "sqliteLibraryMode"),
        f"{config.label} environment output did not report the managed-only SQLite runtime mode",
    )
    require(
        require_string(request_input, "outputOption") == "--output",
        f"{config.label} capabilities output did not report the canonical --output selector",
    )

    trial_balance = required_mapping(
        query_commands_by_name, require_string(operation_ids, "trialBalance")
    )
    account_ledger = required_mapping(
        query_commands_by_name, require_string(operation_ids, "accountLedger")
    )
    period_summary = required_mapping(
        query_commands_by_name, require_string(operation_ids, "periodSummary")
    )
    require(
        required_list(trial_balance, "outputModes") == ["json", "text", "csv"],
        f"{config.label} trial-balance did not report json,text,csv stdout modes",
    )
    require(
        required_list(account_ledger, "outputModes") == ["json", "text", "csv"],
        f"{config.label} account-ledger did not report json,text,csv stdout modes",
    )
    require(
        required_list(period_summary, "outputModes") == ["json", "text", "csv"],
        f"{config.label} period-summary did not report json,text,csv stdout modes",
    )
    trial_balance_artifacts = required_list(trial_balance, "artifactOutputs")
    require(
        len(trial_balance_artifacts) == 1 and isinstance(trial_balance_artifacts[0], dict),
        f"{config.label} trial-balance did not report the canonical PDF artifact contract",
    )
    trial_balance_artifact = trial_balance_artifacts[0]
    require(
        require_string(trial_balance_artifact, "format") == "pdf"
        and require_string(trial_balance_artifact, "option") == "--pdf-out <path>",
        f"{config.label} trial-balance did not report the canonical PDF artifact contract",
    )
    for error_code in (
        "invalid-page-cursor",
        "interactive-prompt-unavailable",
        "protected-book-verification-failed",
    ):
        require(
            error_code in error_codes,
            f"{config.label} capabilities output did not report the {error_code} error descriptor",
        )

    if config.expect_loaded_sqlite_details:
        expected_runtime_provenance = (
            "bundle-managed" if config.expect_bundle_home_property else "source-checkout-managed"
        )
        require(
            require_string(runtime, "status") == "ready",
            f"{config.label} environment output did not report a ready SQLite runtime",
        )
        require(
            require_string(runtime, "runtimeProvenance") == expected_runtime_provenance,
            f"{config.label} environment output did not report the expected SQLite runtime provenance",
        )
        require(
            bool(require_string(runtime, "loadedLibraryPath").strip()),
            f"{config.label} environment output did not report the loaded SQLite library path",
        )
        require(
            require_string(sqlite, "requiredSqliteSourceId")
            == require_string(managed_sqlite, "requiredSqliteSourceId"),
            f"{config.label} environment output did not report the canonical SQLite source id requirement",
        )
        require(
            require_string(runtime, "loadedSqliteVersion")
            == require_string(managed_sqlite, "requiredMinimumSqliteVersion"),
            f"{config.label} environment output did not report the canonical SQLite version",
        )
        require(
            require_string(runtime, "loadedSqlite3mcVersion")
            == require_string(managed_sqlite, "requiredSqlite3mcVersion"),
            f"{config.label} environment output did not report the canonical SQLite3 Multiple Ciphers version",
        )
        require(
            require_string(runtime, "loadedSqliteSourceId")
            == require_string(managed_sqlite, "requiredSqliteSourceId"),
            f"{config.label} environment output did not report the canonical SQLite source id",
        )
        require(
            required_list(sqlite, "requiredCompileOptions")
            == required_list(managed_sqlite, "requiredCompileOptions"),
            f"{config.label} environment output did not report the canonical SQLite compile options",
        )
        require(
            required_list(sqlite, "forbiddenCompileOptions")
            == required_list(managed_sqlite, "forbiddenCompileOptions"),
            f"{config.label} environment output did not report the canonical forbidden SQLite compile options",
        )
        require(
            require_bool(sqlite, "requiresSecureMemorySupport")
            == require_bool(managed_sqlite, "requiresSecureMemorySupport"),
            f"{config.label} environment output did not report the canonical SQLite3MC secure-memory requirement",
        )
        require(
            require_string(runtime, "compileOptionsVerification") == "verified",
            f"{config.label} environment output did not report verified SQLite compile-option enforcement",
        )

    if config.expect_bundle_home_property:
        require(
            require_string(sqlite, "bundleHomeSystemProperty")
            == require_string(runtime_surface, "sqliteBundleHomeSystemProperty"),
            f"{config.label} environment output did not report the bundle-home system property",
        )

    return error_descriptor_exit_codes


def assert_operator_queries_and_reports(
    config: ReleaseSmokeConfig,
    list_postings_second_page_output: str,
    list_postings_text_output: str,
    account_balance_text_output: str,
    trial_balance_text_output: str,
    pdf_stdout: str,
    pdf_stderr: str,
    account_ledger_csv_output: str,
    period_summary_text_output: str,
) -> None:
    require_match(
        list_postings_second_page_output,
        re.escape(config.second_page_command_id),
        f"{config.label} second posting page did not round-trip the opaque nextCursor",
    )
    require_match(
        list_postings_text_output,
        r"^Postings$",
        f"{config.label} text posting register did not render the report title",
    )
    require_match(
        list_postings_text_output,
        r"Returned postings[[:space:]]+:[[:space:]]+2",
        f"{config.label} text posting register did not render the returned-posting count",
    )
    require_match(
        list_postings_text_output,
        r"2026-04-08",
        f"{config.label} text posting register did not render the latest effective date",
    )
    require_match(
        list_postings_text_output,
        r"2026-04-07",
        f"{config.label} text posting register did not render the earlier effective date",
    )
    require_match(
        list_postings_text_output,
        r"10\.00",
        f"{config.label} text posting register did not render accounting-scale amounts",
    )
    require_match(
        account_balance_text_output,
        r"^Account Balance$",
        f"{config.label} text account-balance output did not render the report title",
    )
    require_match(
        account_balance_text_output,
        r"Account[[:space:]]+:[[:space:]]+1000",
        f"{config.label} text account-balance output did not render the selected account",
    )
    require_match(
        account_balance_text_output,
        r"6\.00",
        f"{config.label} text account-balance output did not render the expected net balance",
    )
    require_match(
        trial_balance_text_output,
        r"^Trial Balance$",
        f"{config.label} trial-balance output did not render the report title",
    )
    require_match(
        trial_balance_text_output,
        r"As of[[:space:]]+:[[:space:]]+2026-04-08",
        f"{config.label} trial-balance output did not render the as-of date",
    )
    require_match(
        trial_balance_text_output,
        r"1000",
        f"{config.label} trial-balance output did not render account 1000",
    )
    require_match(
        trial_balance_text_output,
        r"6\.00",
        f"{config.label} trial-balance output did not render the expected net amount",
    )
    require(
        config.trial_balance_pdf.local_path.is_file(),
        f"{config.label} trial-balance did not write the requested PDF artifact",
    )
    require(
        config.trial_balance_pdf.local_path.read_bytes().startswith(b"%PDF-"),
        f"{config.label} trial-balance PDF artifact did not start with %PDF-",
    )
    require(
        pdf_stdout == trial_balance_text_output,
        f"{config.label} PDF export changed stdout instead of preserving the text report surface",
    )
    require_match(
        pdf_stderr,
        r"^Info$",
        f"{config.label} PDF export did not emit the canonical diagnostics heading",
    )
    require_match(
        pdf_stderr,
        r"^Code[[:space:]]+:[[:space:]]+pdf-exported$",
        f"{config.label} PDF export did not emit the canonical pdf-exported diagnostics code",
    )
    require_match(
        pdf_stderr,
        r"^Argument[[:space:]]+:[[:space:]]+--pdf-out$",
        f"{config.label} PDF export did not attribute diagnostics to --pdf-out",
    )
    reported_pdf_path = extract_pdf_exported_path(pdf_stderr)
    require(
        reported_artifact_path_matches(config, config.trial_balance_pdf, reported_pdf_path),
        f"{config.label} PDF export diagnostics did not report the normalized written artifact path",
    )
    account_ledger_header, account_ledger_rows = parse_csv_rows(
        account_ledger_csv_output, f"{config.label} account-ledger CSV output"
    )
    expected_account_ledger_header = [
        "rowKind",
        "accountCode",
        "accountName",
        "accountType",
        "accountRole",
        "normalBalance",
        "active",
        "effectiveDateFrom",
        "effectiveDateTo",
        "currencyCode",
        "openingDebitTotal",
        "openingCreditTotal",
        "openingNetAmount",
        "openingBalanceSide",
        "closingDebitTotal",
        "closingCreditTotal",
        "closingNetAmount",
        "closingBalanceSide",
        "effectiveDate",
        "recordedAt",
        "postingId",
        "postingKind",
        "postingOriginKind",
        "reversalState",
        "reversalTarget",
        "debitAmount",
        "creditAmount",
        "runningNetAmount",
        "runningBalanceSide",
        "counterpartAccounts",
        "sourceDocumentIds",
        "sourceDocumentTypes",
        "approvalIds",
        "approvalDecisions",
    ]
    require(
        account_ledger_header == expected_account_ledger_header,
        f"{config.label} account-ledger CSV output did not render the expected header",
    )
    require(
        len(account_ledger_rows) == 2,
        f"{config.label} account-ledger CSV output did not render the expected row count",
    )
    opening_entry, adjustment_entry = account_ledger_rows
    require(
        opening_entry["rowKind"] == "entry"
        and opening_entry["accountCode"] == "1000"
        and opening_entry["accountName"] == "Cash"
        and opening_entry["accountType"] == "ASSET"
        and opening_entry["accountRole"] == "ORDINARY"
        and opening_entry["normalBalance"] == "DEBIT"
        and opening_entry["active"] == "true"
        and opening_entry["effectiveDateFrom"] == "2026-04-07"
        and opening_entry["effectiveDateTo"] == "2026-04-08"
        and opening_entry["currencyCode"] == "EUR"
        and opening_entry["openingDebitTotal"] == "0.00"
        and opening_entry["openingCreditTotal"] == "0.00"
        and opening_entry["openingNetAmount"] == "0.00"
        and opening_entry["openingBalanceSide"] == "ZERO"
        and opening_entry["closingDebitTotal"] == "10.00"
        and opening_entry["closingCreditTotal"] == "4.00"
        and opening_entry["closingNetAmount"] == "6.00"
        and opening_entry["closingBalanceSide"] == "DEBIT"
        and opening_entry["effectiveDate"] == "2026-04-07"
        and opening_entry["recordedAt"] != ""
        and opening_entry["postingId"] != ""
        and opening_entry["postingKind"] == "STANDARD"
        and opening_entry["postingOriginKind"] == "CASH_REVENUE"
        and opening_entry["reversalState"] == "direct"
        and opening_entry["reversalTarget"] == ""
        and opening_entry["debitAmount"] == "10.00"
        and opening_entry["creditAmount"] == "0.00"
        and opening_entry["runningNetAmount"] == "10.00"
        and opening_entry["runningBalanceSide"] == "DEBIT"
        and opening_entry["counterpartAccounts"] == "2000"
        and opening_entry["sourceDocumentIds"]
        == expected_source_document(config.actor_prefix, "sale", "2026-04-07")["sourceDocumentId"]
        and opening_entry["sourceDocumentTypes"]
        == expected_source_document(config.actor_prefix, "sale", "2026-04-07")["sourceDocumentType"]
        and opening_entry["approvalIds"] == ""
        and opening_entry["approvalDecisions"] == "",
        f"{config.label} account-ledger CSV output did not render the opening ledger movement row",
    )
    require_match(
        opening_entry["postingId"],
        r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        f"{config.label} account-ledger CSV output did not render a canonical posting identifier for the opening ledger movement row",
    )
    require_match(
        opening_entry["recordedAt"],
        r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$",
        f"{config.label} account-ledger CSV output did not render a canonical recordedAt timestamp for the opening ledger movement row",
    )
    require(
        adjustment_entry["rowKind"] == "entry"
        and adjustment_entry["accountCode"] == "1000"
        and adjustment_entry["accountName"] == "Cash"
        and adjustment_entry["accountType"] == "ASSET"
        and adjustment_entry["accountRole"] == "ORDINARY"
        and adjustment_entry["normalBalance"] == "DEBIT"
        and adjustment_entry["active"] == "true"
        and adjustment_entry["effectiveDateFrom"] == "2026-04-07"
        and adjustment_entry["effectiveDateTo"] == "2026-04-08"
        and adjustment_entry["currencyCode"] == "EUR"
        and adjustment_entry["openingDebitTotal"] == "0.00"
        and adjustment_entry["openingCreditTotal"] == "0.00"
        and adjustment_entry["openingNetAmount"] == "0.00"
        and adjustment_entry["openingBalanceSide"] == "ZERO"
        and adjustment_entry["closingDebitTotal"] == "10.00"
        and adjustment_entry["closingCreditTotal"] == "4.00"
        and adjustment_entry["closingNetAmount"] == "6.00"
        and adjustment_entry["closingBalanceSide"] == "DEBIT"
        and adjustment_entry["effectiveDate"] == "2026-04-08"
        and adjustment_entry["recordedAt"] != ""
        and adjustment_entry["postingId"] != ""
        and adjustment_entry["postingKind"] == "STANDARD"
        and adjustment_entry["postingOriginKind"] == "CORRECTION_ADJUSTMENT"
        and adjustment_entry["reversalState"] == "direct"
        and adjustment_entry["reversalTarget"] == ""
        and adjustment_entry["debitAmount"] == "0.00"
        and adjustment_entry["creditAmount"] == "4.00"
        and adjustment_entry["runningNetAmount"] == "6.00"
        and adjustment_entry["runningBalanceSide"] == "DEBIT"
        and adjustment_entry["counterpartAccounts"] == "2000"
        and adjustment_entry["sourceDocumentIds"]
        == expected_source_document(config.actor_prefix, "adjustment", "2026-04-08")[
            "sourceDocumentId"
        ]
        and adjustment_entry["sourceDocumentTypes"]
        == expected_source_document(config.actor_prefix, "adjustment", "2026-04-08")[
            "sourceDocumentType"
        ]
        and adjustment_entry["approvalIds"] == ""
        and adjustment_entry["approvalDecisions"] == "",
        f"{config.label} account-ledger CSV output did not render the running-balance adjustment row",
    )
    require_match(
        adjustment_entry["postingId"],
        r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        f"{config.label} account-ledger CSV output did not render a canonical posting identifier for the running-balance adjustment row",
    )
    require_match(
        adjustment_entry["recordedAt"],
        r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$",
        f"{config.label} account-ledger CSV output did not render a canonical recordedAt timestamp for the running-balance adjustment row",
    )
    require_match(
        period_summary_text_output,
        r"^Period Summary$",
        f"{config.label} period-summary output did not render the report title",
    )
    require_match(
        period_summary_text_output,
        r"Posting count",
        f"{config.label} period-summary output did not render posting-count metadata",
    )
    require_match(
        period_summary_text_output,
        r"2",
        f"{config.label} period-summary output did not render the expected posting count",
    )


def expected_reported_artifact_path(config: ReleaseSmokeConfig, smoke_path: SmokePath) -> str:
    if config.reported_work_root is not None and smoke_path.argument != str(smoke_path.local_path):
        return str(config.reported_work_root / smoke_path.relative_path)
    return str(smoke_path.local_path)


def reported_artifact_path_matches(
    config: ReleaseSmokeConfig,
    smoke_path: SmokePath,
    reported_path: str,
) -> bool:
    expected_path = expected_reported_artifact_path(config, smoke_path)
    if normalize_reported_path(reported_path) == normalize_reported_path(expected_path):
        return True
    reported_components = normalized_path_components(reported_path)
    relative_components = normalized_path_components(smoke_path.relative_path.as_posix())
    return (
        len(reported_components) >= len(relative_components)
        and reported_components[-len(relative_components) :] == relative_components
    )
