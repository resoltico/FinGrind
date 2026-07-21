from __future__ import annotations

from pathlib import Path

from .models import ReleaseSmokeFailure, ReleaseSmokeScenario, SmokePath

ARGUMENT_PATH_MODE_ABSOLUTE = "absolute"
ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE = "relative-to-work-root"
UNICODE_WORKSPACE_SEGMENT = "Rīga büro"
ENTITY_NAME = "Acme Studio"
ACCOUNTING_KERNEL_PROFILE = "internal-management-bookkeeping-kernel"
ACCOUNTING_FRAMEWORK_POSITION = "NON_STATUTORY_INTERNAL_MANAGEMENT"
ENTITY_FORM = "OWNER_MANAGED_SINGLE_ENTITY"
BOOK_TEMPLATE_ID = "OWNER_MANAGED_SERVICE"
ACCOUNTING_BASIS = "CASH"
FUNCTIONAL_CURRENCY = "EUR"
FISCAL_YEAR_START = "01-01"
STARTER_CASH_ACCOUNT_CODE = "cash"
STARTER_CASH_ACCOUNT_NAME = "Cash"
STARTER_REVENUE_ACCOUNT_CODE = "service-revenue"
STARTER_REVENUE_ACCOUNT_NAME = "Service Revenue"
BANK_ACCOUNT_CODE = "operating-bank"
BANK_ACCOUNT_NAME = "Operating Bank"
EXPENSE_SUPPLEMENT_ACCOUNT_CODE = "misc-expense"
EXPENSE_SUPPLEMENT_ACCOUNT_NAME = "Misc Expense"


def build_release_smoke_scenario(
    work_root: Path,
    argument_path_mode: str,
    scenario_id: str,
) -> ReleaseSmokeScenario:
    normalized_scenario_id = require_scenario_id(scenario_id)
    normalized_path_mode = require_argument_path_mode(argument_path_mode)

    return ReleaseSmokeScenario(
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
        trial_balance_pdf=smoke_path(
            work_root,
            normalized_path_mode,
            Path("reports odd") / f"trial balance [{normalized_scenario_id}].pdf",
        ),
        trial_balance_pdf_stderr_path=(
            work_root / "reports odd" / f"trial balance [{normalized_scenario_id}].stderr.txt"
        ),
        second_page_command_id=normalized_scenario_id + "-sale",
        request_prefix=normalized_scenario_id,
        entity_name=ENTITY_NAME,
        accounting_kernel_profile=ACCOUNTING_KERNEL_PROFILE,
        accounting_framework_position=ACCOUNTING_FRAMEWORK_POSITION,
        entity_form=ENTITY_FORM,
        book_template_id=BOOK_TEMPLATE_ID,
        accounting_basis=ACCOUNTING_BASIS,
        functional_currency=FUNCTIONAL_CURRENCY,
        fiscal_year_start=FISCAL_YEAR_START,
        starter_cash_account_code=STARTER_CASH_ACCOUNT_CODE,
        starter_cash_account_name=STARTER_CASH_ACCOUNT_NAME,
        starter_revenue_account_code=STARTER_REVENUE_ACCOUNT_CODE,
        starter_revenue_account_name=STARTER_REVENUE_ACCOUNT_NAME,
        bank_account_code=BANK_ACCOUNT_CODE,
        bank_account_name=BANK_ACCOUNT_NAME,
        expense_supplement_account_code=EXPENSE_SUPPLEMENT_ACCOUNT_CODE,
        expense_supplement_account_name=EXPENSE_SUPPLEMENT_ACCOUNT_NAME,
    )


def smoke_path(work_root: Path, argument_path_mode: str, relative_path: Path) -> SmokePath:
    local_path = work_root / relative_path
    if argument_path_mode == ARGUMENT_PATH_MODE_ABSOLUTE:
        return SmokePath(
            relative_path=relative_path, local_path=local_path, argument=str(local_path)
        )
    if argument_path_mode == ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE:
        return SmokePath(
            relative_path=relative_path,
            local_path=local_path,
            argument=relative_path.as_posix(),
        )
    raise ReleaseSmokeFailure("unsupported release-smoke argument path mode: " + argument_path_mode)


def require_scenario_id(scenario_id: str) -> str:
    normalized = scenario_id.strip()
    if not normalized:
        raise ReleaseSmokeFailure(
            "environment variable FINGRIND_RELEASE_SMOKE_SCENARIO_ID must be one non-blank slug"
        )
    if normalized != normalized.lower():
        raise ReleaseSmokeFailure(
            "environment variable FINGRIND_RELEASE_SMOKE_SCENARIO_ID must be lowercase"
        )
    allowed = set("abcdefghijklmnopqrstuvwxyz0123456789-")
    if any(character not in allowed for character in normalized):
        raise ReleaseSmokeFailure(
            "environment variable FINGRIND_RELEASE_SMOKE_SCENARIO_ID must contain only lowercase letters, digits, and hyphens"
        )
    if normalized.startswith("-") or normalized.endswith("-") or "--" in normalized:
        raise ReleaseSmokeFailure(
            "environment variable FINGRIND_RELEASE_SMOKE_SCENARIO_ID must use single internal hyphens only"
        )
    return normalized


def require_argument_path_mode(argument_path_mode: str) -> str:
    normalized = argument_path_mode.strip()
    if normalized in (
        ARGUMENT_PATH_MODE_ABSOLUTE,
        ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE,
    ):
        return normalized
    raise ReleaseSmokeFailure(
        "environment variable FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE must be one of: absolute, relative-to-work-root"
    )
