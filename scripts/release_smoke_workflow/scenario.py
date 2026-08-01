from __future__ import annotations

from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

from .models import ReleaseSmokeScenario
from .scenario_paths import smoke_path
from .scenario_validation import require_argument_path_mode, require_scenario_id

UNICODE_WORKSPACE_SEGMENT = "Rīga büro"
ENTITY_NAME = "Acme Studio"
ACCOUNTING_KERNEL_PROFILE = "internal-management-bookkeeping-kernel"
ACCOUNTING_FRAMEWORK_POSITION = "NON_STATUTORY_INTERNAL_MANAGEMENT"
ENTITY_FORM = "OWNER_MANAGED_SINGLE_ENTITY"
BOOK_TEMPLATE_ID = "OWNER_MANAGED_SERVICE"
INVENTORY_COSTING_DOCTRINE: str | None = None
ACCOUNTING_BASIS = "CASH"
FUNCTIONAL_CURRENCY = "EUR"
FISCAL_YEAR_START = "01-01"
BOOK_START_EFFECTIVE_DATE = "2026-01-01"
STARTER_CASH_ACCOUNT_CODE = "cash"
STARTER_CASH_ACCOUNT_NAME = "Cash"
STARTER_REVENUE_ACCOUNT_CODE = "service-revenue"
STARTER_REVENUE_ACCOUNT_NAME = "Service Revenue"
BANK_ACCOUNT_CODE = "operating-bank"
BANK_ACCOUNT_NAME = "Operating Bank"
EXPENSE_SUPPLEMENT_ACCOUNT_CODE = "misc-expense"
EXPENSE_SUPPLEMENT_ACCOUNT_NAME = "Misc Expense"
ATTESTATION_FOUNDER_PRINCIPAL_ID = "4bc17dd7-145f-4ea7-bb55-167ca2f6ac11"


def build_release_smoke_scenario(
    work_root: Path,
    argument_path_mode: str,
    scenario_id: str,
) -> ReleaseSmokeScenario:
    normalized_scenario_id = require_scenario_id(scenario_id)
    normalized_path_mode = require_argument_path_mode(argument_path_mode)

    return ReleaseSmokeScenario(
        work_root=work_root,
        request_sale=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"--sale [{normalized_scenario_id}].json",
        ),
        request_expense=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"expense [{normalized_scenario_id}].json",
        ),
        request_taxed_sale=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"taxed sale [{normalized_scenario_id}].json",
        ),
        request_raw_journal=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"bank transfer [{normalized_scenario_id}].json",
        ),
        invalid_request=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"bad fields [{normalized_scenario_id}].json",
        ),
        declare_bank_account=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd")
            / f"declare account operating bank [{normalized_scenario_id}].json",
        ),
        declare_expense_supplement=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"declare account misc expense [{normalized_scenario_id}].json",
        ),
        book=smoke_path(
            work_root,
            normalized_path_mode,
            Path("books odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"-entity [{normalized_scenario_id}].sqlite",
        ),
        book_key=smoke_path(
            work_root,
            normalized_path_mode,
            Path("keys odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"--entity [{normalized_scenario_id}].key",
        ),
        attestation_founder_principal_id=ATTESTATION_FOUNDER_PRINCIPAL_ID,
        attestation_founder_key=smoke_path(
            work_root,
            normalized_path_mode,
            Path("attestation credentials")
            / UNICODE_WORKSPACE_SEGMENT
            / "founder"
            / f"{normalized_scenario_id}.fgatk",
        ),
        attestation_founder_passphrase=smoke_path(
            work_root,
            normalized_path_mode,
            Path("attestation credentials")
            / UNICODE_WORKSPACE_SEGMENT
            / "founder"
            / f"{normalized_scenario_id}.passphrase",
        ),
        backup_book=smoke_path(
            work_root,
            normalized_path_mode,
            Path("backup odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"-entity backup [{normalized_scenario_id}].sqlite",
        ),
        backup_book_key=smoke_path(
            work_root,
            normalized_path_mode,
            Path("backup odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"--entity backup [{normalized_scenario_id}].key",
        ),
        backup_id=str(
            uuid5(
                NAMESPACE_URL,
                "fingrind-release-smoke:" + normalized_scenario_id + ":backup",
            )
        ),
        restored_book=smoke_path(
            work_root,
            normalized_path_mode,
            Path("restored odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"-entity restored [{normalized_scenario_id}].sqlite",
        ),
        restored_book_key=smoke_path(
            work_root,
            normalized_path_mode,
            Path("restored odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"--entity restored [{normalized_scenario_id}].key",
        ),
        replacement_book_key=smoke_path(
            work_root,
            normalized_path_mode,
            Path("keys odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"--entity [{normalized_scenario_id}]-replacement.key",
        ),
        prompt_failure_book=smoke_path(
            work_root,
            normalized_path_mode,
            Path("books odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"prompt unavailable [{normalized_scenario_id}].sqlite",
        ),
        attestation_receipt=smoke_path(
            work_root,
            normalized_path_mode,
            Path("receipts odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "retained"
            / f"-receipt [{normalized_scenario_id}].fgar",
        ),
        trial_balance_pdf=smoke_path(
            work_root,
            normalized_path_mode,
            Path("reports odd") / f"trial balance [{normalized_scenario_id}].pdf",
        ),
        trial_balance_pdf_stderr_path=(
            work_root / "reports odd" / f"trial balance [{normalized_scenario_id}].stderr.txt"
        ),
        request_prefix=normalized_scenario_id,
        entity_name=ENTITY_NAME,
        accounting_kernel_profile=ACCOUNTING_KERNEL_PROFILE,
        accounting_framework_position=ACCOUNTING_FRAMEWORK_POSITION,
        entity_form=ENTITY_FORM,
        book_template_id=BOOK_TEMPLATE_ID,
        inventory_costing_doctrine=INVENTORY_COSTING_DOCTRINE,
        accounting_basis=ACCOUNTING_BASIS,
        functional_currency=FUNCTIONAL_CURRENCY,
        fiscal_year_start=FISCAL_YEAR_START,
        book_start_effective_date=BOOK_START_EFFECTIVE_DATE,
        starter_cash_account_code=STARTER_CASH_ACCOUNT_CODE,
        starter_cash_account_name=STARTER_CASH_ACCOUNT_NAME,
        starter_revenue_account_code=STARTER_REVENUE_ACCOUNT_CODE,
        starter_revenue_account_name=STARTER_REVENUE_ACCOUNT_NAME,
        bank_account_code=BANK_ACCOUNT_CODE,
        bank_account_name=BANK_ACCOUNT_NAME,
        expense_supplement_account_code=EXPENSE_SUPPLEMENT_ACCOUNT_CODE,
        expense_supplement_account_name=EXPENSE_SUPPLEMENT_ACCOUNT_NAME,
    )
