"""Assertions for fixture files that represent individual accounting requests."""

from __future__ import annotations

import json

from . import fixture_payloads


def assert_acceptance_request_fixtures(fixture_scenario, expected_source_document) -> None:
    assert_request_payload(
        json.loads(fixture_scenario.request_sale.local_path.read_text(encoding="utf-8")),
        "SALE_SETTLED",
        "23161157-7aff-5d55-b340-33a484925b90",
        expected_source_document("fixture-regression", "sale", "2026-04-07"),
    )
    assert_request_payload(
        json.loads(fixture_scenario.request_expense.local_path.read_text(encoding="utf-8")),
        "EXPENSE_SETTLED",
        "778a2374-e09a-5294-b2e6-2018b709d90e",
        expected_source_document("fixture-regression", "expense", "2026-04-08"),
    )
    taxed_sale_request = json.loads(
        fixture_scenario.request_taxed_sale.local_path.read_text(encoding="utf-8")
    )
    assert_request_payload(
        taxed_sale_request,
        "SALE_SETTLED",
        "1fc49d91-ff91-5399-a7ef-be3521161348",
        expected_source_document("fixture-regression", "taxed-sale", "2026-04-09"),
    )
    assert taxed_sale_request["tax"] == {
        "taxRegistrationId": fixture_payloads.PLAN_TAX_REGISTRATION_ID,
        "taxCode": "release-smoke-vat-sale",
    }
    assert_direct_journal_payload(
        json.loads(fixture_scenario.request_raw_journal.local_path.read_text(encoding="utf-8")),
        "DIRECT_JOURNAL",
        "aa0086ea-9b83-5835-937f-24948fd582b8",
        expected_source_document("fixture-regression", "transfer", "2026-04-08"),
        ["operating-bank", "cash"],
    )

    sale_request = json.loads(fixture_scenario.request_sale.local_path.read_text(encoding="utf-8"))
    expense_request = json.loads(
        fixture_scenario.request_expense.local_path.read_text(encoding="utf-8")
    )
    assert sale_request["cashAccountCode"] == "cash"
    assert sale_request["revenueAccountCode"] == "service-revenue"
    assert sale_request["amount"]["minorUnits"] == "1000"
    assert expense_request["expenseAccountCode"] == "misc-expense"
    assert expense_request["cashAccountCode"] == "cash"
    assert expense_request["amount"]["minorUnits"] == "400"
    assert (
        json.loads(fixture_scenario.declare_bank_account.local_path.read_text(encoding="utf-8"))[
            "accountNodeKind"
        ]
        == "POSTABLE"
    )
    assert (
        json.loads(fixture_scenario.declare_bank_account.local_path.read_text(encoding="utf-8"))[
            "cashFlowAssetClassification"
        ]
        == "CASH_AND_CASH_EQUIVALENT"
    )
    assert (
        json.loads(
            fixture_scenario.declare_expense_supplement.local_path.read_text(encoding="utf-8")
        )["accountNodeKind"]
        == "POSTABLE"
    )


def assert_request_payload(
    request_payload: dict[str, object],
    expected_entry_kind: str,
    expected_command_id: str,
    expected_document: dict[str, str],
) -> None:
    assert request_payload["entryKind"] == expected_entry_kind
    assert "recipeKind" not in request_payload
    assert request_payload["evidence"] == {
        "sourceDocuments": [expected_document],
        "approvals": [],
    }
    assert request_payload["provenance"]["commandId"] == expected_command_id


def assert_direct_journal_payload(
    request_payload: dict[str, object],
    expected_entry_kind: str,
    expected_command_id: str,
    expected_document: dict[str, str],
    expected_account_codes: list[str],
) -> None:
    assert request_payload["entryKind"] == expected_entry_kind
    assert "recipeKind" not in request_payload
    assert request_payload["evidence"] == {
        "sourceDocuments": [expected_document],
        "approvals": [],
    }
    assert request_payload["provenance"]["commandId"] == expected_command_id
    lines = request_payload["lines"]
    assert isinstance(lines, list)
    assert len(lines) == 2
    assert [line["accountCode"] for line in lines] == expected_account_codes
    assert [line["side"] for line in lines] == ["DEBIT", "CREDIT"]
