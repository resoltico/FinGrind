from __future__ import annotations

import re
from typing import Any

from .models import ReleaseSmokeConfig
from .support import (
    require,
    require_match,
    require_no_match,
    require_string,
    required_list,
    required_mapping,
)


def assert_capabilities_payload(
    config: ReleaseSmokeConfig,
    contract: dict[str, object],
    capabilities_payload: dict[str, Any],
) -> None:
    payload = required_mapping(capabilities_payload, "payload")
    environment = required_mapping(payload, "environment")
    distribution = required_mapping(environment, "distribution")
    storage = required_mapping(environment, "storage")
    sqlite = required_mapping(environment, "sqlite")
    request_input = required_mapping(payload, "requestInput")
    commands = required_mapping(payload, "commands")
    response_model = required_mapping(payload, "responseModel")
    query_commands = required_list(commands, "query")
    query_commands_by_name = {
        require_string(command, "name"): command for command in query_commands if isinstance(command, dict)
    }
    error_descriptors = required_list(response_model, "errorDescriptors")
    error_codes = {
        require_string(descriptor, "code")
        for descriptor in error_descriptors
        if isinstance(descriptor, dict)
    }
    runtime_surface = required_mapping(contract, "runtimeSurface")
    protected_book_format = required_mapping(contract, "protectedBookFormat")
    public_distribution = required_mapping(contract, "publicDistribution")
    managed_sqlite = required_mapping(contract, "managedSqlite")
    operation_ids = required_mapping(contract, "operationIds")
    runtime_distribution = require_string(runtime_surface, config.runtime_distribution_key)

    require(
        require_string(distribution, "runtimeDistribution") == runtime_distribution,
        f"{config.label} capabilities output did not report the canonical runtime distribution",
    )
    require(
        require_string(distribution, "publicCliDistribution")
        == require_string(runtime_surface, "publicCliDistribution"),
        f"{config.label} capabilities output did not report the public CLI distribution contract",
    )
    require(
        required_list(distribution, "supportedPublicCliBundleTargets")
        == required_list(public_distribution, "supportedPublicCliBundleTargets"),
        f"{config.label} capabilities output did not report the supported public bundle targets",
    )
    require(
        required_list(distribution, "unsupportedPublicCliBundleTargets")
        == required_list(public_distribution, "unsupportedPublicCliBundleTargets"),
        f"{config.label} capabilities output did not report the current unsupported public bundle targets",
    )
    require(
        require_string(storage, "storageDriver") == require_string(runtime_surface, "storageDriver"),
        f"{config.label} capabilities output did not report the SQLite3 Multiple Ciphers storage driver",
    )
    require(
        require_string(storage, "bookProtectionMode")
        == require_string(runtime_surface, "bookProtectionMode"),
        f"{config.label} capabilities output did not report required book protection",
    )
    storage_format = required_mapping(storage, "defaultProtectedBookFormat")
    require(
        require_string(storage_format, "cipher")
        == require_string(protected_book_format, "cipher"),
        f"{config.label} capabilities output did not report the canonical default book cipher",
    )
    require(
        storage_format.get("legacyMode") == protected_book_format.get("legacyMode"),
        f"{config.label} capabilities output did not report the canonical legacy-mode flag",
    )
    require(
        storage_format.get("pageSize") == protected_book_format.get("pageSize"),
        f"{config.label} capabilities output did not report the canonical protected-book page size",
    )
    require(
        storage_format.get("reservedBytes") == protected_book_format.get("reservedBytes"),
        f"{config.label} capabilities output did not report the canonical protected-book reserved bytes",
    )
    require(
        require_string(sqlite, "libraryMode")
        == require_string(runtime_surface, "sqliteLibraryMode"),
        f"{config.label} capabilities output did not report the managed-only SQLite runtime mode",
    )
    require(
        require_string(request_input, "outputOption") == "--output",
        f"{config.label} capabilities output did not report the canonical --output selector",
    )

    trial_balance = required_mapping(query_commands_by_name, require_string(operation_ids, "trialBalance"))
    account_ledger = required_mapping(query_commands_by_name, require_string(operation_ids, "accountLedger"))
    period_summary = required_mapping(query_commands_by_name, require_string(operation_ids, "periodSummary"))
    require(
        required_list(trial_balance, "outputModes") == ["json", "human", "csv"],
        f"{config.label} trial-balance did not report json,human,csv stdout modes",
    )
    require(
        required_list(account_ledger, "outputModes") == ["json", "human", "csv"],
        f"{config.label} account-ledger did not report json,human,csv stdout modes",
    )
    require(
        required_list(period_summary, "outputModes") == ["json", "human", "csv"],
        f"{config.label} period-summary did not report json,human,csv stdout modes",
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
            "bundle-managed" if config.expect_bundle_home_property else "environment-configured"
        )
        require(
            require_string(sqlite, "runtimeStatus") == "ready",
            f"{config.label} capabilities output did not report a ready SQLite runtime",
        )
        require(
            require_string(sqlite, "runtimeProvenance") == expected_runtime_provenance,
            f"{config.label} capabilities output did not report the expected SQLite runtime provenance",
        )
        require(
            bool(require_string(sqlite, "loadedLibraryPath").strip()),
            f"{config.label} capabilities output did not report the loaded SQLite library path",
        )
        require(
            require_string(sqlite, "requiredSqliteSourceId")
            == require_string(managed_sqlite, "requiredSqliteSourceId"),
            f"{config.label} capabilities output did not report the canonical SQLite source id requirement",
        )
        require(
            require_string(sqlite, "loadedSqliteVersion")
            == require_string(managed_sqlite, "requiredMinimumSqliteVersion"),
            f"{config.label} capabilities output did not report the canonical SQLite version",
        )
        require(
            require_string(sqlite, "loadedSqlite3mcVersion")
            == require_string(managed_sqlite, "requiredSqlite3mcVersion"),
            f"{config.label} capabilities output did not report the canonical SQLite3 Multiple Ciphers version",
        )
        require(
            require_string(sqlite, "loadedSqliteSourceId")
            == require_string(managed_sqlite, "requiredSqliteSourceId"),
            f"{config.label} capabilities output did not report the canonical SQLite source id",
        )
        require(
            required_list(sqlite, "requiredCompileOptions")
            == required_list(managed_sqlite, "requiredCompileOptions"),
            f"{config.label} capabilities output did not report the canonical SQLite compile options",
        )
        require(
            require_string(sqlite, "compileOptionsVerification") == "verified",
            f"{config.label} capabilities output did not report verified SQLite compile-option enforcement",
        )

    if config.expect_bundle_home_property:
        require(
            require_string(sqlite, "bundleHomeSystemProperty")
            == require_string(runtime_surface, "sqliteBundleHomeSystemProperty"),
            f"{config.label} capabilities output did not report the bundle-home system property",
        )


def assert_operator_queries_and_reports(
    config: ReleaseSmokeConfig,
    list_postings_second_page_output: str,
    list_postings_human_output: str,
    account_balance_human_output: str,
    trial_balance_human_output: str,
    pdf_stdout: str,
    pdf_stderr: str,
    account_ledger_csv_output: str,
    period_summary_human_output: str,
) -> None:
    require_match(
        list_postings_second_page_output,
        re.escape(config.second_page_command_id),
        f"{config.label} second posting page did not round-trip the opaque nextCursor",
    )
    require_match(
        list_postings_human_output,
        r"^Effective date[[:space:]]+\|[[:space:]]+Recorded at",
        f"{config.label} human posting register did not render a table header",
    )
    require_match(
        list_postings_human_output,
        r"10\.00",
        f"{config.label} human posting register did not render accounting-scale amounts",
    )
    require_match(
        account_balance_human_output,
        r"^Account Balance$",
        f"{config.label} human account-balance output did not render the report title",
    )
    require_match(
        account_balance_human_output,
        r"Account[[:space:]]+:[[:space:]]+1000",
        f"{config.label} human account-balance output did not render the selected account",
    )
    require_match(
        account_balance_human_output,
        r"6\.00",
        f"{config.label} human account-balance output did not render the expected net balance",
    )
    require_match(
        trial_balance_human_output,
        r"^Trial Balance$",
        f"{config.label} trial-balance output did not render the report title",
    )
    require_match(
        trial_balance_human_output,
        r"Effective date to[[:space:]]+:[[:space:]]+2026-04-08",
        f"{config.label} trial-balance output did not render the effective date",
    )
    require_match(
        trial_balance_human_output,
        r"1000",
        f"{config.label} trial-balance output did not render account 1000",
    )
    require_match(
        trial_balance_human_output,
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
        pdf_stdout == trial_balance_human_output,
        f"{config.label} PDF export changed stdout instead of preserving the human report surface",
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
    require_match(
        pdf_stderr,
        re.escape(config.trial_balance_pdf.argument),
        f"{config.label} PDF export diagnostics did not report the normalized written artifact path",
    )
    require_match(
        account_ledger_csv_output,
        r"^accountCode,accountName,effectiveDateFrom,effectiveDateTo,postingId,effectiveDate,recordedAt,currencyCode,debitAmount,creditAmount,runningBalance,runningBalanceSide,counterpartAccounts$",
        f"{config.label} account-ledger CSV output did not render the expected header",
    )
    require_match(
        account_ledger_csv_output,
        r"^1000,Cash,2026-04-07,2026-04-08,[^,]+,2026-04-07,[^,]+,EUR,10\.00,0\.00,10\.00,DEBIT,2000$",
        f"{config.label} account-ledger CSV output did not render the opening ledger movement row",
    )
    require_match(
        account_ledger_csv_output,
        r"^1000,Cash,2026-04-07,2026-04-08,[^,]+,2026-04-08,[^,]+,EUR,0\.00,4\.00,6\.00,DEBIT,2000$",
        f"{config.label} account-ledger CSV output did not render the running-balance adjustment row",
    )
    require_match(
        period_summary_human_output,
        r"^Period Summary$",
        f"{config.label} period-summary output did not render the report title",
    )
    require_match(
        period_summary_human_output,
        r"Posting count",
        f"{config.label} period-summary output did not render posting-count metadata",
    )
    require_match(
        period_summary_human_output,
        r"2",
        f"{config.label} period-summary output did not render the expected posting count",
    )
