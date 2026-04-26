from __future__ import annotations

import json
import os
from pathlib import Path

from .models import ReleaseSmokeConfig, ReleaseSmokeFailure
from .scenario import build_release_smoke_scenario
from .support import require


def load_config() -> ReleaseSmokeConfig:
    command_prefix = require_json_array("FINGRIND_RELEASE_SMOKE_COMMAND_PREFIX_JSON")
    require(
        len(command_prefix) > 0,
        "environment variable FINGRIND_RELEASE_SMOKE_COMMAND_PREFIX_JSON must not be empty",
    )
    work_root = Path(require_env("FINGRIND_RELEASE_SMOKE_WORK_ROOT"))
    argument_path_mode = require_env("FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE")
    scenario_id = require_env("FINGRIND_RELEASE_SMOKE_SCENARIO_ID")
    scenario = build_release_smoke_scenario(work_root, argument_path_mode, scenario_id)
    return ReleaseSmokeConfig(
        label=require_env("FINGRIND_RELEASE_SMOKE_LABEL"),
        repo_root=Path(require_env("FINGRIND_RELEASE_SMOKE_REPO_ROOT")),
        command_prefix=command_prefix,
        command_cwd=optional_path("FINGRIND_RELEASE_SMOKE_COMMAND_CWD"),
        command_env_drop=require_json_array(
            "FINGRIND_RELEASE_SMOKE_COMMAND_ENV_DROP_JSON", default=[]
        ),
        command_env_set=require_json_object(
            "FINGRIND_RELEASE_SMOKE_COMMAND_ENV_SET_JSON", default={}
        ),
        runtime_distribution_key=require_env("FINGRIND_RELEASE_SMOKE_RUNTIME_DISTRIBUTION_KEY"),
        expect_loaded_sqlite_details=require_bool(
            "FINGRIND_RELEASE_SMOKE_EXPECT_LOADED_SQLITE_DETAILS"
        ),
        expect_bundle_home_property=require_bool(
            "FINGRIND_RELEASE_SMOKE_EXPECT_BUNDLE_HOME_PROPERTY"
        ),
        book_key_output_permissions=require_env(
            "FINGRIND_RELEASE_SMOKE_BOOK_KEY_OUTPUT_PERMISSIONS"
        ),
        request_sale=scenario.request_sale,
        request_adjustment=scenario.request_adjustment,
        invalid_request=scenario.invalid_request,
        declare_cash=scenario.declare_cash,
        declare_revenue=scenario.declare_revenue,
        book=scenario.book,
        book_key=scenario.book_key,
        replacement_book_key=scenario.replacement_book_key,
        prompt_failure_book=scenario.prompt_failure_book,
        trial_balance_pdf=scenario.trial_balance_pdf,
        trial_balance_pdf_stderr_path=scenario.trial_balance_pdf_stderr_path,
        second_page_command_id=scenario.second_page_command_id,
        actor_prefix=scenario.actor_prefix,
        open_book_mode=require_env("FINGRIND_RELEASE_SMOKE_OPEN_BOOK_MODE"),
    )


def optional_path(name: str) -> Path | None:
    value = os.environ.get(name, "").strip()
    return Path(value) if value else None


def require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ReleaseSmokeFailure(f"missing required environment variable: {name}")
    return value


def require_bool(name: str) -> bool:
    value = require_env(name)
    if value == "true":
        return True
    if value == "false":
        return False
    raise ReleaseSmokeFailure(
        f"environment variable {name} must be one of: true, false"
    )


def require_json_array(name: str, default: list[str] | None = None) -> list[str]:
    raw = os.environ.get(name, "").strip()
    if not raw:
        return list(default or [])
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ReleaseSmokeFailure(
            f"environment variable {name} must contain one JSON array"
        ) from exc
    if not isinstance(parsed, list) or any(not isinstance(item, str) or not item for item in parsed):
        raise ReleaseSmokeFailure(
            f"environment variable {name} must contain one JSON array of non-blank strings"
        )
    return parsed


def require_json_object(name: str, default: dict[str, str] | None = None) -> dict[str, str]:
    raw = os.environ.get(name, "").strip()
    if not raw:
        return dict(default or {})
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ReleaseSmokeFailure(
            f"environment variable {name} must contain one JSON object"
        ) from exc
    if not isinstance(parsed, dict):
        raise ReleaseSmokeFailure(
            f"environment variable {name} must contain one JSON object"
        )
    normalized: dict[str, str] = {}
    for key, value in parsed.items():
        if not isinstance(key, str) or not key or not isinstance(value, str):
            raise ReleaseSmokeFailure(
                f"environment variable {name} must contain one JSON object of string pairs"
            )
        normalized[key] = value
    return normalized
