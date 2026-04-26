from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .models import ReleaseSmokeConfig


def prepare_fixture_directories(config: ReleaseSmokeConfig) -> None:
    for path in [
        config.request_sale.local_path,
        config.request_adjustment.local_path,
        config.invalid_request.local_path,
        config.declare_cash.local_path,
        config.declare_revenue.local_path,
        config.book.local_path,
        config.book_key.local_path,
        config.replacement_book_key.local_path,
        config.prompt_failure_book.local_path,
        config.trial_balance_pdf.local_path,
        config.trial_balance_pdf_stderr_path,
    ]:
        path.parent.mkdir(parents=True, exist_ok=True)


def write_acceptance_fixtures(config: ReleaseSmokeConfig) -> None:
    actor_prefix = config.actor_prefix
    write_json(
        config.request_sale.local_path,
        {
            "effectiveDate": "2026-04-07",
            "lines": [
                {
                    "accountCode": "1000",
                    "side": "DEBIT",
                    "currencyCode": "EUR",
                    "amount": "10.00",
                },
                {
                    "accountCode": "2000",
                    "side": "CREDIT",
                    "currencyCode": "EUR",
                    "amount": "10.00",
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
            "effectiveDate": "2026-04-08",
            "lines": [
                {
                    "accountCode": "1000",
                    "side": "CREDIT",
                    "currencyCode": "EUR",
                    "amount": "4.00",
                },
                {
                    "accountCode": "2000",
                    "side": "DEBIT",
                    "currencyCode": "EUR",
                    "amount": "4.00",
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
            "normalBalance": "DEBIT",
            "nonsenseOne": "unexpected",
            "nonsenseTwo": "unexpected",
        },
    )
    write_json(
        config.declare_cash.local_path,
        {"accountCode": "1000", "accountName": "Cash", "normalBalance": "DEBIT"},
    )
    write_json(
        config.declare_revenue.local_path,
        {"accountCode": "2000", "accountName": "Revenue", "normalBalance": "CREDIT"},
    )


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

