from __future__ import annotations

from .cli import run_cli, run_cli_allow_failure
from .models import ReleaseSmokeConfig
from .support import parse_json_output, require, require_match, require_no_match


def verify_rekey_and_wrong_key_semantics(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
) -> None:
    print(f"{config.label}: verifying rekey and wrong-key semantics")
    replacement_key_output = parse_json_output(
        run_cli(
            config,
            operation_ids["generateBookKeyFile"],
            "--book-key-file",
            config.replacement_book_key.argument,
            "--output",
            "json",
        ),
        f"{config.label} replacement generate-book-key-file output was not valid JSON",
    )
    require(
        replacement_key_output.get("status") == "ok",
        f"{config.label} replacement key generation did not report ok status",
    )
    rekey_output = run_cli(
        config,
        operation_ids["rekeyBook"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--replacement-book-key-file",
        config.replacement_book_key.argument,
        "--output",
        "json",
    )
    require_match(
        rekey_output,
        r'"status"[[:space:]]*:[[:space:]]*"ok"',
        f"{config.label} rekey-book did not report ok status",
    )
    wrong_key_output, wrong_key_status = run_cli_allow_failure(
        config,
        operation_ids["listAccounts"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--output",
        "json",
    )
    replacement_key_trial_balance_output = run_cli(
        config,
        operation_ids["trialBalance"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.replacement_book_key.argument,
        "--effective-date-as-of",
        "2026-04-08",
        "--output",
        "text",
    )
    require(
        wrong_key_status == error_exit_codes["protected-book-verification-failed"],
        f"{config.label} wrong-key listing exited with {wrong_key_status} instead of the published protected-book-verification-failed exit code",
    )
    require_match(
        wrong_key_output,
        r'"code"[[:space:]]*:[[:space:]]*"protected-book-verification-failed"',
        f"{config.label} wrong-key listing did not report protected-book-verification-failed",
    )
    require_no_match(
        wrong_key_output,
        r"SQLITE_NOTADB",
        f"{config.label} wrong-key listing leaked the SQLite NOTADB storage symptom",
    )
    require_match(
        replacement_key_trial_balance_output,
        r"^Trial Balance$",
        f"{config.label} rekeyed book did not render the trial-balance title",
    )
    require_match(
        replacement_key_trial_balance_output,
        r"6\.00",
        f"{config.label} trial-balance did not remain readable after rekey",
    )
