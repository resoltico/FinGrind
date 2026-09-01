from __future__ import annotations

import sys
from pathlib import Path

from .models import ReleaseSmokeConfig, SmokePath


def write_bridge_script(temp_path: Path) -> Path:
    bridge_script = temp_path / "bridge.py"
    bridge_script.write_text(
        (
            "import json\n"
            "import pathlib\n"
            "import sys\n"
            "request = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding='utf-8'))\n"
            "arguments = json.loads(pathlib.Path(request['argumentsFile']).read_text(encoding='utf-8'))\n"
            "stdin_text = (\n"
            "    pathlib.Path(request['stdinFile']).read_text(encoding='utf-8')\n"
            "    if request['stdinFile'] is not None\n"
            "    else None\n"
            ")\n"
            "json.dump({'arguments': arguments, 'stdinText': stdin_text}, sys.stdout, ensure_ascii=False)\n"
            "sys.stdout.write('\\n')"
        ),
        encoding="utf-8",
    )
    return bridge_script


def smoke_path(local_root: Path, relative_path: Path) -> SmokePath:
    return SmokePath(
        relative_path=relative_path,
        local_path=local_root / relative_path,
        argument=str(local_root / relative_path),
    )


def base_bridge_config(
    repo_root: Path,
    temp_path: Path,
    bridge_script: Path,
    dummy: SmokePath,
    *,
    runtime_distribution_key: str,
    reported_work_root: Path | None,
    book_key_output_permissions: str,
    pdf_path: SmokePath,
    pdf_argument_override: str | None,
    stderr_path: Path,
    label: str,
) -> ReleaseSmokeConfig:
    resolved_pdf_path = pdf_path
    if pdf_argument_override is not None:
        resolved_pdf_path = SmokePath(
            relative_path=pdf_path.relative_path,
            local_path=pdf_path.local_path,
            argument=pdf_argument_override,
        )
    return ReleaseSmokeConfig(
        label=label,
        repo_root=repo_root,
        work_root=temp_path,
        command_prefix=["unused-direct-command"],
        command_bridge_prefix=[sys.executable, str(bridge_script)],
        command_cwd=None,
        reported_work_root=reported_work_root,
        command_env_drop=[],
        command_env_set={},
        runtime_distribution_key=runtime_distribution_key,
        expect_loaded_sqlite_details=True,
        expect_bundle_home_property=True,
        book_key_output_permissions=book_key_output_permissions,
        request_sale=dummy,
        request_expense=dummy,
        request_taxed_sale=dummy,
        request_raw_journal=dummy,
        invalid_request=dummy,
        declare_bank_account=dummy,
        declare_expense_supplement=dummy,
        book=dummy,
        book_key=dummy,
        attestation_founder_principal_id="4bc17dd7-145f-4ea7-bb55-167ca2f6ac11",
        attestation_founder_key=dummy,
        attestation_founder_passphrase=dummy,
        backup_book=dummy,
        backup_book_key=dummy,
        backup_id="96ace780-ce14-5177-9c49-3917db69edae",
        restored_book=dummy,
        restored_book_key=dummy,
        replacement_book_key=dummy,
        prompt_failure_book=dummy,
        attestation_receipt=dummy,
        trial_balance_pdf=resolved_pdf_path,
        trial_balance_pdf_stderr_path=stderr_path,
        request_prefix="bridge",
        open_book_mode="book-key-file",
        entity_name="Acme Studio",
        accounting_kernel_profile="internal-management-bookkeeping-kernel",
        accounting_framework_position="NON_STATUTORY_INTERNAL_MANAGEMENT",
        entity_form="OWNER_MANAGED_SINGLE_ENTITY",
        book_template_id="OWNER_MANAGED_SERVICE",
        inventory_costing_doctrine=None,
        accounting_basis="CASH",
        functional_currency="EUR",
        fiscal_year_start="01-01",
        book_start_effective_date="2026-01-01",
        starter_cash_account_code="cash",
        starter_cash_account_name="Cash",
        starter_revenue_account_code="service-revenue",
        starter_revenue_account_name="Service Revenue",
        bank_account_code="operating-bank",
        bank_account_name="Operating Bank",
        expense_supplement_account_code="misc-expense",
        expense_supplement_account_name="Misc Expense",
        native_sqlite_probe_classpath=str(temp_path / "native-sqlite-format-boundary-probe.jar"),
    )
