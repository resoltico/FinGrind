from __future__ import annotations

import json
from pathlib import Path
from typing import Any

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
        {
            "postingKind": "STANDARD",
            "effectiveDate": "2026-04-07",
            "lines": [
                {
                    "accountCode": "1000",
                    "side": "DEBIT",
                    "amount": {
                        "currencyCode": "EUR",
                        "minorUnits": "1000",
                    },
                },
                {
                    "accountCode": "2000",
                    "side": "CREDIT",
                    "amount": {
                        "currencyCode": "EUR",
                        "minorUnits": "1000",
                    },
                },
            ],
            "provenance": {
                "actorId": actor_prefix,
                "actorType": "AGENT",
                "commandId": actor_prefix + "-sale",
                "idempotencyKey": actor_prefix + "-idem-1",
                "causationId": actor_prefix + "-cause-1",
            },
        },
    )
    write_json(
        config.request_adjustment.local_path,
        {
            "postingKind": "STANDARD",
            "effectiveDate": "2026-04-08",
            "lines": [
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
            "provenance": {
                "actorId": actor_prefix,
                "actorType": "AGENT",
                "commandId": actor_prefix + "-adjustment",
                "idempotencyKey": actor_prefix + "-idem-2",
                "causationId": actor_prefix + "-cause-2",
            },
        },
    )
    write_json(
        config.invalid_request.local_path,
        {
            "accountCode": "1000",
            "accountName": "Cash",
            "accountType": "ASSET",
            "accountRole": "ORDINARY",
            "financialPositionLineClassification": "CURRENT_ASSET",
            "profitAndLossLineClassification": None,
            "nonsenseOne": "unexpected",
            "nonsenseTwo": "unexpected",
        },
    )
    write_json(
        config.declare_cash.local_path,
        {
            "accountCode": "1000",
            "accountName": "Cash",
            "accountType": "ASSET",
            "accountRole": "ORDINARY",
            "financialPositionLineClassification": "CURRENT_ASSET",
            "profitAndLossLineClassification": None,
        },
    )
    write_json(
        config.declare_revenue.local_path,
        {
            "accountCode": "2000",
            "accountName": "Revenue",
            "accountType": "REVENUE",
            "accountRole": "ORDINARY",
            "financialPositionLineClassification": None,
            "profitAndLossLineClassification": "OPERATING_REVENUE",
        },
    )


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
