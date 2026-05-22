from __future__ import annotations

import stat

from .assertions import assert_discovery_payloads
from .cli import run_cli
from .models import ReleaseSmokeConfig, ReleaseSmokeFailure
from .support import (
    parse_json_output,
    payload_field,
    project_version,
    require,
    require_match,
)


def verify_version_command(config: ReleaseSmokeConfig, operation_ids: dict[str, str]) -> None:
    print(f"{config.label}: verifying version command")
    version_payload = parse_json_output(
        run_cli(config, operation_ids["version"], "--output", "json"),
        f"{config.label} version output was not valid JSON",
    )
    require(
        version_payload.get("status") == "ok",
        f"{config.label} version output did not report ok status",
    )
    require(
        payload_field(version_payload, "payload", "application") == "FinGrind",
        f"{config.label} version output did not include application name",
    )
    require(
        payload_field(version_payload, "payload", "version") == project_version(config.repo_root),
        f"{config.label} version output did not report the expected version",
    )


def verify_runtime_contract(
    config: ReleaseSmokeConfig,
    contract: dict[str, object],
    operation_ids: dict[str, str],
) -> dict[str, int]:
    print(f"{config.label}: verifying runtime contract")
    capabilities_payload = parse_json_output(
        run_cli(config, operation_ids["capabilities"], "--output", "json", "--detail", "full"),
        f"{config.label} capabilities output was not valid JSON",
    )
    environment_payload = parse_json_output(
        run_cli(config, operation_ids["environment"], "--output", "json"),
        f"{config.label} environment output was not valid JSON",
    )
    return assert_discovery_payloads(config, contract, capabilities_payload, environment_payload)


def verify_book_key_generation(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    print(f"{config.label}: generating a dedicated book key file")
    generate_key_payload = parse_json_output(
        run_cli(
            config,
            operation_ids["generateBookKeyFile"],
            "--book-key-file",
            config.book_key.argument,
        ),
        f"{config.label} generate-book-key-file output was not valid JSON",
    )
    require(
        config.book_key.local_path.is_file(),
        f"{config.label} did not generate the requested key file: {config.book_key.local_path}",
    )
    require(
        generate_key_payload.get("status") == "ok",
        f"{config.label} key generation did not report ok status",
    )
    require(
        payload_field(generate_key_payload, "payload", "permissions")
        == config.book_key_output_permissions,
        f"{config.label} key generation did not report {config.book_key_output_permissions} file permissions",
    )
    if config.book_key_output_permissions == "0600":
        require(
            stat.S_IMODE(config.book_key.local_path.stat().st_mode) == 0o600,
            f"{config.label} generated key file did not use 0600 permissions",
        )


def verify_open_book(config: ReleaseSmokeConfig, operation_ids: dict[str, str]) -> None:
    print(f"{config.label}: verifying explicit book initialization")
    open_payload = parse_json_output(
        open_book(config, operation_ids),
        f"{config.label} open-book output was not valid JSON",
    )
    require(
        open_payload.get("status") == "ok",
        f"{config.label} open-book did not report ok status",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "entityName") == config.entity_name,
        f"{config.label} open-book did not echo the expected entity name",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "entityForm") == config.entity_form,
        f"{config.label} open-book did not echo the expected entity form",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "ownerModel") == config.owner_model,
        f"{config.label} open-book did not echo the expected owner model",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "businessActivityTags")
        == config.business_activity_tags,
        f"{config.label} open-book did not echo the expected business activity tags",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "functionalCurrency")
        == config.functional_currency,
        f"{config.label} open-book did not echo the expected functional currency",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "fiscalYearStart")
        == config.fiscal_year_start,
        f"{config.label} open-book did not echo the expected fiscal year start",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "policyProfile")
        == config.policy_profile,
        f"{config.label} open-book did not echo the expected accounting policy profile",
    )


def verify_account_registry(config: ReleaseSmokeConfig, operation_ids: dict[str, str]) -> None:
    print(f"{config.label}: verifying account declaration and registry listing")
    declare_cash_output = run_cli(
        config,
        operation_ids["declareAccount"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--request-file",
        config.declare_cash.argument,
    )
    declare_revenue_output = run_cli(
        config,
        operation_ids["declareAccount"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--request-file",
        config.declare_revenue.argument,
    )
    list_output = run_cli(
        config,
        operation_ids["listAccounts"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
    )
    require_match(
        declare_cash_output,
        r'"accountCode"[[:space:]]*:[[:space:]]*"1000"',
        f"{config.label} cash declaration did not echo account 1000",
    )
    require_match(
        declare_revenue_output,
        r'"accountCode"[[:space:]]*:[[:space:]]*"2000"',
        f"{config.label} revenue declaration did not echo account 2000",
    )
    require_match(
        list_output,
        r'"accountCode"[[:space:]]*:[[:space:]]*"1000"',
        f"{config.label} account listing did not include account 1000",
    )
    require_match(
        list_output,
        r'"accountCode"[[:space:]]*:[[:space:]]*"2000"',
        f"{config.label} account listing did not include account 2000",
    )


def open_book(config: ReleaseSmokeConfig, operation_ids: dict[str, str]) -> str:
    if config.open_book_mode == "generated-key-stdin":
        generated_passphrase = config.book_key.local_path.read_text(encoding="utf-8")
        require(
            bool(generated_passphrase),
            f"{config.label} generated an empty key file",
        )
        return run_cli(
            config,
            operation_ids["openBook"],
            "--book-file",
            config.book.argument,
            "--entity-name",
            config.entity_name,
            "--entity-form",
            config.entity_form,
            "--owner-model",
            config.owner_model,
            "--business-activity-tag",
            *config.business_activity_tags,
            "--functional-currency",
            config.functional_currency,
            "--fiscal-year-start",
            config.fiscal_year_start,
            "--policy-profile",
            config.policy_profile,
            "--book-passphrase-stdin",
            stdin_text=generated_passphrase,
        )
    if config.open_book_mode == "book-key-file":
        return run_cli(
            config,
            operation_ids["openBook"],
            "--book-file",
            config.book.argument,
            "--entity-name",
            config.entity_name,
            "--entity-form",
            config.entity_form,
            "--owner-model",
            config.owner_model,
            "--business-activity-tag",
            *config.business_activity_tags,
            "--functional-currency",
            config.functional_currency,
            "--fiscal-year-start",
            config.fiscal_year_start,
            "--policy-profile",
            config.policy_profile,
            "--book-key-file",
            config.book_key.argument,
        )
    raise ReleaseSmokeFailure(
        f"{config.label} configured unsupported open-book mode: {config.open_book_mode}"
    )
