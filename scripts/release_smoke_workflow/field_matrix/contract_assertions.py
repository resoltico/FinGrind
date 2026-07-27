"""Orchestrated synthetic regressions for the fail-closed field-matrix infrastructure."""

from __future__ import annotations

from .field_matrix_administrative_evidence_contract import (
    assert_administrative_evidence_is_route_specific,
)
from .field_matrix_append_contract import assert_verified_append_requires_observed_advance
from .field_matrix_cash_flow_contract import assert_cash_flow_totals_are_substantive_and_articulate
from .field_matrix_contract_fixtures import (
    assert_capability_display_labels_are_required,
    synthetic_matrix,
)
from .field_matrix_coverage_contract import assert_coverage_contracts
from .field_matrix_csv_pdf_contract import assert_csv_pdf_refusal_uses_text_diagnostics
from .field_matrix_invocation_contract import assert_invocation_contracts
from .field_matrix_posting_evidence_contract import assert_posting_evidence_is_route_specific
from .field_matrix_query_identity_contract import (
    assert_query_identity_is_not_inferred_from_shared_facts,
)
from .field_matrix_report_content_contract import assert_report_facts_are_substantive_in_every_mode
from .field_matrix_tax_setup_contract import (
    assert_protected_book_format_boundaries_are_adjacent,
    assert_tax_report_setup_parses_durable_tax_fact,
)


def assert_field_matrix_contracts() -> None:
    """Run each independent synthetic proof for live-capability matrix invariants."""
    matrix, bindings = synthetic_matrix()
    assert matrix.operation("report").display_label == "Report"
    assert_capability_display_labels_are_required()
    assert_coverage_contracts(matrix, bindings)
    assert_verified_append_requires_observed_advance(matrix, bindings)
    assert_invocation_contracts(matrix, bindings)
    assert_csv_pdf_refusal_uses_text_diagnostics()
    assert_report_facts_are_substantive_in_every_mode()
    assert_cash_flow_totals_are_substantive_and_articulate()
    assert_protected_book_format_boundaries_are_adjacent()
    assert_tax_report_setup_parses_durable_tax_fact()
    assert_query_identity_is_not_inferred_from_shared_facts()
    assert_posting_evidence_is_route_specific()
    assert_administrative_evidence_is_route_specific()
