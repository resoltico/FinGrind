"""Synthetic route-specific posting and durable-state evidence contracts."""

from __future__ import annotations

import json
from types import SimpleNamespace

from .field_matrix_contract_fixtures import head
from .field_matrix_query_identity_contract import require_rejected
from .mutation_evidence_posting import (
    assert_committed_posting_response,
    assert_persisted_posting_state,
)


def assert_posting_evidence_is_route_specific() -> None:
    """A chain head transition cannot substitute for a concrete posting response and state."""
    config = SimpleNamespace(label="synthetic field-matrix mutation")
    expected_head = head("7", "a", previous_head_character="b")
    request = {
        "entryKind": "DIRECT_JOURNAL",
        "effectiveDate": "2026-01-02",
        "provenance": {"idempotencyKey": "posting-idempotency"},
    }
    committed_output = json.dumps(
        {
            "status": "ok",
            "payload": {
                "postingId": "posting-1",
                "idempotencyKey": "posting-idempotency",
                "effectiveDate": "2026-01-02",
                "idempotentReplay": False,
                "attestationCommit": {
                    "operationOrder": expected_head.operation_order,
                    "operationHead": expected_head.operation_head,
                },
            },
        }
    )
    evidence = assert_committed_posting_response(
        config,
        "post-entry",
        request,
        "json",
        committed_output,
        expected_head,
        "synthetic posting mutation",
    )
    assert_persisted_posting_state(
        {
            "postingId": "posting-1",
            "idempotencyKey": "posting-idempotency",
            "effectiveDate": "2026-01-02",
            "entry": {"entryKind": "DIRECT_JOURNAL"},
        },
        evidence,
        purpose="synthetic posting durable state",
    )
    require_rejected(
        lambda: assert_committed_posting_response(
            config,
            "post-entry",
            request,
            "json",
            json.dumps(
                {
                    "status": "ok",
                    "payload": {
                        "attestationCommit": {
                            "operationOrder": expected_head.operation_order,
                            "operationHead": expected_head.operation_head,
                        }
                    },
                }
            ),
            expected_head,
            "generic successful mutation",
        ),
        "postingId",
        "field-matrix accepted a generic posting response",
    )
    require_rejected(
        lambda: assert_persisted_posting_state(
            {
                "postingId": "posting-1",
                "idempotencyKey": "wrong-idempotency",
                "effectiveDate": "2026-01-02",
                "entry": {"entryKind": "DIRECT_JOURNAL"},
            },
            evidence,
            purpose="synthetic mismatched durable posting",
        ),
        "did not persist",
        "field-matrix accepted mismatched durable posting state",
    )
