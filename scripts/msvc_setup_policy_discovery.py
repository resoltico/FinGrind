"""Pure Visual Studio discovery and developer-command planning policy."""

from __future__ import annotations

from collections.abc import Sequence
from pathlib import PureWindowsPath

from msvc_setup_policy_models import MsvcSetupPolicyError

VSWHERE_ARGUMENTS = (
    "-latest",
    "-products",
    "*",
    "-requires",
    "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
    "-property",
    "installationPath",
    "-utf8",
)
VSDEV_CMD_RELATIVE_PATH = ("Common7", "Tools", "VsDevCmd.bat")
VISUAL_STUDIO_2022_EDITIONS = ("Enterprise", "Professional", "Community", "BuildTools")
VSWHERE_INVOCATION_FAILURE = (
    "vswhere failed while locating a Visual Studio installation with MSVC tools"
)
VSWHERE_INVOCATION_SHAPE_FAILURE = "vswhere invocation did not return an exit code and output"
VSDEV_CMD_NOT_FOUND_FAILURE = (
    "unable to locate VsDevCmd.bat via vswhere or standard Visual Studio 2022 installation paths"
)
_CMD_PATH_FORBIDDEN_CHARACTERS = frozenset('"%!^&|<>()\x00\r\n')


def vswhere_arguments() -> tuple[str, ...]:
    """Return the one canonical query vector for a VS installation with x64 MSVC tools."""

    return VSWHERE_ARGUMENTS


def select_vswhere_installation(exit_code: object, output: object) -> str | None:
    """Return the first substantive vswhere output line or raise its stable failure."""

    if (
        not isinstance(exit_code, int)
        or isinstance(exit_code, bool)
        or isinstance(output, (bytes, str))
        or not isinstance(output, Sequence)
        or any(not isinstance(line, str) for line in output)
    ):
        raise MsvcSetupPolicyError(VSWHERE_INVOCATION_SHAPE_FAILURE)
    if exit_code != 0:
        raise MsvcSetupPolicyError(VSWHERE_INVOCATION_FAILURE)
    for line in output:
        candidate = line.removeprefix("\ufeff")
        if candidate and not candidate.isspace():
            return candidate
    return None


def plan_vsdevcmd_candidates(
    installation_path: str | None,
    program_files: str | None,
) -> tuple[str, ...]:
    """Plan ordered VsDevCmd candidates; the adapter alone checks filesystem state."""

    candidates: list[str] = []
    if installation_path is not None and not installation_path.isspace() and installation_path:
        candidates.append(_join_windows_path(installation_path, *VSDEV_CMD_RELATIVE_PATH))
    if program_files is not None and not program_files.isspace() and program_files:
        for edition in VISUAL_STUDIO_2022_EDITIONS:
            candidates.append(
                _join_windows_path(
                    program_files,
                    "Microsoft Visual Studio",
                    "2022",
                    edition,
                    *VSDEV_CMD_RELATIVE_PATH,
                )
            )
    return tuple(candidates)


def render_vsdevcmd_command_line(vsdevcmd_path: str, arch: str, host_arch: str) -> str:
    """Render the exact, injection-safe command passed to cmd.exe."""

    if not isinstance(vsdevcmd_path, str) or not vsdevcmd_path:
        raise MsvcSetupPolicyError("VsDevCmd.bat path must be non-empty")
    if any(character in _CMD_PATH_FORBIDDEN_CHARACTERS for character in vsdevcmd_path):
        raise MsvcSetupPolicyError(
            "VsDevCmd.bat path must not contain cmd expansion or control syntax"
        )
    checked_arch = validate_architecture_token(arch, "Arch")
    checked_host_arch = validate_architecture_token(host_arch, "HostArch")
    return f'call "{vsdevcmd_path}" -arch={checked_arch} -host_arch={checked_host_arch} >nul && set'


def validate_architecture_token(value: str, parameter_name: str) -> str:
    """Require one cmd-safe architecture token without relying on shell escaping."""

    if not value or value.isspace() or not _is_architecture_token(value):
        raise MsvcSetupPolicyError(
            f"{parameter_name} must be one non-empty MSVC architecture token without command syntax"
        )
    return value


def _join_windows_path(root: str, *parts: str) -> str:
    return str(PureWindowsPath(root, *parts))


def _is_architecture_token(value: str) -> bool:
    first_character = value[0]
    if not first_character.isascii() or not first_character.isalnum():
        return False
    return all(
        character.isascii() and (character.isalnum() or character in "._-")
        for character in value[1:]
    )
