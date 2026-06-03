"""Regression checks for release-smoke scenario and fixture contracts."""

from __future__ import annotations

import json
import pathlib
import tempfile


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
    assert docker.actor_prefix == "docker-acceptance"


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
            "CASH_REVENUE",
            "fixture-regression-sale",
            expected_source_document("fixture-regression", "sale", "2026-04-07"),
        )
        assert_request_payload(
            json.loads(fixture_scenario.request_expense.local_path.read_text(encoding="utf-8")),
            "CASH_EXPENSE",
            "fixture-regression-expense",
            expected_source_document("fixture-regression", "expense", "2026-04-08"),
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
                fixture_scenario.declare_asset_supplement.local_path.read_text(encoding="utf-8")
            )["accountNodeKind"]
            == "POSTABLE"
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
    assert request_payload["evidence"] == {
        "sourceDocuments": [expected_document],
        "approvals": [],
    }
    assert request_payload["provenance"]["commandId"] == expected_command_id
