"""Regression checks for release-smoke scenario and fixture contracts."""

from __future__ import annotations

import json
import pathlib
import re
import tempfile

from contract_values import load_contract_values


def assert_release_smoke_scenarios(
    build_release_smoke_scenario,
    absolute_mode: str,
    relative_mode: str,
) -> None:
    bundle = build_release_smoke_scenario(
        pathlib.Path("/tmp/workspace odd/Rīga büro/2026 Q2 close"),
        absolute_mode,
        "bundle-acceptance",
    )
    assert "Rīga büro" in str(bundle.book.local_path)
    assert bundle.book.argument == str(bundle.book.local_path)
    assert bundle.backup_book.argument == str(bundle.backup_book.local_path)
    assert bundle.second_page_command_id == "bundle-acceptance-sale"
    assert bundle.accounting_basis == "CASH"

    docker = build_release_smoke_scenario(
        pathlib.Path("/workdir"),
        relative_mode,
        "docker-acceptance",
    )
    assert docker.book.argument == "books odd/Rīga büro/nested/-entity [docker-acceptance].sqlite"
    assert (
        docker.backup_book.argument
        == "backup odd/Rīga büro/nested/-entity backup [docker-acceptance].sqlite"
    )
    assert (
        docker.replacement_book_key.argument
        == "keys odd/Rīga büro/nested/--entity [docker-acceptance]-replacement.key"
    )
    assert (
        docker.restored_book_key.argument
        == "restored odd/Rīga büro/nested/--entity restored [docker-acceptance].key"
    )
    assert docker.request_prefix == "docker-acceptance"
    assert docker.accounting_basis == "CASH"


def assert_fixture_generation(
    build_release_smoke_scenario,
    prepare_fixture_directories,
    write_acceptance_fixtures,
    expected_source_document,
    absolute_mode: str,
) -> None:
    with tempfile.TemporaryDirectory() as fixture_dir:
        fixture_scenario = build_release_smoke_scenario(
            pathlib.Path(fixture_dir),
            absolute_mode,
            "fixture-regression",
        )
        prepare_fixture_directories(fixture_scenario)
        write_acceptance_fixtures(fixture_scenario)

        assert_request_payload(
            json.loads(fixture_scenario.request_sale.local_path.read_text(encoding="utf-8")),
            "SALE_SETTLED",
            "fixture-regression-sale",
            expected_source_document("fixture-regression", "sale", "2026-04-07"),
        )
        assert_request_payload(
            json.loads(fixture_scenario.request_expense.local_path.read_text(encoding="utf-8")),
            "EXPENSE_SETTLED",
            "fixture-regression-expense",
            expected_source_document("fixture-regression", "expense", "2026-04-08"),
        )
        assert_direct_journal_payload(
            json.loads(fixture_scenario.request_raw_journal.local_path.read_text(encoding="utf-8")),
            "DIRECT_JOURNAL",
            "fixture-regression-transfer",
            expected_source_document("fixture-regression", "transfer", "2026-04-08"),
            ["operating-bank", "cash"],
        )

        sale_request = json.loads(
            fixture_scenario.request_sale.local_path.read_text(encoding="utf-8")
        )
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
            json.loads(
                fixture_scenario.declare_bank_account.local_path.read_text(encoding="utf-8")
            )["accountNodeKind"]
            == "POSTABLE"
        )
        assert (
            json.loads(
                fixture_scenario.declare_bank_account.local_path.read_text(encoding="utf-8")
            )["cashFlowAssetClassification"]
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


def assert_operation_id_references(repo_root: pathlib.Path) -> None:
    contract = load_contract_values(repo_root)
    operation_ids = contract["operationIds"]
    referenced_operation_keys = {
        match.group(1)
        for path in (repo_root / "scripts" / "release_smoke_workflow").glob("*.py")
        for match in re.finditer(
            r'operation_ids\["([a-zA-Z0-9]+)"\]', path.read_text(encoding="utf-8")
        )
    }
    unknown_operation_keys = sorted(referenced_operation_keys.difference(operation_ids))
    if unknown_operation_keys:
        raise AssertionError(
            "release-smoke workflow references unknown operation-id keys: "
            + ", ".join(unknown_operation_keys)
        )
