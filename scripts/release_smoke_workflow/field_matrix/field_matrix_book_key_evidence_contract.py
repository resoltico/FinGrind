"""Synthetic contract for transaction-backed generated book-key responses."""

from __future__ import annotations

import json
from types import SimpleNamespace

from .field_matrix_query_identity_contract import require_rejected
from .mutation_evidence_bootstrap import assert_generated_book_key_response


def assert_generated_book_key_publication_contract() -> None:
    """Require generated book keys to expose one public transaction-only artifact."""
    config = SimpleNamespace(label="synthetic field-matrix mutation")
    generated_book_key = {
        "status": "ok",
        "payload": {"encoding": "base64url", "entropyBits": 256, "permissions": "0600"},
        "artifacts": [
            {
                "format": "book-key-file",
                "publicationTransaction": {
                    "id": "0123456789abcdef0123456789abcdef",
                    "state": "complete",
                    "commitOutcome": "committed",
                    "cleanupOutcome": "clean",
                },
            }
        ],
    }
    assert_generated_book_key_response(
        config, "json", json.dumps(generated_book_key), "synthetic generated book key"
    )
    require_rejected(
        lambda: assert_generated_book_key_response(
            config,
            "json",
            json.dumps({**generated_book_key, "artifacts": []}),
            "missing synthetic generated book-key artifact",
        ),
        "one book-key artifact",
        "field-matrix accepted a generated book key without its transaction artifact",
    )
    generated_book_key["artifacts"][0]["retainedStage"] = "private.key.stage"
    require_rejected(
        lambda: assert_generated_book_key_response(
            config,
            "json",
            json.dumps(generated_book_key),
            "leaked synthetic generated book-key stage",
        ),
        "private retainedStage",
        "field-matrix accepted a generated book key with a leaked retained stage",
    )
