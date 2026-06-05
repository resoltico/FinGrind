from __future__ import annotations

from .cli import run_cli_allow_failure
from .models import ReleaseSmokeConfig
from .support import require, require_match, require_no_match


def verify_deterministic_nonsense_workflows(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
) -> None:
    print(f"{config.label}: verifying deterministic nonsense workflows")
    invalid_cursor_output, invalid_cursor_status = run_cli_allow_failure(
        config,
        operation_ids["listPostings"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.replacement_book_key.argument,
        "--cursor",
        "definitely-not-a-valid-cursor",
        "--output",
        "json",
    )
    machine_prompt_failure_output, machine_prompt_failure_status = run_cli_allow_failure(
        config,
        operation_ids["openBook"],
        "--book-file",
        config.prompt_failure_book.argument,
        "--entity-name",
        config.entity_name,
        "--functional-currency",
        config.functional_currency,
        "--fiscal-year-start",
        config.fiscal_year_start,
        "--book-passphrase-prompt",
        "--output",
        "json",
    )
    terminal_prompt_failure_output, terminal_prompt_failure_status = run_cli_allow_failure(
        config,
        operation_ids["openBook"],
        "--book-file",
        config.prompt_failure_book.argument,
        "--entity-name",
        config.entity_name,
        "--functional-currency",
        config.functional_currency,
        "--fiscal-year-start",
        config.fiscal_year_start,
        "--book-passphrase-prompt",
        "--output",
        "text",
    )
    invalid_request_output, invalid_request_status = run_cli_allow_failure(
        config,
        operation_ids["declareAccount"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.replacement_book_key.argument,
        "--request-file",
        config.invalid_request.argument,
        "--output",
        "json",
    )
    require(
        invalid_cursor_status == error_exit_codes["invalid-page-cursor"],
        f"{config.label} invalid cursor exited with {invalid_cursor_status} instead of the published invalid-page-cursor exit code",
    )
    require_match(
        invalid_cursor_output,
        r'"code"[[:space:]]*:[[:space:]]*"invalid-page-cursor"',
        f"{config.label} invalid cursor did not report invalid-page-cursor",
    )
    require_no_match(
        invalid_cursor_output,
        r'"code"[[:space:]]*:[[:space:]]*"runtime-failure"',
        f"{config.label} invalid cursor regressed to runtime-failure",
    )
    require(
        machine_prompt_failure_status == error_exit_codes["unsupported-output-selection"],
        f"{config.label} machine-output prompt request exited with {machine_prompt_failure_status} instead of the published unsupported-output-selection exit code",
    )
    require_match(
        machine_prompt_failure_output,
        r'"code"[[:space:]]*:[[:space:]]*"unsupported-output-selection"',
        f"{config.label} machine-output prompt request did not report unsupported-output-selection",
    )
    require_match(
        machine_prompt_failure_output,
        r"--output text",
        f"{config.label} machine-output prompt request did not report the text-output repair hint",
    )
    require(
        terminal_prompt_failure_status == error_exit_codes["interactive-prompt-unavailable"],
        f"{config.label} prompt-unavailable exited with {terminal_prompt_failure_status} instead of the published interactive-prompt-unavailable exit code",
    )
    require_match(
        terminal_prompt_failure_output,
        r"interactive-prompt-unavailable",
        f"{config.label} prompt-unavailable did not report interactive-prompt-unavailable",
    )
    require_match(
        terminal_prompt_failure_output,
        r"--book-passphrase-stdin",
        f"{config.label} prompt-unavailable did not report a repair hint",
    )
    require(
        invalid_request_status == error_exit_codes["invalid-request"],
        f"{config.label} invalid request exited with {invalid_request_status} instead of the published invalid-request exit code",
    )
    require_match(
        invalid_request_output,
        r'"code"[[:space:]]*:[[:space:]]*"invalid-request"',
        f"{config.label} invalid request did not report invalid-request",
    )
    require_match(
        invalid_request_output,
        r"Unexpected fields: nonsenseOne, nonsenseTwo",
        f"{config.label} invalid request did not report all unexpected fields together",
    )
