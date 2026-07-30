"""Pure validation and serialization policy for the MSVC environment capture."""

from __future__ import annotations

from collections.abc import Sequence

from msvc_setup_policy_models import EnvironmentEntry, MsvcSetupPolicyError

VSCMD_VER_NAME = "VSCMD_VER"
VSCMD_ENVIRONMENT_FAILURE = (
    "VsDevCmd.bat did not publish VSCMD_VER; refusing to continue with a partial environment"
)
GITHUB_ENV_DELIMITER_PREFIX = "__FINGRIND_ENV__"


def parse_vsdevcmd_environment_dump(lines: Sequence[str]) -> tuple[EnvironmentEntry, ...]:
    """Parse `set` output at its first equals sign with Windows-style key matching."""

    entries: list[EnvironmentEntry] = []
    positions: dict[str, int] = {}
    for index, line in enumerate(lines):
        if index == 0:
            line = line.removeprefix("\ufeff")
        separator_index = line.find("=")
        if separator_index <= 0:
            continue
        name = line[:separator_index]
        value = line[separator_index + 1 :]
        normalized_name = name.casefold()
        existing_index = positions.get(normalized_name)
        if existing_index is None:
            positions[normalized_name] = len(entries)
            entries.append(EnvironmentEntry(name=name, value=value))
        else:
            entries[existing_index] = EnvironmentEntry(
                name=entries[existing_index].name, value=value
            )
    return tuple(entries)


def validate_vsdevcmd_environment(entries: Sequence[EnvironmentEntry]) -> None:
    """Reject a developer-command result that did not establish its own version marker."""

    for entry in entries:
        if entry.name.casefold() == VSCMD_VER_NAME.casefold() and entry.value.strip():
            return
    raise MsvcSetupPolicyError(VSCMD_ENVIRONMENT_FAILURE)


def serialize_github_environment(entries: Sequence[EnvironmentEntry]) -> str:
    """Encode validated entries for GITHUB_ENV without delimiter-injection ambiguity."""

    normalized_entries = tuple(_normalize_github_environment_entry(entry) for entry in entries)
    delimiter = _choose_github_environment_delimiter(normalized_entries)
    lines: list[str] = []
    for entry in normalized_entries:
        lines.extend((f"{entry.name}<<{delimiter}", entry.value, delimiter))
    return "\n".join(lines) + ("\n" if lines else "")


def _normalize_github_environment_entry(entry: EnvironmentEntry) -> EnvironmentEntry:
    if not entry.name or any(
        character in entry.name for character in ("\x00", "\r", "\n", "=", "<<")
    ):
        raise MsvcSetupPolicyError(
            "VsDevCmd.bat produced a variable name that is unsafe for GITHUB_ENV"
        )
    if "\x00" in entry.value:
        raise MsvcSetupPolicyError(
            "VsDevCmd.bat produced a variable value that is unsafe for GITHUB_ENV"
        )
    return EnvironmentEntry(
        name=entry.name,
        value=entry.value.replace("\r\n", "\n").replace("\r", "\n"),
    )


def _choose_github_environment_delimiter(entries: Sequence[EnvironmentEntry]) -> str:
    occupied_lines = {value_line for entry in entries for value_line in entry.value.split("\n")}
    suffix = 0
    while True:
        delimiter = (
            GITHUB_ENV_DELIMITER_PREFIX
            if suffix == 0
            else f"{GITHUB_ENV_DELIMITER_PREFIX}_{suffix}"
        )
        if delimiter not in occupied_lines:
            return delimiter
        suffix += 1
