from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True)
class SmokePath:
    relative_path: Path
    local_path: Path
    argument: str


@dataclass(frozen=True)
class ReleaseSmokeConfig:
    label: str
    repo_root: Path
    work_root: Path
    command_prefix: list[str]
    command_bridge_prefix: list[str]
    command_cwd: Path | None
    reported_work_root: Path | None
    command_env_drop: list[str]
    command_env_set: dict[str, str]
    runtime_distribution_key: str
    expect_loaded_sqlite_details: bool
    expect_bundle_home_property: bool
    book_key_output_permissions: str
    request_sale: SmokePath
    request_expense: SmokePath
    request_taxed_sale: SmokePath
    request_raw_journal: SmokePath
    invalid_request: SmokePath
    declare_bank_account: SmokePath
    declare_expense_supplement: SmokePath
    book: SmokePath
    book_key: SmokePath
    attestation_founder_principal_id: str
    attestation_founder_key: SmokePath
    attestation_founder_passphrase: SmokePath
    backup_book: SmokePath
    backup_book_key: SmokePath
    backup_id: str
    restored_book: SmokePath
    restored_book_key: SmokePath
    replacement_book_key: SmokePath
    prompt_failure_book: SmokePath
    attestation_receipt: SmokePath
    trial_balance_pdf: SmokePath
    trial_balance_pdf_stderr_path: Path
    request_prefix: str
    open_book_mode: str
    entity_name: str
    accounting_kernel_profile: str
    accounting_framework_position: str
    entity_form: str
    book_template_id: str
    inventory_costing_doctrine: str | None
    accounting_basis: str
    functional_currency: str
    fiscal_year_start: str
    book_start_effective_date: str
    starter_cash_account_code: str
    starter_cash_account_name: str
    starter_revenue_account_code: str
    starter_revenue_account_name: str
    bank_account_code: str
    bank_account_name: str
    expense_supplement_account_code: str
    expense_supplement_account_name: str
    native_sqlite_probe_classpath: str
    native_sqlite_java_prefix: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class ReleaseSmokeScenario:
    work_root: Path
    request_sale: SmokePath
    request_expense: SmokePath
    request_taxed_sale: SmokePath
    request_raw_journal: SmokePath
    invalid_request: SmokePath
    declare_bank_account: SmokePath
    declare_expense_supplement: SmokePath
    book: SmokePath
    book_key: SmokePath
    attestation_founder_principal_id: str
    attestation_founder_key: SmokePath
    attestation_founder_passphrase: SmokePath
    backup_book: SmokePath
    backup_book_key: SmokePath
    backup_id: str
    restored_book: SmokePath
    restored_book_key: SmokePath
    replacement_book_key: SmokePath
    prompt_failure_book: SmokePath
    attestation_receipt: SmokePath
    trial_balance_pdf: SmokePath
    trial_balance_pdf_stderr_path: Path
    request_prefix: str
    entity_name: str
    accounting_kernel_profile: str
    accounting_framework_position: str
    entity_form: str
    book_template_id: str
    inventory_costing_doctrine: str | None
    accounting_basis: str
    functional_currency: str
    fiscal_year_start: str
    book_start_effective_date: str
    starter_cash_account_code: str
    starter_cash_account_name: str
    starter_revenue_account_code: str
    starter_revenue_account_name: str
    bank_account_code: str
    bank_account_name: str
    expense_supplement_account_code: str
    expense_supplement_account_name: str


class ReleaseSmokeFailure(RuntimeError):
    """Raised when the release acceptance workflow finds a contract violation."""
