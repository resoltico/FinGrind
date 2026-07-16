from __future__ import annotations

import stat

from .cli import run_cli
from .models import ReleaseSmokeConfig
from .open_book_support import open_book
from .support import parse_json_output, payload_field, require, require_match


def verify_book_key_generation(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    print(f"{config.label}: generating a dedicated book key file")
    generate_key_payload = parse_json_output(
        run_cli(
            config,
            operation_ids["generateBookKeyFile"],
            "--new-book-key-file",
            config.book_key.argument,
            "--output",
            "json",
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
        payload_field(open_payload, "payload", "bookIdentity", "accountingKernelProfile")
        == config.accounting_kernel_profile,
        f"{config.label} open-book did not publish the expected accounting kernel profile",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "accountingFrameworkPosition")
        == config.accounting_framework_position,
        f"{config.label} open-book did not publish the expected framework posture",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "entityForm") == config.entity_form,
        f"{config.label} open-book did not publish the expected entity form",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "bookTemplateId")
        == config.book_template_id,
        f"{config.label} open-book did not publish the expected book template",
    )
    require(
        payload_field(open_payload, "payload", "bookIdentity", "accountingBasis")
        == config.accounting_basis,
        f"{config.label} open-book did not publish the expected accounting basis",
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
        "businessActivityTags" not in payload_field(open_payload, "payload", "bookIdentity"),
        f"{config.label} open-book leaked retired business-activity metadata",
    )


def verify_account_registry(config: ReleaseSmokeConfig, operation_ids: dict[str, str]) -> None:
    print(f"{config.label}: verifying account declaration and registry listing")
    declare_bank_output = run_cli(
        config,
        operation_ids["declareAccount"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--request-file",
        config.declare_bank_account.argument,
        "--output",
        "json",
    )
    declare_revenue_output = run_cli(
        config,
        operation_ids["declareAccount"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--request-file",
        config.declare_expense_supplement.argument,
        "--output",
        "json",
    )
    list_output = run_cli(
        config,
        operation_ids["listAccounts"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--output",
        "json",
    )
    require_match(
        declare_bank_output,
        rf'"accountCode"[[:space:]]*:[[:space:]]*"{config.bank_account_code}"',
        f"{config.label} bank-account declaration did not echo the requested account",
    )
    require_match(
        declare_revenue_output,
        rf'"accountCode"[[:space:]]*:[[:space:]]*"{config.expense_supplement_account_code}"',
        f"{config.label} expense-supplement declaration did not echo the requested account",
    )
    require_match(
        list_output,
        rf'"accountCode"[[:space:]]*:[[:space:]]*"{config.starter_cash_account_code}"',
        f"{config.label} account listing did not include the seeded cash account",
    )
    require_match(
        list_output,
        rf'"accountCode"[[:space:]]*:[[:space:]]*"{config.starter_revenue_account_code}"',
        f"{config.label} account listing did not include the seeded revenue account",
    )
    require_match(
        list_output,
        rf'"accountCode"[[:space:]]*:[[:space:]]*"{config.bank_account_code}"',
        f"{config.label} account listing did not include the declared bank account",
    )
    require_match(
        list_output,
        rf'"accountCode"[[:space:]]*:[[:space:]]*"{config.expense_supplement_account_code}"',
        f"{config.label} account listing did not include the declared expense supplement",
    )
