"""Synthetic report identity and substantive-fact contracts across every output mode."""

from __future__ import annotations

import json
from types import SimpleNamespace

from ..models import ReleaseSmokeFailure
from .capabilities import OperationCapability
from .field_matrix_contract_fixtures import trial_balance_operation
from .report_contexts import ReportBookContext
from .report_output_semantics import _assert_report_semantics
from .tax_report_setup import TaxReportFact


def assert_report_facts_are_substantive_in_every_mode() -> None:
    """Reject metadata, text, CSV, and identity facts that belong to another report."""
    context = ReportBookContext(
        config=SimpleNamespace(label="synthetic field-matrix report"),
        period_start="2026-01-01",
        period_end="2026-01-31",
        as_of="2026-01-31",
        account_code="cash",
        expected_report_tokens=(("trial-balance", "known-account"),),
    )
    tax_fact = TaxReportFact("unused-registration", "unused-code", "1")
    operation = trial_balance_operation()
    metadata_only = {
        "family": "trial-balance",
        "resolvedQuery": {"accountCode": "known-account"},
        "rows": [{"accountCode": "other-account"}],
    }
    require_report_semantic_rejection(
        context,
        operation,
        metadata_only,
        "other-account",
        "other-account",
        tax_fact,
        "in substantive report data",
    )
    substantive = {
        "family": "trial-balance",
        "resolvedQuery": {"accountCode": "known-account"},
        "rows": [{"accountCode": "known-account"}],
    }
    require_report_semantic_rejection(
        context,
        operation,
        substantive,
        "other-account",
        "known-account",
        tax_fact,
        "outside query context",
    )
    require_report_semantic_rejection(
        context,
        operation,
        substantive,
        "known-account",
        "other-account",
        tax_fact,
        "in a report row",
    )
    _assert_report_semantics(
        context,
        operation,
        synthetic_report_outputs(substantive, "known-account", "known-account"),
        tax_fact,
    )
    require_report_identity_rejection(
        context,
        operation,
        tax_fact,
        "canonical report title",
        synthetic_report_outputs(
            substantive,
            "known-account",
            "known-account",
            text_title="Account Ledger",
        ),
    )
    require_report_identity_rejection(
        context,
        operation,
        tax_fact,
        "report family on every data row",
        synthetic_report_outputs(
            substantive,
            "known-account",
            "known-account",
            csv_family="account-ledger",
        ),
    )


def synthetic_report_outputs(
    payload: dict[str, object],
    text_fact: str,
    csv_fact: str,
    *,
    text_title: str = "Trial Balance",
    csv_family: str = "trial-balance",
) -> dict[str, str]:
    """Build matching JSON, text, and CSV representations for one report fixture."""
    return {
        "json": json.dumps({"status": "ok", "payload": payload}),
        "text": (
            f"{text_title}\n{'=' * len(text_title)}\n\nRows\n----\n"
            f"{text_fact}\n\nContext\n-------\nknown-account\n"
        ),
        "csv": f"family,accountCode,netAmount\n{csv_family},{csv_fact},100\n",
    }


def require_report_semantic_rejection(
    context: ReportBookContext,
    operation: OperationCapability,
    payload: dict[str, object],
    text_fact: str,
    csv_fact: str,
    tax_fact: TaxReportFact,
    expected_message: str,
) -> None:
    """Require one representation to fail when its expected substantive fact is misplaced."""
    try:
        _assert_report_semantics(
            context,
            operation,
            synthetic_report_outputs(payload, text_fact, csv_fact),
            tax_fact,
        )
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError("field-matrix accepted a report fact outside its required output payload")


def require_report_identity_rejection(
    context: ReportBookContext,
    operation: OperationCapability,
    tax_fact: TaxReportFact,
    expected_message: str,
    outputs: dict[str, str],
) -> None:
    """Require a report identity mismatch to fail even when its data fact is present."""
    try:
        _assert_report_semantics(context, operation, outputs, tax_fact)
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError("field-matrix accepted a report from the wrong operation family")
