"""Regression checks for release-smoke fixture generation."""

from __future__ import annotations

import os
import pathlib
import stat
import tempfile

from .fixture_plan_contract import assert_ledger_plan_fixtures
from .fixture_request_contract import assert_acceptance_request_fixtures
from .models import ReleaseSmokeFailure
from .windows_owner_only_directory_contract import assert_windows_owner_only_directory_contract


def assert_fixture_generation(
    build_release_smoke_scenario,
    prepare_fixture_directories,
    write_acceptance_fixtures,
    write_ledger_plan_fixtures,
    expected_source_document,
    absolute_mode: str,
) -> None:
    assert_windows_owner_only_directory_contract()
    with tempfile.TemporaryDirectory() as fixture_dir:
        fixture_scenario = build_release_smoke_scenario(
            pathlib.Path(fixture_dir),
            absolute_mode,
            "fixture-regression",
        )
        prepare_fixture_directories(fixture_scenario)
        write_acceptance_fixtures(fixture_scenario)
        plan_fixtures = write_ledger_plan_fixtures(fixture_scenario)

        if os.name == "posix":
            for directory in {
                fixture_scenario.book.local_path.parent,
                fixture_scenario.book_key.local_path.parent,
                fixture_scenario.attestation_founder_key.local_path.parent,
                fixture_scenario.backup_book.local_path.parent,
                fixture_scenario.backup_book_key.local_path.parent,
                fixture_scenario.restored_book.local_path.parent,
                fixture_scenario.restored_book_key.local_path.parent,
                fixture_scenario.replacement_book_key.local_path.parent,
                fixture_scenario.attestation_receipt.local_path.parent,
                fixture_scenario.trial_balance_pdf.local_path.parent,
            }:
                assert stat.S_IMODE(directory.stat().st_mode) == 0o700

        assert (
            fixture_scenario.attestation_founder_passphrase.local_path.read_text(encoding="utf-8")
            == "release-smoke-founder-passphrase\n"
        )

        assert_acceptance_request_fixtures(fixture_scenario, expected_source_document)
        assert_ledger_plan_fixtures(plan_fixtures, expected_source_document)

    with tempfile.TemporaryDirectory() as stale_directory:
        stale_root = pathlib.Path(stale_directory)
        marker = stale_root / "prior-run-marker"
        marker.write_text("preserve me\n", encoding="utf-8")
        stale_scenario = build_release_smoke_scenario(
            stale_root,
            absolute_mode,
            "fixture-stale-root",
        )
        try:
            prepare_fixture_directories(stale_scenario)
        except ReleaseSmokeFailure as exc:
            assert "fresh and empty" in str(exc)
        else:
            raise AssertionError("fixture preparation accepted a reused release-smoke work root")
        assert marker.read_text(encoding="utf-8") == "preserve me\n"
        assert not stale_scenario.request_sale.local_path.parent.exists()
