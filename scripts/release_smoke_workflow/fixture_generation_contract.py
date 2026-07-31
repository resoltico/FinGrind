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
    """Keep private Windows fixture parents on the explicit pinned runtime boundary."""
    assert_windows_owner_only_directory_script_contract()
    directory = pathlib.Path("C:/release-smoke/private")
    power_shell = pathlib.Path("/tools/pwsh.exe")
    security_script = pathlib.Path(fixtures.__file__).resolve().parent.parent / (
        "secure-windows-owner-only-directory.ps1"
    )
    with (
        patch.dict(
            os.environ,
            {"FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE": str(power_shell)},
            clear=True,
        ),
        patch("pathlib.Path.is_file", return_value=True),
        patch(
            "release_smoke_workflow.fixtures.subprocess.run",
            return_value=subprocess.CompletedProcess(["pwsh.exe"], 0, "", ""),
        ) as run,
    ):
        fixtures.secure_windows_directory(directory)

    assert run.call_args.args[0] == [
        str(power_shell),
        "-NoLogo",
        "-NoProfile",
        "-NonInteractive",
        "-ExecutionPolicy",
        "RemoteSigned",
        "-File",
        str(security_script),
        str(directory),
    ]
    assert run.call_args.kwargs == {
        "check": False,
        "capture_output": True,
        "text": True,
        "encoding": "utf-8",
        "errors": "strict",
    }

    assert_windows_directory_failure(
        directory,
        subprocess.CompletedProcess(["pwsh.exe"], 1, "", "ACL denied"),
        "apply the owner-only Windows directory security descriptor",
        "ACL denied",
    )
    with patch.dict(os.environ, {}, clear=True), patch("pathlib.Path.is_file", return_value=True):
        require_release_smoke_failure(
            lambda: fixtures.secure_windows_directory(directory),
            "FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE is not set",
        )
    with (
        patch.dict(
            os.environ,
            {"FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE": "relative-pwsh.exe"},
            clear=True,
        ),
        patch("pathlib.Path.is_file", return_value=True),
    ):
        require_release_smoke_failure(
            lambda: fixtures.secure_windows_directory(directory),
            "must name one absolute executable file",
        )
    with (
        patch.dict(
            os.environ,
            {"FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE": str(power_shell)},
            clear=True,
        ),
        patch("pathlib.Path.is_file", side_effect=[True, False]),
    ):
        require_release_smoke_failure(
            lambda: fixtures.secure_windows_directory(directory),
            "missing Windows owner-only directory script",
        )
    with (
        patch.dict(
            os.environ,
            {"FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE": str(power_shell)},
            clear=True,
        ),
        patch("pathlib.Path.is_file", return_value=True),
        patch(
            "release_smoke_workflow.fixtures.subprocess.run",
            side_effect=OSError("whoami is unavailable"),
        ),
    ):
        require_release_smoke_failure(
            lambda: fixtures.secure_windows_directory(directory),
            "whoami is unavailable",
        )


def assert_windows_owner_only_directory_script_contract() -> None:
    """Require an exact Windows DACL, not an ambiguous ACL command approximation."""
    script = pathlib.Path(fixtures.__file__).resolve().parent.parent / (
        "secure-windows-owner-only-directory.ps1"
    )
    text = script.read_text(encoding="utf-8")
    for required_fragment in (
        "[System.Security.Principal.WindowsIdentity]::GetCurrent().User",
        "$directorySecurity.SetAccessRuleProtection($true, $false)",
        "$directorySecurity.SetOwner($ownerSid)",
        "$directorySecurity.SetAccessRule($ownerOnlyRule)",
        "[System.IO.FileSystemAclExtensions]::SetAccessControl",
        "[System.IO.FileSystemAclExtensions]::GetAccessControl",
        "$appliedRules.Count -ne 1",
    ):
        assert required_fragment in text
    for forbidden_fragment in ("Get-Acl", "Set-Acl", "icacls", "whoami"):
        assert forbidden_fragment not in text


def assert_windows_directory_failure(
    directory: pathlib.Path,
    command_result: subprocess.CompletedProcess[str],
    expected_action: str,
    expected_detail: str,
) -> None:
    with (
        patch.dict(
            os.environ,
            {"FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE": "/tools/pwsh.exe"},
            clear=True,
        ),
        patch("pathlib.Path.is_file", return_value=True),
        patch(
            "release_smoke_workflow.fixtures.subprocess.run",
            return_value=command_result,
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
