from __future__ import annotations

from pathlib import Path

from .models import ReleaseSmokeFailure, ReleaseSmokeScenario, SmokePath

ARGUMENT_PATH_MODE_ABSOLUTE = "absolute"
ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE = "relative-to-work-root"
UNICODE_WORKSPACE_SEGMENT = "Rīga büro"


def build_release_smoke_scenario(
    work_root: Path,
    argument_path_mode: str,
    scenario_id: str,
) -> ReleaseSmokeScenario:
    normalized_scenario_id = require_scenario_id(scenario_id)
    normalized_path_mode = require_argument_path_mode(argument_path_mode)

    return ReleaseSmokeScenario(
        request_sale=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"--sale [{normalized_scenario_id}].json",
        ),
        request_adjustment=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"adjustment [{normalized_scenario_id}].json",
        ),
        invalid_request=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"bad fields [{normalized_scenario_id}].json",
        ),
        declare_cash=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"declare account cash [{normalized_scenario_id}].json",
        ),
        declare_revenue=smoke_path(
            work_root,
            normalized_path_mode,
            Path("requests odd") / f"declare account revenue [{normalized_scenario_id}].json",
        ),
        book=smoke_path(
            work_root,
            normalized_path_mode,
            Path("books odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"-entity [{normalized_scenario_id}].sqlite",
        ),
        book_key=smoke_path(
            work_root,
            normalized_path_mode,
            Path("keys odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"--entity [{normalized_scenario_id}].key",
        ),
        replacement_book_key=smoke_path(
            work_root,
            normalized_path_mode,
            Path("keys odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"--entity [{normalized_scenario_id}]-replacement.key",
        ),
        prompt_failure_book=smoke_path(
            work_root,
            normalized_path_mode,
            Path("books odd")
            / UNICODE_WORKSPACE_SEGMENT
            / "nested"
            / f"prompt unavailable [{normalized_scenario_id}].sqlite",
        ),
        trial_balance_pdf=smoke_path(
            work_root,
            normalized_path_mode,
            Path("reports odd") / f"trial balance [{normalized_scenario_id}].pdf",
        ),
        trial_balance_pdf_stderr_path=(
            work_root / "reports odd" / f"trial balance [{normalized_scenario_id}].stderr.txt"
        ),
        second_page_command_id=normalized_scenario_id + "-sale",
        actor_prefix=normalized_scenario_id,
    )


def smoke_path(work_root: Path, argument_path_mode: str, relative_path: Path) -> SmokePath:
    local_path = work_root / relative_path
    if argument_path_mode == ARGUMENT_PATH_MODE_ABSOLUTE:
        return SmokePath(
            relative_path=relative_path, local_path=local_path, argument=str(local_path)
        )
    if argument_path_mode == ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE:
        return SmokePath(
            relative_path=relative_path,
            local_path=local_path,
            argument=relative_path.as_posix(),
        )
    raise ReleaseSmokeFailure("unsupported release-smoke argument path mode: " + argument_path_mode)


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
