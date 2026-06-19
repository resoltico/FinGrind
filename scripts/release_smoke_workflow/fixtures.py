from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .fixture_payloads import (
    cash_expense_request,
    cash_revenue_request,
    declare_account_request,
    raw_transfer_request,
)
from .models import ReleaseSmokeConfig


def prepare_fixture_directories(config: ReleaseSmokeConfig) -> None:
    # Security-sensitive book and key parents must be created by the CLI surface itself so the
    # acceptance workflow proves the same owner-only hardening contract that real operators use.
    for path in [
        config.request_sale.local_path,
        config.request_expense.local_path,
        config.request_raw_journal.local_path,
        config.invalid_request.local_path,
        config.declare_bank_account.local_path,
        config.declare_expense_supplement.local_path,
        config.trial_balance_pdf.local_path,
        config.trial_balance_pdf_stderr_path,
    ]:
        path.parent.mkdir(parents=True, exist_ok=True)


def write_acceptance_fixtures(config: ReleaseSmokeConfig) -> None:
    actor_prefix = config.actor_prefix
    write_json(
        config.request_sale.local_path,
        cash_revenue_request(
            actor_prefix=actor_prefix,
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
        cash_expense_request(
            actor_prefix=actor_prefix,
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
        config.request_raw_journal.local_path,
        raw_transfer_request(
            actor_prefix=actor_prefix,
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
        declare_account_request(
            account_code="invalid-supplement",
            account_name="Invalid Supplement",
            account_type="ASSET",
            account_role="ORDINARY",
            account_node_kind="POSTABLE",
            financial_position_line_classification="CURRENT_ASSET",
            nonsense_one="unexpected",
            nonsense_two="unexpected",
        ),
    )
    write_json(
        config.declare_bank_account.local_path,
        declare_account_request(
            account_code=config.bank_account_code,
            account_name=config.bank_account_name,
            account_type="ASSET",
            account_role="ORDINARY",
            account_node_kind="POSTABLE",
            financial_position_line_classification="CURRENT_ASSET",
        ),
    )
    write_json(
        config.declare_expense_supplement.local_path,
        declare_account_request(
            account_code=config.expense_supplement_account_code,
            account_name=config.expense_supplement_account_name,
            account_type="EXPENSE",
            account_role="ORDINARY",
            account_node_kind="POSTABLE",
            profit_and_loss_line_classification="OPERATING_EXPENSE",
        ),
    )


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
