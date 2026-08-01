"""Posting-query provenance evidence for the attestation scale scenario."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_head_checks import AttestationCommit
from ..cli import run_cli
from ..csv_support import parse_csv_rows
from ..models import ReleaseSmokeConfig
from ..support import parse_json_output, require, require_match, required_list, required_mapping
from .attestation_scale_contract import POSTING_CSV_HEADER, SCALE_PAGE_LIMIT
from .attestation_scale_posting_assertions import (
    record_posting_provenance_row,
    require_csv_posting_provenance_rows,
    require_expected_posting_commit,
)


def verify_paginated_list_postings(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    expected_commits: Mapping[str, AttestationCommit],
) -> None:
    """Require every JSON list-postings page to retain unique provenance."""
    cursor: str | None = None
    seen_posting_ids: set[str] = set()
    while True:
        arguments: list[str] = [
            operation_ids["listPostings"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--account-code",
            config.starter_cash_account_code,
            "--limit",
            str(SCALE_PAGE_LIMIT),
        ]
        if cursor is not None:
            arguments.extend(("--cursor", cursor))
        arguments.extend(("--output", "json"))
        envelope = parse_json_output(
            run_cli(config, *arguments),
            f"{config.label} scale list-postings output was not valid JSON",
        )
        payload = required_mapping(envelope, "payload")
        resolved_query = required_mapping(payload, "resolvedQuery")
        require(
            envelope.get("status") == "ok" and resolved_query.get("cursor") == cursor,
            f"{config.label} scale list-postings did not retain its supplied cursor",
        )
        postings = required_list(payload, "postings")
        require(
            postings,
            f"{config.label} scale list-postings emitted an empty non-terminal page",
        )
        for row in postings:
            record_posting_provenance_row(
                row,
                expected_commits,
                seen_posting_ids,
                config,
                "list-postings",
            )
        next_cursor = payload.get("nextCursor")
        if next_cursor is None:
            break
        require(
            isinstance(next_cursor, str) and bool(next_cursor) and next_cursor != cursor,
            f"{config.label} scale list-postings emitted an invalid continuation cursor",
        )
        cursor = next_cursor
    require(
        seen_posting_ids == set(expected_commits),
        f"{config.label} scale list-postings did not expose every posting with provenance",
    )


def verify_sample_get_posting(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    posting_ids: list[str],
    expected_commits: Mapping[str, AttestationCommit],
) -> None:
    """Require representative get-posting JSON and text output to retain provenance."""
    # get-posting deliberately advertises JSON and text only; list-postings and
    # account-ledger are the CSV-bearing posting query surfaces.  This scenario
    # proves get-posting's machine-readable commitment directly in JSON rather
    # than inventing a non-existent CSV contract.
    sample_indices = (0, len(posting_ids) // 2, len(posting_ids) - 1)
    for index in sample_indices:
        expected_posting_id = posting_ids[index]
        envelope = parse_json_output(
            run_cli(
                config,
                operation_ids["getPosting"],
                "--book-file",
                config.book.argument,
                "--book-key-file",
                config.book_key.argument,
                "--posting-id",
                expected_posting_id,
                "--output",
                "json",
            ),
            f"{config.label} scale get-posting output was not valid JSON",
        )
        posting = required_mapping(required_mapping(envelope, "payload"), "posting")
        require_expected_posting_commit(
            posting,
            expected_posting_id,
            expected_commits,
            config,
            "get-posting",
        )
        expected_commit = expected_commits[expected_posting_id]
        get_posting_text = run_cli(
            config,
            operation_ids["getPosting"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--posting-id",
            expected_posting_id,
            "--output",
            "text",
        )
        require_match(
            get_posting_text,
            rf"Attestation order[[:space:]]*:[[:space:]]*{expected_commit.operation_order}",
            f"{config.label} scale get-posting text did not retain its attestation order",
        )
        require_match(
            get_posting_text,
            rf"Attestation head[[:space:]]*:[[:space:]]*{expected_commit.operation_head}",
            f"{config.label} scale get-posting text did not retain its attestation head",
        )


def verify_list_postings_csv(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    expected_commits: Mapping[str, AttestationCommit],
) -> None:
    """Require list-postings CSV to retain its flat provenance contract."""
    header, rows = parse_csv_rows(
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
            "csv",
        ),
        f"{config.label} scale list-postings CSV",
    )
    require(
        header == POSTING_CSV_HEADER,
        f"{config.label} scale list-postings CSV did not retain the canonical column contract",
    )
    require_csv_posting_provenance_rows(rows, expected_commits, config, "list-postings CSV")
