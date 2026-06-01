from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .evidence_fixtures import posting_evidence, posting_provenance
from .models import ReleaseSmokeConfig


def prepare_fixture_directories(config: ReleaseSmokeConfig) -> None:
    # Security-sensitive book and key parents must be created by the CLI surface itself so the
    # acceptance workflow proves the same owner-only hardening contract that real operators use.
    for path in [
        config.request_sale.local_path,
        config.request_adjustment.local_path,
        config.invalid_request.local_path,
        config.declare_cash.local_path,
        config.declare_revenue.local_path,
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
            cash_account_code="1000",
            revenue_account_code="2000",
            minor_units="1000",
            evidence_suffix="sale",
            command_suffix="sale",
            idempotency_suffix="idem-1",
            causation_suffix="cause-1",
        ),
    )
    write_json(
        config.request_adjustment.local_path,
        correction_adjustment_request(
            actor_prefix=actor_prefix,
            effective_date="2026-04-08",
            lines=[
                {
                    "accountCode": "1000",
                    "side": "CREDIT",
                    "amount": {
                        "currencyCode": "EUR",
                        "minorUnits": "400",
                    },
                },
                {
                    "accountCode": "2000",
                    "side": "DEBIT",
                    "amount": {
                        "currencyCode": "EUR",
                        "minorUnits": "400",
                    },
                },
            ],
            evidence_suffix="adjustment",
            command_suffix="adjustment",
            idempotency_suffix="idem-2",
            causation_suffix="cause-2",
        ),
    )
    write_json(
        config.invalid_request.local_path,
        declare_account_request(
            account_code="1000",
            account_name="Cash",
            account_type="ASSET",
            account_role="ORDINARY",
            account_node_kind="POSTABLE",
            financial_position_line_classification="CURRENT_ASSET",
            nonsense_one="unexpected",
            nonsense_two="unexpected",
        ),
    )
    write_json(
        config.declare_cash.local_path,
        declare_account_request(
            account_code="1000",
            account_name="Cash",
            account_type="ASSET",
            account_role="ORDINARY",
            account_node_kind="POSTABLE",
            financial_position_line_classification="CURRENT_ASSET",
        ),
    )
    write_json(
        config.declare_revenue.local_path,
        declare_account_request(
            account_code="2000",
            account_name="Revenue",
            account_type="REVENUE",
            account_role="ORDINARY",
            account_node_kind="POSTABLE",
            profit_and_loss_line_classification="OPERATING_REVENUE",
        ),
    )


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def cash_revenue_request(
    *,
    actor_prefix: str,
    effective_date: str,
    cash_account_code: str,
    revenue_account_code: str,
    minor_units: str,
    evidence_suffix: str,
    command_suffix: str,
    idempotency_suffix: str,
    causation_suffix: str,
) -> dict[str, Any]:
    return {
        "entryKind": "CASH_REVENUE",
        "effectiveDate": effective_date,
        "cashAccountCode": cash_account_code,
        "revenueAccountCode": revenue_account_code,
        "amount": {
            "currencyCode": "EUR",
            "minorUnits": minor_units,
        },
        "evidence": posting_evidence(actor_prefix, evidence_suffix, effective_date),
        "provenance": posting_provenance(
            actor_prefix, command_suffix, idempotency_suffix, causation_suffix
        ),
    }


def correction_adjustment_request(
    *,
    actor_prefix: str,
    effective_date: str,
    lines: list[dict[str, Any]],
    evidence_suffix: str,
    command_suffix: str,
    idempotency_suffix: str,
    causation_suffix: str,
) -> dict[str, Any]:
    return {
        "entryKind": "CORRECTION_ADJUSTMENT",
        "effectiveDate": effective_date,
        "lines": lines,
        "evidence": posting_evidence(actor_prefix, evidence_suffix, effective_date),
        "provenance": posting_provenance(
            actor_prefix, command_suffix, idempotency_suffix, causation_suffix
        ),
    }


def declare_account_request(
    *,
    account_code: str,
    account_name: str,
    account_type: str,
    account_role: str,
    account_node_kind: str,
    financial_position_line_classification: str | None = None,
    profit_and_loss_line_classification: str | None = None,
    nonsense_one: str | None = None,
    nonsense_two: str | None = None,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "accountCode": account_code,
        "accountName": account_name,
        "accountType": account_type,
        "accountRole": account_role,
        "accountNodeKind": account_node_kind,
    }
    if financial_position_line_classification is not None:
        payload["financialPositionLineClassification"] = financial_position_line_classification
    if profit_and_loss_line_classification is not None:
        payload["profitAndLossLineClassification"] = profit_and_loss_line_classification
    if nonsense_one is not None:
        payload["nonsenseOne"] = nonsense_one
    if nonsense_two is not None:
        payload["nonsenseTwo"] = nonsense_two
    return payload
