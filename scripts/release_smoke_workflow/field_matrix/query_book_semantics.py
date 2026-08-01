"""Semantic proof for key, book, and attestation query representations."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ..artifact_contracts import reported_artifact_path_matches
from ..models import ReleaseSmokeConfig
from ..support import require
from .query_contract_catalog import ModeSemanticAssertion
from .query_listing_semantics import (
    _assert_get_posting_mode,
    _assert_list_accounts_mode,
    _assert_list_postings_mode,
    _assert_list_tax_registrations_mode,
)
from .query_models import AttestationHeadFacts
from .query_response_support import (
    _require_attestation_head_payload,
    _required_integer,
    _required_mapping,
    _required_text,
    _success_payload,
)
from .query_text_contract import _public_path_token, _require_text_facts


def _attestation_key_mode_assertion(
    config: ReleaseSmokeConfig,
    key_id: str,
) -> ModeSemanticAssertion:
    def assertion(output_mode: str, output: str) -> None:
        if output_mode == "json":
            payload = _success_payload(config, "inspect-attestation-key-file", output)
            require(
                payload.get("keyId") == key_id
                and isinstance(payload.get("credentialSpki"), str)
                and bool(payload["credentialSpki"].strip()),
                f"{config.label} field-matrix inspect-attestation-key-file[json] did not retain "
                "the selected credential identity",
            )
            return
        _require_text_facts(
            config,
            "inspect-attestation-key-file",
            output_mode,
            output,
            key_id,
            "Credential SPKI",
        )

    return assertion


def _book_query_mode_assertion(
    config: ReleaseSmokeConfig,
    operation_id: str,
    attestation_head: AttestationHeadFacts,
    tax_registration_id: str,
    posting_id: str,
    protected_book_format: Mapping[str, Any],
) -> ModeSemanticAssertion:
    def assertion(output_mode: str, output: str) -> None:
        if operation_id == "inspect-book":
            _assert_inspect_book_mode(config, output_mode, output, protected_book_format)
            return
        if operation_id == "verify-book":
            _assert_verify_book_mode(config, output_mode, output, attestation_head)
            return
        if operation_id == "attestation-review":
            _assert_attestation_review_mode(config, output_mode, output, attestation_head)
            return
        if operation_id == "list-accounts":
            _assert_list_accounts_mode(config, output_mode, output)
            return
        if operation_id == "list-tax-registrations":
            _assert_list_tax_registrations_mode(
                config,
                output_mode,
                output,
                tax_registration_id,
            )
            return
        if operation_id == "list-postings":
            _assert_list_postings_mode(config, output_mode, output, posting_id)
            return
        if operation_id == "get-posting":
            _assert_get_posting_mode(config, output_mode, output, posting_id)
            return
        raise AssertionError(f"unrouted query operation: {operation_id}")

    return assertion


def _assert_inspect_book_mode(
    config: ReleaseSmokeConfig,
    output_mode: str,
    output: str,
    protected_book_format: Mapping[str, Any],
) -> None:
    purpose = f"{config.label} field-matrix inspect-book[{output_mode}]"
    expected_application_id = _required_integer(
        protected_book_format,
        "applicationId",
        f"{purpose} canonical protected-book format",
    )
    expected_format_version = _required_integer(
        protected_book_format,
        "formatVersion",
        f"{purpose} canonical protected-book format",
    )
    if output_mode == "json":
        payload = _success_payload(config, "inspect-book", output)
        reported_book_file = _required_text(
            payload,
            "bookFile",
            purpose,
        )
        migration_policy = _required_mapping(payload, "migrationPolicy", purpose)
        require(
            reported_artifact_path_matches(config, config.book, reported_book_file)
            and payload.get("state") == "initialized"
            and payload.get("compatibleWithCurrentBinary") is True
            and payload.get("canInitializeWithOpenBook") is False
            and payload.get("applicationId") == expected_application_id
            and payload.get("detectedBookFormatVersion") == expected_format_version
            and payload.get("supportedBookFormatVersion") == expected_format_version
            and migration_policy.get("supportedBookFormatVersion") == expected_format_version,
            f"{purpose} did not retain the prepared book's canonical format identity",
        )
        return
    _require_text_facts(
        config,
        "inspect-book",
        output_mode,
        output,
        _public_path_token(config.book),
        config.entity_name,
        "SQLite applicationId",
        "Supported book format version",
        "Detected book format version",
        str(expected_application_id),
        str(expected_format_version),
    )


def _assert_verify_book_mode(
    config: ReleaseSmokeConfig,
    output_mode: str,
    output: str,
    facts: AttestationHeadFacts,
) -> None:
    if output_mode == "json":
        payload = _success_payload(config, "verify-book", output)
        _require_attestation_head_payload(
            payload,
            facts,
            f"{config.label} field-matrix verify-book[json]",
        )
        return
    _require_text_facts(
        config,
        "verify-book",
        output_mode,
        output,
        facts.book_id,
        facts.operation_order,
        facts.operation_head,
        facts.previous_head,
    )


def _assert_attestation_review_mode(
    config: ReleaseSmokeConfig,
    output_mode: str,
    output: str,
    facts: AttestationHeadFacts,
) -> None:
    if output_mode == "json":
        payload = _success_payload(config, "attestation-review", output)
        verified_head = _required_mapping(
            payload,
            "verifiedAttestationHead",
            f"{config.label} field-matrix attestation-review[json]",
        )
        require(
            set(payload) == {"bookId", "verifiedAttestationHead", "findings"}
            and set(verified_head) == {"operationOrder", "operationHead"}
            and payload.get("bookId") == facts.book_id
            and verified_head.get("operationOrder") == facts.operation_order
            and verified_head.get("operationHead") == facts.operation_head
            and isinstance(payload.get("findings"), list),
            f"{config.label} field-matrix attestation-review[json] did not retain the live "
            "attestation review scope",
        )
        return
    _require_text_facts(
        config,
        "attestation-review",
        output_mode,
        output,
        facts.book_id,
        facts.operation_order,
    )
