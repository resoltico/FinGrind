from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from . import fixture_payloads, plan_payloads
from .models import ReleaseSmokeConfig, SmokePath
from .scenario_paths import sibling_smoke_path


@dataclass(frozen=True)
class LedgerPlanFixturePaths:
    administrative: SmokePath
    posting: SmokePath
    read_only: SmokePath
    reactivate_rename: SmokePath
    reactivate_rename_seed: SmokePath
    reactivate_rename_retire: SmokePath


def write_acceptance_fixtures(config: ReleaseSmokeConfig) -> None:
    request_prefix = config.request_prefix
    config.attestation_founder_passphrase.local_path.write_text(
        "release-smoke-founder-passphrase\n", encoding="utf-8"
    )
    if os.name == "posix":
        config.attestation_founder_passphrase.local_path.chmod(0o600)
    write_json(
        config.request_sale.local_path,
        fixture_payloads.sale_request(
            request_prefix=request_prefix,
            effective_date="2026-04-07",
            cash_account_code=config.starter_cash_account_code,
            revenue_account_code=config.starter_revenue_account_code,
            minor_units="1000",
            evidence_suffix="sale",
            command_suffix="sale",
            idempotency_suffix="idem-1",
            causation_suffix="cause-1",
        ),
    )
    write_json(
        config.request_expense.local_path,
        fixture_payloads.expense_request(
            request_prefix=request_prefix,
            effective_date="2026-04-08",
            expense_account_code=config.expense_supplement_account_code,
            cash_account_code=config.starter_cash_account_code,
            minor_units="400",
            evidence_suffix="expense",
            command_suffix="expense",
            idempotency_suffix="idem-2",
            causation_suffix="cause-2",
        ),
    )
    write_json(
        config.request_taxed_sale.local_path,
        fixture_payloads.taxed_sale_request(
            request_prefix=request_prefix,
            effective_date="2026-04-09",
            cash_account_code=config.starter_cash_account_code,
            revenue_account_code=config.starter_revenue_account_code,
            minor_units="1000",
            tax_registration_id=fixture_payloads.PLAN_TAX_REGISTRATION_ID,
            tax_code="release-smoke-vat-sale",
            evidence_suffix="taxed-sale",
            command_suffix="taxed-sale",
            idempotency_suffix="idem-taxed-sale",
            causation_suffix="cause-taxed-sale",
        ),
    )
    write_json(
        config.request_raw_journal.local_path,
        fixture_payloads.raw_transfer_request(
            request_prefix=request_prefix,
            effective_date="2026-04-08",
            source_account_code=config.starter_cash_account_code,
            destination_account_code=config.bank_account_code,
            minor_units="250",
            evidence_suffix="transfer",
            command_suffix="transfer",
            idempotency_suffix="idem-3",
            causation_suffix="cause-3",
        ),
    )
    write_json(
        config.invalid_request.local_path,
        fixture_payloads.declare_account_request(
            account_code="invalid-supplement",
            account_name="Invalid Supplement",
            account_type="ASSET",
            account_node_kind="POSTABLE",
            financial_position_line_classification="CURRENT_ASSET",
            cash_flow_asset_classification="CASH_AND_CASH_EQUIVALENT",
            nonsense_one="unexpected",
            nonsense_two="unexpected",
        ),
    )
    write_json(
        config.declare_bank_account.local_path,
        fixture_payloads.declare_account_request(
            account_code=config.bank_account_code,
            account_name=config.bank_account_name,
            account_type="ASSET",
            account_node_kind="POSTABLE",
            financial_position_line_classification="CURRENT_ASSET",
            cash_flow_asset_classification="CASH_AND_CASH_EQUIVALENT",
        ),
    )
    write_json(
        config.declare_expense_supplement.local_path,
        fixture_payloads.declare_account_request(
            account_code=config.expense_supplement_account_code,
            account_name=config.expense_supplement_account_name,
            account_type="EXPENSE",
            account_node_kind="POSTABLE",
            profit_and_loss_line_classification="OPERATING_EXPENSE",
        ),
    )


def write_ledger_plan_fixtures(config: ReleaseSmokeConfig) -> LedgerPlanFixturePaths:
    administrative = sibling_smoke_path(
        config.request_sale,
        f"ledger-plan-administrative [{config.request_prefix}].json",
    )
    posting = sibling_smoke_path(
        config.request_sale,
        f"ledger-plan-posting [{config.request_prefix}].json",
    )
    read_only = sibling_smoke_path(
        config.request_sale,
        f"ledger-plan-read-only [{config.request_prefix}].json",
    )
    reactivate_rename = sibling_smoke_path(
        config.request_sale,
        f"ledger-plan-reactivate-rename [{config.request_prefix}].json",
    )
    reactivate_rename_seed = sibling_smoke_path(
        config.request_sale,
        f"ledger-plan-reactivate-rename-seed [{config.request_prefix}].json",
    )
    reactivate_rename_retire = sibling_smoke_path(
        config.request_sale,
        f"ledger-plan-reactivate-rename-retire [{config.request_prefix}].json",
    )
    write_json(
        administrative.local_path,
        plan_payloads.administrative_ledger_plan_request(config.request_prefix),
    )
    write_json(
        posting.local_path,
        plan_payloads.posting_ledger_plan_request(
            config.request_prefix,
            config.starter_cash_account_code,
            config.bank_account_code,
        ),
    )
    write_json(
        read_only.local_path,
        plan_payloads.read_only_ledger_plan_request(config.request_prefix),
    )
    write_json(
        reactivate_rename.local_path,
        plan_payloads.reactivate_rename_ledger_plan_request(config.request_prefix),
    )
    write_json(
        reactivate_rename_seed.local_path,
        fixture_payloads.declare_account_request(
            account_code=fixture_payloads.PLAN_REACTIVATE_RENAME_ACCOUNT_CODE,
            account_name=fixture_payloads.PLAN_REACTIVATE_RENAME_INITIAL_NAME,
            account_type="ASSET",
            account_node_kind="POSTABLE",
            financial_position_line_classification="CURRENT_ASSET",
            cash_flow_asset_classification="NON_CASH",
        ),
    )
    write_json(
        reactivate_rename_retire.local_path,
        {"accountCode": fixture_payloads.PLAN_REACTIVATE_RENAME_ACCOUNT_CODE},
    )
    return LedgerPlanFixturePaths(
        administrative=administrative,
        posting=posting,
        read_only=read_only,
        reactivate_rename=reactivate_rename,
        reactivate_rename_seed=reactivate_rename_seed,
        reactivate_rename_retire=reactivate_rename_retire,
    )


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
