from __future__ import annotations

import re

from .account_ledger_assertions import assert_account_ledger_csv
from .artifact_contracts import reported_pdf_artifact_path_matches
from .models import ReleaseSmokeConfig
from .path_support import extract_pdf_artifact_path
from .support import require, require_match, require_no_match

_POSTING_RECORD_HEADING = re.compile(r"20\d{2}-\d{2}-\d{2} \| .+")
_POSTING_RECORD_SEPARATOR = re.compile(r"-+")
_POSTING_REFERENCE = re.compile(r"^Posting ref[ \t]*:[ \t]*\S+[ \t]*$", re.MULTILINE)
_POSTING_ATTESTATION_ORDER = re.compile(r"^Attestation order[ \t]*:[ \t]*\d+[ \t]*$", re.MULTILINE)


def assert_operator_queries_and_reports(
    config: ReleaseSmokeConfig,
    list_postings_text_output: str,
    account_balance_text_output: str,
    trial_balance_text_output: str,
    pdf_stdout: str,
    pdf_stderr: str,
    account_ledger_csv_output: str,
    period_summary_text_output: str,
) -> None:
    _assert_postings_text(config, list_postings_text_output)
    _assert_account_balance_text(config, account_balance_text_output)
    _assert_trial_balance_text(config, trial_balance_text_output)
    _assert_pdf_export(config, trial_balance_text_output, pdf_stdout, pdf_stderr)
    assert_account_ledger_csv(config, account_ledger_csv_output)
    _assert_period_summary_text(config, period_summary_text_output)


def _assert_postings_text(config: ReleaseSmokeConfig, text_output: str) -> None:
    for pattern, message in (
        (r"^Postings$", "report title"),
        (r"Returned postings[[:space:]]+:[[:space:]]+2", "returned-posting count"),
        (r"2026-04-08", "latest effective date"),
        (r"2026-04-07", "earlier effective date"),
        (r"10\.00", "accounting-scale amounts"),
    ):
        require_match(
            text_output,
            pattern,
            f"{config.label} text posting register did not render the {message}",
        )
    _assert_inline_posting_attestation_orders(config, text_output)
    require_no_match(
        text_output,
        r"^Attestation commitments$",
        f"{config.label} text posting register still rendered the detached attestation appendix",
    )
    require_no_match(
        text_output,
        r"\b[0-9a-f]{64}\b",
        f"{config.label} text posting register leaked a full attestation head",
    )


def _assert_inline_posting_attestation_orders(config: ReleaseSmokeConfig, text_output: str) -> None:
    record_blocks = [
        block for block in text_output.split("\n\n") if _is_posting_record_block(block)
    ]
    require(
        len(record_blocks) == 2,
        f"{config.label} text posting register did not render one adaptive record block per posting",
    )
    for record_block in record_blocks:
        require(
            len(_POSTING_REFERENCE.findall(record_block)) == 1,
            f"{config.label} text posting record did not render exactly one inline posting reference",
        )
        require(
            len(_POSTING_ATTESTATION_ORDER.findall(record_block)) == 1,
            f"{config.label} text posting record did not render exactly one inline attestation order",
        )


def _is_posting_record_block(block: str) -> bool:
    lines = block.splitlines()
    return (
        len(lines) >= 2
        and _POSTING_RECORD_HEADING.fullmatch(lines[0]) is not None
        and _POSTING_RECORD_SEPARATOR.fullmatch(lines[1]) is not None
    )


def _assert_account_balance_text(config: ReleaseSmokeConfig, text_output: str) -> None:
    for pattern, message in (
        (r"^Account Balance$", "report title"),
        (
            rf"Account[[:space:]]+:[[:space:]]+.+[[:space:]]+\[{re.escape(config.starter_cash_account_code)}\]",
            "selected account",
        ),
        (r"6\.00", "expected net balance"),
    ):
        require_match(
            text_output,
            pattern,
            f"{config.label} text account-balance output did not render the {message}",
        )
    for pattern, message in (
        (
            r"Seed template[[:space:]]+:[[:space:]]+Owner-managed service seed template",
            "seed-template context",
        ),
        (r"Accounting basis[[:space:]]+:[[:space:]]+Cash basis", "accounting-basis context"),
    ):
        require_match(
            text_output,
            pattern,
            f"{config.label} text account-balance output did not render the {message}",
        )
    require_no_match(
        text_output,
        r"Starter chart|starter chart|starterChart",
        f"{config.label} text account-balance output still used the retired starter-chart label",
    )


def _assert_trial_balance_text(config: ReleaseSmokeConfig, text_output: str) -> None:
    for pattern, message in (
        (r"^Trial Balance$", "report title"),
        (r"As of[[:space:]]+:[[:space:]]+2026-04-08", "as-of date"),
        (re.escape(config.starter_cash_account_code), "seeded cash account"),
        (r"6\.00", "expected net amount"),
    ):
        require_match(
            text_output,
            pattern,
            f"{config.label} trial-balance output did not render the {message}",
        )
    for pattern, message in (
        (
            r"Seed template[[:space:]]+:[[:space:]]+Owner-managed service seed template",
            "seed-template context",
        ),
        (r"Accounting basis[[:space:]]+:[[:space:]]+Cash basis", "accounting-basis context"),
    ):
        require_match(
            text_output,
            pattern,
            f"{config.label} trial-balance output did not render the {message}",
        )
    require_no_match(
        text_output,
        r"Starter chart|starter chart|starterChart",
        f"{config.label} trial-balance output still used the retired starter-chart label",
    )


def _assert_pdf_export(
    config: ReleaseSmokeConfig,
    trial_balance_text_output: str,
    pdf_stdout: str,
    pdf_stderr: str,
) -> None:
    require(
        config.trial_balance_pdf.local_path.is_file(),
        f"{config.label} trial-balance did not write the requested PDF artifact",
    )
    require(
        config.trial_balance_pdf.local_path.read_bytes().startswith(b"%PDF-"),
        f"{config.label} trial-balance PDF artifact did not start with %PDF-",
    )
    require(
        pdf_stdout != trial_balance_text_output,
        f"{config.label} PDF export did not replace stdout with one artifact confirmation block",
    )
    require(
        "Trial Balance" not in pdf_stdout,
        f"{config.label} PDF export leaked the full text report body onto stdout",
    )
    for pattern, message in (
        (r"^Artifact$", "artifact confirmation heading"),
        (r"^Format[[:space:]]+:[[:space:]]+pdf$", "artifact format line"),
        (r"^Path[[:space:]]+:[[:space:]]+.+$", "artifact path line"),
    ):
        require_match(
            pdf_stdout,
            pattern,
            f"{config.label} PDF export did not emit the {message}",
        )
    require(
        pdf_stderr == "",
        f"{config.label} successful PDF export emitted diagnostics instead of staying silent.\n"
        f"Captured stderr file: {config.trial_balance_pdf_stderr_path}\n"
        f"Captured stderr:\n{pdf_stderr}",
    )
    reported_pdf_path = extract_pdf_artifact_path(pdf_stdout)
    require(
        reported_pdf_artifact_path_matches(config, config.trial_balance_pdf, reported_pdf_path),
        f"{config.label} PDF export stdout did not report the canonical physical artifact path",
    )


def _assert_period_summary_text(config: ReleaseSmokeConfig, text_output: str) -> None:
    for pattern, message in (
        (r"^Period Summary$", "report title"),
        (r"Posting count", "posting-count metadata"),
        (r"2", "expected posting count"),
    ):
        require_match(
            text_output,
            pattern,
            f"{config.label} period-summary output did not render the {message}",
        )
