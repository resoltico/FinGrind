from __future__ import annotations

from .models import ReleaseSmokeFailure
from .scenario_paths import ARGUMENT_PATH_MODE_ABSOLUTE, ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE


def require_scenario_id(scenario_id: str) -> str:
    normalized = scenario_id.strip()
    if not normalized:
        raise ReleaseSmokeFailure(
            "environment variable FINGRIND_RELEASE_SMOKE_SCENARIO_ID must be one non-blank slug"
        )
    if normalized != normalized.lower():
        raise ReleaseSmokeFailure(
            "environment variable FINGRIND_RELEASE_SMOKE_SCENARIO_ID must be lowercase"
        )
    allowed = set("abcdefghijklmnopqrstuvwxyz0123456789-")
    if any(character not in allowed for character in normalized):
        raise ReleaseSmokeFailure(
            "environment variable FINGRIND_RELEASE_SMOKE_SCENARIO_ID must contain only lowercase letters, digits, and hyphens"
        )
    if normalized.startswith("-") or normalized.endswith("-") or "--" in normalized:
        raise ReleaseSmokeFailure(
            "environment variable FINGRIND_RELEASE_SMOKE_SCENARIO_ID must use single internal hyphens only"
        )
    return normalized


def require_argument_path_mode(argument_path_mode: str) -> str:
    normalized = argument_path_mode.strip()
    if normalized in (
        ARGUMENT_PATH_MODE_ABSOLUTE,
        ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE,
    ):
        return normalized
    raise ReleaseSmokeFailure(
        "environment variable FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE must be one of: absolute, relative-to-work-root"
    )
