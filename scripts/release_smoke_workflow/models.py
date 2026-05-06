from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class SmokePath:
    local_path: Path
    argument: str


@dataclass(frozen=True)
class ReleaseSmokeConfig:
    label: str
    repo_root: Path
    command_prefix: list[str]
    command_bridge_prefix: list[str]
    command_cwd: Path | None
    reported_work_root: Path | None
    command_env_drop: list[str]
    command_env_set: dict[str, str]
    runtime_distribution_key: str
    expect_loaded_sqlite_details: bool
    expect_bundle_home_property: bool
    book_key_output_permissions: str
    request_sale: SmokePath
    request_adjustment: SmokePath
    invalid_request: SmokePath
    declare_cash: SmokePath
    declare_revenue: SmokePath
    book: SmokePath
    book_key: SmokePath
    replacement_book_key: SmokePath
    prompt_failure_book: SmokePath
    trial_balance_pdf: SmokePath
    trial_balance_pdf_stderr_path: Path
    second_page_command_id: str
    actor_prefix: str
    open_book_mode: str


@dataclass(frozen=True)
class ReleaseSmokeScenario:
    request_sale: SmokePath
    request_adjustment: SmokePath
    invalid_request: SmokePath
    declare_cash: SmokePath
    declare_revenue: SmokePath
    book: SmokePath
    book_key: SmokePath
    replacement_book_key: SmokePath
    prompt_failure_book: SmokePath
    trial_balance_pdf: SmokePath
    trial_balance_pdf_stderr_path: Path
    second_page_command_id: str
    actor_prefix: str


class ReleaseSmokeFailure(RuntimeError):
    """Raised when the release acceptance workflow finds a contract violation."""
