"""Assertions for aggregate-plan fixture files."""

from __future__ import annotations

import json

from . import fixture_payloads, plan_payloads
from .fixture_request_contract import assert_direct_journal_payload


def assert_ledger_plan_fixtures(plan_fixtures, expected_source_document) -> None:
    administrative_plan = json.loads(
        plan_fixtures.administrative.local_path.read_text(encoding="utf-8")
    )
    assert administrative_plan["planId"] == plan_payloads.administrative_ledger_plan_id(
        "fixture-regression"
    )
    administrative_steps = administrative_plan["steps"]
    assert [step["kind"] for step in administrative_steps] == [
        "declare-account",
        "declare-account",
        "declare-tax-registration",
    ]
    assert (
        administrative_steps[0]["declareAccount"]["accountCode"]
        == fixture_payloads.PLAN_TAX_PAYABLE_ACCOUNT_CODE
    )
    assert (
        administrative_steps[1]["declareAccount"]["accountCode"]
        == fixture_payloads.PLAN_TAX_RECOVERABLE_ACCOUNT_CODE
    )
    registration = administrative_steps[2]["declareTaxRegistration"]
    assert registration["taxRegistrationId"] == fixture_payloads.PLAN_TAX_REGISTRATION_ID
    assert registration["payableAccountCode"] == fixture_payloads.PLAN_TAX_PAYABLE_ACCOUNT_CODE
    assert (
        registration["recoverableAccountCode"] == fixture_payloads.PLAN_TAX_RECOVERABLE_ACCOUNT_CODE
    )

    posting_plan = json.loads(plan_fixtures.posting.local_path.read_text(encoding="utf-8"))
    assert posting_plan["planId"] == plan_payloads.posting_ledger_plan_id("fixture-regression")
    assert [step["stepId"] for step in posting_plan["steps"]] == ["post-plan-bank-transfer"]
    assert [step["kind"] for step in posting_plan["steps"]] == ["post-entry"]
    posting_request = posting_plan["steps"][0]["posting"]
    assert_direct_journal_payload(
        posting_request,
        "DIRECT_JOURNAL",
        "0b5fe4c0-e1c2-51a1-bfd4-523507dbac11",
        expected_source_document("fixture-regression", "plan-transfer", "2026-04-09"),
        ["operating-bank", "cash"],
    )
    assert posting_request["provenance"]["idempotencyKey"] == (
        "fixture-regression-idem-plan-transfer"
    )

    read_only_plan = json.loads(plan_fixtures.read_only.local_path.read_text(encoding="utf-8"))
    assert read_only_plan == {
        "planId": plan_payloads.read_only_ledger_plan_id("fixture-regression"),
        "steps": [
            {
                "stepId": "list-accounts-after-administration",
                "kind": "list-accounts",
                "query": {"limit": 50},
            }
        ],
    }

    reactivate_rename_seed = json.loads(
        plan_fixtures.reactivate_rename_seed.local_path.read_text(encoding="utf-8")
    )
    assert (
        reactivate_rename_seed["accountCode"]
        == fixture_payloads.PLAN_REACTIVATE_RENAME_ACCOUNT_CODE
    )
    assert (
        reactivate_rename_seed["accountName"]
        == fixture_payloads.PLAN_REACTIVATE_RENAME_INITIAL_NAME
    )
    assert json.loads(
        plan_fixtures.reactivate_rename_retire.local_path.read_text(encoding="utf-8")
    ) == {"accountCode": fixture_payloads.PLAN_REACTIVATE_RENAME_ACCOUNT_CODE}

    reactivate_rename_plan = json.loads(
        plan_fixtures.reactivate_rename.local_path.read_text(encoding="utf-8")
    )
    assert reactivate_rename_plan["planId"] == plan_payloads.reactivate_rename_ledger_plan_id(
        "fixture-regression"
    )
    reactivate_rename_steps = reactivate_rename_plan["steps"]
    assert [step["stepId"] for step in reactivate_rename_steps] == [
        "reactivate-plan-target",
        "rename-plan-target",
    ]
    assert [step["kind"] for step in reactivate_rename_steps] == [
        "declare-account",
        "declare-account",
    ]
    assert [step["declareAccount"]["accountCode"] for step in reactivate_rename_steps] == [
        fixture_payloads.PLAN_REACTIVATE_RENAME_ACCOUNT_CODE,
        fixture_payloads.PLAN_REACTIVATE_RENAME_ACCOUNT_CODE,
    ]
    assert [step["declareAccount"]["accountName"] for step in reactivate_rename_steps] == [
        fixture_payloads.PLAN_REACTIVATE_RENAME_INITIAL_NAME,
        fixture_payloads.PLAN_REACTIVATE_RENAME_FINAL_NAME,
    ]
