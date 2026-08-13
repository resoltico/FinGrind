"""Regression checks for Windows release-smoke owner-only directory preparation."""

from __future__ import annotations

import os
import pathlib
import subprocess
import tempfile
from unittest.mock import patch

from . import fixtures
from .models import ReleaseSmokeFailure
from .scenario import build_release_smoke_scenario
from .scenario_paths import ARGUMENT_PATH_MODE_ABSOLUTE


def assert_windows_owner_only_directory_contract() -> None:
    """Keep private Windows fixture parents on the explicit pinned runtime boundary."""
    assert_windows_owner_only_directory_script_contract()
    assert_private_fixture_ancestry_contract()
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
            side_effect=OSError("pwsh is unavailable"),
        ),
    ):
        require_release_smoke_failure(
            lambda: fixtures.secure_windows_directory(directory),
            "pwsh is unavailable",
        )


def assert_private_fixture_ancestry_contract() -> None:
    """Every nested private artifact parent is hardened from the trusted work root downward."""
    with tempfile.TemporaryDirectory() as temporary_directory:
        work_root = pathlib.Path(temporary_directory) / "workspace"
        work_root.mkdir()
        scenario = build_release_smoke_scenario(
            work_root,
            ARGUMENT_PATH_MODE_ABSOLUTE,
            "owner-only-ancestry",
        )
        private_parents = {
            scenario.book.local_path.parent,
            scenario.book_key.local_path.parent,
            scenario.attestation_founder_key.local_path.parent,
            scenario.backup_book.local_path.parent,
            scenario.backup_book_key.local_path.parent,
            scenario.restored_book.local_path.parent,
            scenario.restored_book_key.local_path.parent,
            scenario.replacement_book_key.local_path.parent,
            scenario.attestation_receipt.local_path.parent,
            scenario.trial_balance_pdf.local_path.parent,
        }
        with patch("release_smoke_workflow.fixtures.prepare_owner_only_directory") as prepare:
            fixtures.prepare_fixture_directories(scenario)

    secured_directories = tuple(call.args[0] for call in prepare.call_args_list)
    assert secured_directories == tuple(
        sorted(secured_directories, key=lambda path: (len(path.parts), str(path)))
    )
    assert work_root not in secured_directories
    for private_parent in private_parents:
        current_directory = work_root
        for segment in private_parent.relative_to(work_root).parts:
            current_directory = current_directory / segment
            assert current_directory in secured_directories


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
