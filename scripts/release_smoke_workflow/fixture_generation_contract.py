"""Regression checks for release-smoke fixture generation."""

from __future__ import annotations

import os
import pathlib
import stat
import subprocess
import tempfile
from unittest.mock import patch

from . import fixtures
from .fixture_plan_contract import assert_ledger_plan_fixtures
from .fixture_request_contract import assert_acceptance_request_fixtures
from .models import ReleaseSmokeFailure


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


def assert_windows_owner_only_directory_contract() -> None:
    """Keep private Windows fixture parents independent of PowerShell modules."""
    directory = pathlib.Path("C:/release-smoke/private")
    owner_sid = "S-1-5-21-101-202-303-1001"
    system_root = r"C:\Windows"
    system_directory = pathlib.Path(system_root) / "System32"
    with (
        patch.dict(os.environ, {"SystemRoot": system_root}, clear=True),
        patch(
            "release_smoke_workflow.fixtures.subprocess.run",
            side_effect=[
                subprocess.CompletedProcess(["whoami.exe"], 0, f'"runner","{owner_sid}"\n', ""),
                subprocess.CompletedProcess(["icacls.exe"], 0, "", ""),
            ],
        ) as run,
    ):
        fixtures.secure_windows_directory(directory)

    assert run.call_args_list[0].args[0] == [
        str(system_directory / "whoami.exe"),
        "/user",
        "/fo",
        "csv",
        "/nh",
    ]
    assert run.call_args_list[1].args[0] == [
        str(system_directory / "icacls.exe"),
        str(directory),
        "/inheritance:r",
        "/grant:r",
        f"*{owner_sid}:(OI)(CI)F",
        "/c",
    ]
    for call in run.call_args_list:
        assert call.kwargs == {"check": False, "capture_output": True, "text": True}

    assert_windows_directory_failure(
        directory,
        [subprocess.CompletedProcess(["whoami.exe"], 1, "", "identity denied")],
        "resolve the current Windows owner",
        "identity denied",
    )
    assert_windows_directory_failure(
        directory,
        [
            subprocess.CompletedProcess(["whoami.exe"], 0, f'"runner","{owner_sid}"\n', ""),
            subprocess.CompletedProcess(["icacls.exe"], 1, "", "ACL denied"),
        ],
        "grant the current Windows owner full control",
        "ACL denied",
    )
    assert_windows_directory_failure(
        directory,
        [subprocess.CompletedProcess(["whoami.exe"], 0, '"runner","not-a-sid"\n', "")],
        "could not resolve the current Windows owner",
        "invalid SID",
    )
    with patch.dict(os.environ, {}, clear=True):
        require_release_smoke_failure(
            lambda: fixtures.secure_windows_directory(directory),
            "Windows SystemRoot is not set",
        )
    with (
        patch.dict(os.environ, {"SystemRoot": system_root}, clear=True),
        patch(
            "release_smoke_workflow.fixtures.subprocess.run",
            side_effect=OSError("whoami is unavailable"),
        ),
    ):
        require_release_smoke_failure(
            lambda: fixtures.secure_windows_directory(directory),
            "whoami is unavailable",
        )


def assert_windows_directory_failure(
    directory: pathlib.Path,
    command_results: list[subprocess.CompletedProcess[str]],
    expected_action: str,
    expected_detail: str,
) -> None:
    with (
        patch.dict(os.environ, {"SystemRoot": r"C:\Windows"}, clear=True),
        patch(
            "release_smoke_workflow.fixtures.subprocess.run",
            side_effect=command_results,
        ),
    ):
        require_release_smoke_failure(
            lambda: fixtures.secure_windows_directory(directory),
            expected_action,
            expected_detail,
        )


def require_release_smoke_failure(action, *expected_fragments: str) -> None:
    try:
        action()
    except ReleaseSmokeFailure as exc:
        for expected_fragment in expected_fragments:
            assert expected_fragment in str(exc)
    else:
        raise AssertionError("expected release-smoke fixture security rejection")
