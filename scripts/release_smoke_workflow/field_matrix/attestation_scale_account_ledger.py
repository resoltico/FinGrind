"""Account-ledger provenance evidence for the attestation scale scenario."""

from __future__ import annotations

from collections.abc import Mapping

from ..account_ledger_assertions import ACCOUNT_LEDGER_CSV_HEADER
from ..attestation_head_checks import AttestationCommit
from ..cli import run_cli
from ..csv_support import parse_csv_rows
from ..models import ReleaseSmokeConfig
from ..support import parse_json_output, require, required_list, required_mapping
from .attestation_scale_contract import SCALE_EFFECTIVE_DATE
from .attestation_scale_posting_assertions import (
    record_posting_provenance_row,
    require_csv_posting_provenance_rows,
)


def verify_account_ledger_provenance(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    expected_commits: Mapping[str, AttestationCommit],
) -> None:
    """Require account-ledger JSON and CSV to retain every posting commitment."""
    query_arguments = (
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--account-code",
        config.starter_cash_account_code,
        "--effective-date-from",
        SCALE_EFFECTIVE_DATE,
        "--effective-date-to",
        SCALE_EFFECTIVE_DATE,
        "--limit",
        "50",
    )
    json_envelope = parse_json_output(
        run_cli(
            config,
            operation_ids["accountLedger"],
            *query_arguments,
            "--output",
            "json",
        ),
        f"{config.label} scale account-ledger JSON was not valid JSON",
    )
    json_rows = required_list(required_mapping(json_envelope, "payload"), "rows")
    seen_json_posting_ids: set[str] = set()
    for row in json_rows:
        record_posting_provenance_row(
            row,
            expected_commits,
            seen_json_posting_ids,
            config,
            "account-ledger JSON",
        )
    require(
        seen_json_posting_ids == set(expected_commits),
        f"{config.label} scale account-ledger JSON did not expose every posting provenance link",
    )

    csv_header, csv_rows = parse_csv_rows(
        run_cli(
            config,
            operation_ids["accountLedger"],
            *query_arguments,
            "--output",
            "csv",
        ),
        f"{config.label} scale account-ledger CSV",
    )
    require(
        csv_header == ACCOUNT_LEDGER_CSV_HEADER,
        f"{config.label} scale account-ledger CSV did not retain the canonical column contract",
    )
    require_csv_posting_provenance_rows(
        csv_rows,
        expected_commits,
        config,
        "account-ledger CSV",
    )
