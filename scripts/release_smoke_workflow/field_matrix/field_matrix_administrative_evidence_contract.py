"""Synthetic route-specific registry, tax, key, and genesis mutation evidence contracts."""

from __future__ import annotations

import inspect
import json
from types import SimpleNamespace

from .field_matrix_contract_fixtures import head
from .field_matrix_query_identity_contract import require_rejected
from .mutation_evidence_accounts import assert_account_mutation_response
from .mutation_evidence_bootstrap import (
    assert_generated_attestation_key_response,
    assert_generated_book_key_response,
    assert_open_book_response,
)
from .mutation_evidence_tax import assert_tax_registration_mutation_response


def assert_administrative_evidence_is_route_specific() -> None:
    """Require every non-posting mutation route to publish its own durable evidence."""
    config = SimpleNamespace(label="synthetic field-matrix mutation")
    expected_head = head("7", "a", previous_head_character="b")
    account_request = {"accountCode": "cash", "accountName": "Cash"}
    account_output = json.dumps(
        {
            "status": "ok",
            "payload": {
                "outcome": "declared",
                "account": {"accountCode": "cash", "accountName": "Cash", "active": True},
                "attestationCommit": {
                    "operationOrder": expected_head.operation_order,
                    "operationHead": expected_head.operation_head,
                },
            },
        }
    )
    assert_account_mutation_response(
        config,
        "declare-account",
        account_request,
        "json",
        account_output,
        expected_head,
        "synthetic account mutation",
    )
    require_rejected(
        lambda: assert_account_mutation_response(
            config,
            "declare-account",
            account_request,
            "json",
            json.dumps(
                {
                    "status": "ok",
                    "payload": {
                        "outcome": "declared",
                        "account": {"accountCode": "other", "accountName": "Cash", "active": True},
                        "attestationCommit": {
                            "operationOrder": expected_head.operation_order,
                            "operationHead": expected_head.operation_head,
                        },
                    },
                }
            ),
            expected_head,
            "wrong account mutation",
        ),
        "account lifecycle result",
        "field-matrix accepted a mismatched account response",
    )
    tax_request = {
        "taxRegistrationId": "lv-vat",
        "payableAccountCode": "vat-payable",
        "recoverableAccountCode": "vat-recoverable",
    }
    assert_tax_registration_mutation_response(
        config,
        "declare-tax-registration",
        tax_request,
        "json",
        json.dumps(
            {
                "status": "ok",
                "payload": {
                    "outcome": "declared",
                    "registration": dict(tax_request),
                    "attestationCommit": {
                        "operationOrder": expected_head.operation_order,
                        "operationHead": expected_head.operation_head,
                    },
                },
            }
        ),
        expected_head,
        "synthetic tax mutation",
    )
    assert_generated_book_key_response(
        config,
        "json",
        json.dumps(
            {
                "status": "ok",
                "payload": {"encoding": "base64url", "entropyBits": 256, "permissions": "0600"},
            }
        ),
        "synthetic generated book key",
    )
    generated_credential = assert_generated_attestation_key_response(
        config,
        "json",
        json.dumps({"status": "ok", "payload": {"credentialSpki": "spki-1", "keyId": "key-1"}}),
        "synthetic generated credential",
    )
    assert generated_credential.credential_spki == "spki-1"
    assert_open_book_response(
        config,
        "json",
        json.dumps(
            {
                "status": "ok",
                "payload": {
                    "attestationBookId": expected_head.book_id,
                    "bookIdentity": {},
                    "attestationTrustRoot": {},
                    "attestationCommit": {
                        "operationOrder": expected_head.operation_order,
                        "operationHead": expected_head.operation_head,
                    },
                },
            }
        ),
        expected_head,
        "synthetic open-book",
    )
    require_rejected(
        lambda: assert_open_book_response(
            config,
            "json",
            json.dumps(
                {
                    "status": "ok",
                    "payload": {
                        "attestationBookId": "00000000-0000-4000-8000-000000000099",
                        "bookIdentity": {},
                        "attestationTrustRoot": {},
                        "attestationCommit": {
                            "operationOrder": expected_head.operation_order,
                            "operationHead": expected_head.operation_head,
                        },
                    },
                }
            ),
            expected_head,
            "wrong open-book",
        ),
        "did not identify its attested book",
        "field-matrix accepted an open-book response with another book identity",
    )
    assert_evidence_precedes_coverage_credit()


def assert_evidence_precedes_coverage_credit() -> None:
    """Keep route-specific response proof before shared matrix credit in each writer."""
    from . import (
        administrative_operation_runner,
        administrative_world_bootstrap,
        typed_record_execution,
    )

    administrative_source = inspect.getsource(administrative_operation_runner._run_operation)
    assert administrative_source.index(
        "_assert_administrative_operation_evidence("
    ) < administrative_source.index("_process_operation_output(")
    typed_source = inspect.getsource(typed_record_execution._run_typed_request)
    assert typed_source.index("assert_committed_posting_response(") < typed_source.index(
        "record_new_attestation_append("
    )
    bootstrap_source = inspect.getsource(administrative_world_bootstrap._new_world)
    assert bootstrap_source.index("assert_open_book_response(") < bootstrap_source.index(
        "_process_operation_output("
    )
