"""Regression checks for Windows release-smoke owner-only file preparation."""

from __future__ import annotations

import os
import pathlib
import subprocess
from unittest.mock import patch

from . import fixtures
from .models import ReleaseSmokeFailure


def assert_windows_owner_only_file_contract() -> None:
    """Keep copied secret artifacts on the exact owner-only Windows DACL contract."""
    file_path = pathlib.Path("C:/release-smoke/private/book.key")
    power_shell = pathlib.Path("/tools/pwsh.exe")
    security_script = pathlib.Path(fixtures.__file__).resolve().parent.parent / (
        "secure-windows-owner-only-file.ps1"
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
        fixtures.secure_windows_file(file_path)

    assert run.call_args.args[0] == [
        str(power_shell),
        "-NoLogo",
        "-NoProfile",
        "-NonInteractive",
        "-ExecutionPolicy",
        "RemoteSigned",
        "-File",
        str(security_script),
        str(file_path),
    ]
    assert run.call_args.kwargs == {
        "check": False,
        "capture_output": True,
        "text": True,
        "encoding": "utf-8",
        "errors": "strict",
    }

    _assert_windows_file_failure(
        file_path,
        subprocess.CompletedProcess(["pwsh.exe"], 1, "", "ACL denied"),
        "apply the owner-only Windows file security descriptor",
        "ACL denied",
    )
    with patch.dict(os.environ, {}, clear=True), patch("pathlib.Path.is_file", return_value=True):
        _require_release_smoke_failure(
            lambda: fixtures.secure_windows_file(file_path),
            "FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE is not set",
        )
    with (
        patch.dict(
            os.environ,
            {"FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE": str(power_shell)},
            clear=True,
        ),
        patch("pathlib.Path.is_file", side_effect=[True, False]),
    ):
        _require_release_smoke_failure(
            lambda: fixtures.secure_windows_file(file_path),
            "missing Windows owner-only file script",
        )
    _assert_windows_owner_only_file_script_contract(security_script)
    _assert_boundary_key_copy_is_hardened()


def _assert_windows_owner_only_file_script_contract(security_script: pathlib.Path) -> None:
    text = security_script.read_text(encoding="utf-8")
    for required_fragment in (
        "[System.Security.Principal.WindowsIdentity]::GetCurrent().User",
        "$fileSecurity.SetAccessRuleProtection($true, $false)",
        "$fileSecurity.SetOwner($ownerSid)",
        "$fileSecurity.SetAccessRule($ownerOnlyRule)",
        "[System.IO.FileSystemAclExtensions]::SetAccessControl",
        "[System.IO.FileSystemAclExtensions]::GetAccessControl",
        "$appliedRules.Count -ne 1",
    ):
        assert required_fragment in text
    for forbidden_fragment in ("Get-Acl", "Set-Acl", "icacls", "whoami"):
        assert forbidden_fragment not in text


def _assert_boundary_key_copy_is_hardened() -> None:
    artifact_source = (
        pathlib.Path(fixtures.__file__).parent / "field_matrix" / "format_boundary_artifacts.py"
    ).read_text(encoding="utf-8")
    assert "prepare_owner_only_file(boundary_key.local_path)" in artifact_source


def _assert_windows_file_failure(
    file_path: pathlib.Path,
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
        _require_release_smoke_failure(
            lambda: fixtures.secure_windows_file(file_path),
            expected_action,
            expected_detail,
        )


def _require_release_smoke_failure(action, *expected_fragments: str) -> None:
    try:
        action()
    except ReleaseSmokeFailure as exc:
        for expected_fragment in expected_fragments:
            assert expected_fragment in str(exc)
    else:
        raise AssertionError("expected release-smoke fixture security rejection")
