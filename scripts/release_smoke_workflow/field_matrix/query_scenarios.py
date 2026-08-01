"""Live all-mode coverage for query operations that share the prepared smoke book."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ..attestation_arguments import ATTESTATION_CUSTODIAN
from ..models import ReleaseSmokeConfig
from ..support import require
from .capabilities import CapabilityMatrix, OperationCapability
from .invocation import invoke_all_advertised_modes
from .query_book_semantics import _attestation_key_mode_assertion, _book_query_mode_assertion
from .query_contract_catalog import (
    _BOOK_QUERY_ARGUMENTS,
    _QUERY_OPERATION_IDS,
    _QUERY_TEXT_TITLES,
    ModeSemanticAssertion,
)
from .query_receipt_scenarios import _export_matrix_receipts, _verify_receipts
from .query_runtime_facts import (
    _attestation_head_facts,
    _attestation_key_id,
    _book_access_arguments,
    _first_posting_id,
    _first_tax_registration_id,
)
from .scenario_matrix import SCENARIO_MATRIX, ScenarioDomain


def verify_query_matrix(
    config: ReleaseSmokeConfig,
    matrix: CapabilityMatrix,
    protected_book_format: Mapping[str, Any],
) -> None:
    """Exercise every advertised mode for safe read/query operations.

    This deliberately runs after the administrative plan: the prepared book then
    contains postings and a tax registration, so the matrix can use real durable
    facts rather than treating empty-result behavior as universal coverage.
    """
    print(f"{config.label}: verifying all safe query capability modes")
    _require_exact_query_routing(matrix)
    key_id = _attestation_key_id(config)
    attestation_head = _attestation_head_facts(config)
    tax_registration_id = _first_tax_registration_id(config)
    posting_id = _first_posting_id(config)
    _verify_attestation_key_file(
        config,
        matrix.operation("inspect-attestation-key-file"),
        key_id,
    )
    for operation_id, extra_arguments in _BOOK_QUERY_ARGUMENTS.items():
        _verify_book_query(
            config,
            matrix.operation(operation_id),
            extra_arguments,
            _book_query_mode_assertion(
                config,
                operation_id,
                attestation_head,
                tax_registration_id,
                posting_id,
                protected_book_format,
            ),
        )
    _verify_book_query(
        config,
        matrix.operation("get-posting"),
        ("--posting-id", posting_id),
        _book_query_mode_assertion(
            config,
            "get-posting",
            attestation_head,
            tax_registration_id,
            posting_id,
            protected_book_format,
        ),
    )
    receipt_operation = matrix.operation("export-attestation-receipt")
    receipt_verification_operation = matrix.operation("verify-receipt")
    receipt_paths = _export_matrix_receipts(
        config,
        receipt_operation,
        receipt_verification_operation,
        attestation_head,
    )
    _verify_receipts(config, receipt_verification_operation, receipt_paths)


def _verify_attestation_key_file(
    config: ReleaseSmokeConfig,
    operation: OperationCapability,
    key_id: str,
) -> None:
    invoke_all_advertised_modes(
        config,
        operation,
        lambda _mode: (
            "--attestation-custodian",
            ATTESTATION_CUSTODIAN,
            "--attestation-key-file",
            config.attestation_founder_key.argument,
        ),
        "attestation-key inspection matrix",
        _attestation_key_mode_assertion(config, key_id),
    )


def _require_exact_query_routing(matrix: CapabilityMatrix) -> None:
    live_operation_ids = {
        operation.operation_id
        for operation in matrix.operations.values()
        if operation.category == "query"
        and SCENARIO_MATRIX[operation.operation_id].domain
        in {ScenarioDomain.GENERIC_QUERY, ScenarioDomain.ATTESTATION_QUERY}
    }
    missing_routing = sorted(live_operation_ids - _QUERY_OPERATION_IDS)
    stale_routing = sorted(_QUERY_OPERATION_IDS - live_operation_ids)
    missing_text_titles = sorted(live_operation_ids - set(_QUERY_TEXT_TITLES))
    stale_text_titles = sorted(set(_QUERY_TEXT_TITLES) - live_operation_ids)
    require(
        not missing_routing
        and not stale_routing
        and not missing_text_titles
        and not stale_text_titles,
        _query_routing_mismatch_message(
            missing_routing,
            stale_routing,
            missing_text_titles,
            stale_text_titles,
        ),
    )


def _verify_book_query(
    config: ReleaseSmokeConfig,
    operation: OperationCapability,
    extra_arguments: tuple[str, ...],
    assert_mode_semantics: ModeSemanticAssertion,
) -> None:
    invoke_all_advertised_modes(
        config,
        operation,
        lambda _mode: _book_access_arguments(config, *extra_arguments),
        "book query matrix",
        assert_mode_semantics,
    )


def _query_routing_mismatch_message(
    missing_routing: list[str],
    stale_routing: list[str],
    missing_text_titles: list[str],
    stale_text_titles: list[str],
) -> str:
    parts = ["field-matrix generic query routing differs from live query capabilities"]
    if missing_routing:
        parts.append("missing query routing: " + ", ".join(missing_routing))
    if stale_routing:
        parts.append("stale query routing: " + ", ".join(stale_routing))
    if missing_text_titles:
        parts.append("missing query text titles: " + ", ".join(missing_text_titles))
    if stale_text_titles:
        parts.append("stale query text titles: " + ", ".join(stale_text_titles))
    return "; ".join(parts)
