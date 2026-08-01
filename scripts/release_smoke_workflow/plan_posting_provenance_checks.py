from __future__ import annotations

from .account_ledger_assertions import ACCOUNT_LEDGER_CSV_HEADER
from .attestation_head_checks import AttestationCommit
from .cli import run_cli
from .csv_support import parse_csv_rows
from .models import ReleaseSmokeConfig
from .support import parse_json_output, require, required_list, required_mapping


def assert_posting_plan_provenance(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    posting_id: str,
    plan_commit: AttestationCommit,
) -> None:
    expected_commit = {
        "operationOrder": plan_commit.operation_order,
        "operationHead": plan_commit.operation_head,
    }
    get_posting = parse_json_output(
        run_cli(
            config,
            operation_ids["getPosting"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--posting-id",
            posting_id,
            "--output",
            "json",
        ),
        f"{config.label} aggregate posting plan get-posting output was not valid JSON",
    )
    posting = required_mapping(required_mapping(get_posting, "payload"), "posting")
    require(
        posting.get("postingId") == posting_id
        and posting.get("postingOriginKind") == "DIRECT_JOURNAL"
        and posting.get("attestationCommit") == expected_commit,
        f"{config.label} get-posting did not link the aggregate-plan posting to its exact commit",
    )

    list_postings = parse_json_output(
        run_cli(
            config,
            operation_ids["listPostings"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--account-code",
            config.starter_cash_account_code,
            "--limit",
            "50",
            "--output",
            "json",
        ),
        f"{config.label} aggregate posting plan list-postings output was not valid JSON",
    )
    postings = required_list(required_mapping(list_postings, "payload"), "postings")
    matching_postings = [
        posting
        for posting in postings
        if isinstance(posting, dict) and posting.get("postingId") == posting_id
    ]
    require(
        len(matching_postings) == 1
        and matching_postings[0].get("postingOriginKind") == "DIRECT_JOURNAL"
        and matching_postings[0].get("attestationCommit") == expected_commit,
        f"{config.label} list-postings did not link the aggregate-plan posting to its exact commit",
    )
    _assert_posting_plan_account_ledger_provenance(
        config, operation_ids, posting_id, expected_commit
    )


def _assert_posting_plan_account_ledger_provenance(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    posting_id: str,
    expected_commit: dict[str, str],
) -> None:
    query_arguments = (
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--account-code",
        config.starter_cash_account_code,
        "--effective-date-from",
        "2026-04-09",
        "--effective-date-to",
        "2026-04-09",
    )
    ledger_json = parse_json_output(
        run_cli(
            config,
            operation_ids["accountLedger"],
            *query_arguments,
            "--output",
            "json",
        ),
        f"{config.label} aggregate posting plan account-ledger JSON was not valid JSON",
    )
    ledger_rows = required_list(required_mapping(ledger_json, "payload"), "rows")
    matching_ledger_rows = [
        row for row in ledger_rows if isinstance(row, dict) and row.get("postingId") == posting_id
    ]
    require(
        len(matching_ledger_rows) == 1
        and matching_ledger_rows[0].get("attestationCommit") == expected_commit,
        f"{config.label} account-ledger JSON did not link the aggregate-plan posting to its exact commit",
    )

    ledger_csv = run_cli(
        config,
        operation_ids["accountLedger"],
        *query_arguments,
        "--output",
        "csv",
    )
    header, rows = parse_csv_rows(
        ledger_csv,
        f"{config.label} aggregate posting plan account-ledger CSV",
    )
    matching_csv_rows = [row for row in rows if row.get("postingId") == posting_id]
    require(
        header == ACCOUNT_LEDGER_CSV_HEADER
        and len(matching_csv_rows) == 1
        and matching_csv_rows[0].get("attestationOperationOrder")
        == expected_commit["operationOrder"]
        and matching_csv_rows[0].get("attestationOperationHead")
        == expected_commit["operationHead"],
        f"{config.label} account-ledger CSV did not link the aggregate-plan posting to its exact commit",
    )
