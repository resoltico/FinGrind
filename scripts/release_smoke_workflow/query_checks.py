from __future__ import annotations

from .assertions import assert_operator_queries_and_reports
from .cli import run_cli, run_cli_with_split_streams
from .models import ReleaseSmokeConfig
from .support import parse_json_output, payload_field, require, require_match


def verify_preflight_and_commit(config: ReleaseSmokeConfig, operation_ids: dict[str, str]) -> None:
    print(f"{config.label}: verifying preflight and commit")
    preflight_output = run_cli(
        config,
        operation_ids["preflightEntry"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--request-file",
        config.request_sale.argument,
        "--output",
        "json",
    )
    commit_sale_output = run_cli(
        config,
        operation_ids["postEntry"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--request-file",
        config.request_sale.argument,
        "--output",
        "json",
    )
    commit_expense_output = run_cli(
        config,
        operation_ids["postEntry"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--request-file",
        config.request_expense.argument,
        "--output",
        "json",
    )
    require_match(
        preflight_output,
        r'"status"[[:space:]]*:[[:space:]]*"ok"',
        f"{config.label} preflight did not report ok status",
    )
    require_match(
        commit_sale_output,
        r'"status"[[:space:]]*:[[:space:]]*"ok"',
        f"{config.label} sale commit did not report ok status",
    )
    require_match(
        commit_expense_output,
        r'"status"[[:space:]]*:[[:space:]]*"ok"',
        f"{config.label} expense commit did not report ok status",
    )


def verify_operator_queries_and_reports(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    print(f"{config.label}: verifying operator query and report surfaces")
    list_postings_first_page = parse_json_output(
        run_cli(
            config,
            operation_ids["listPostings"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--limit",
            "1",
            "--output",
            "json",
        ),
        f"{config.label} list-postings first page was not valid JSON",
    )
    next_cursor = payload_field(list_postings_first_page, "payload", "nextCursor")
    require(
        isinstance(next_cursor, str) and next_cursor,
        f"{config.label} list-postings did not report payload.nextCursor for the first page",
    )
    list_postings_second_page_output = run_cli(
        config,
        operation_ids["listPostings"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--cursor",
        next_cursor,
        "--limit",
        "25",
        "--output",
        "json",
    )
    list_postings_text_output = run_cli(
        config,
        operation_ids["listPostings"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--limit",
        "25",
        "--output",
        "text",
    )
    account_balance_text_output = run_cli(
        config,
        operation_ids["accountBalance"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--account-code",
        config.starter_cash_account_code,
        "--output",
        "text",
    )
    trial_balance_text_output = run_cli(
        config,
        operation_ids["trialBalance"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--effective-date-as-of",
        "2026-04-08",
        "--output",
        "text",
    )
    pdf_stdout, pdf_stderr = run_cli_with_split_streams(
        config,
        operation_ids["trialBalance"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--effective-date-as-of",
        "2026-04-08",
        "--output",
        "text",
        "--pdf-out",
        config.trial_balance_pdf.argument,
    )
    config.trial_balance_pdf_stderr_path.write_text(pdf_stderr, encoding="utf-8")
    account_ledger_csv_output = run_cli(
        config,
        operation_ids["accountLedger"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--account-code",
        config.starter_cash_account_code,
        "--effective-date-from",
        "2026-04-07",
        "--effective-date-to",
        "2026-04-08",
        "--output",
        "csv",
    )
    period_summary_text_output = run_cli(
        config,
        operation_ids["periodSummary"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--effective-date-from",
        "2026-04-07",
        "--effective-date-to",
        "2026-04-08",
        "--output",
        "text",
    )
    assert_operator_queries_and_reports(
        config,
        list_postings_second_page_output,
        list_postings_text_output,
        account_balance_text_output,
        trial_balance_text_output,
        pdf_stdout,
        pdf_stderr,
        account_ledger_csv_output,
        period_summary_text_output,
    )
