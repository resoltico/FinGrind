from __future__ import annotations

import json

from .attestation_review_scope import ReviewScope
from .attestation_review_window_assertions import (
    REVIEW_WINDOW_EXCEEDS_HEAD_CODE,
    assert_review_window_error_json,
)
from .models import ReleaseSmokeFailure


def assert_review_window_error_contract() -> None:
    """Keep the typed review-window error assertion fail-closed between field runs."""
    scope = ReviewScope("a" * 64, "book-1", "7", "b" * 64, "c" * 64)
    assert_review_window_error_json(
        _error_envelope(scope, "8", None),
        scope,
        "8",
        None,
        "synthetic open review-window error",
    )
    assert_review_window_error_json(
        _error_envelope(scope, "0", "8"),
        scope,
        "0",
        "8",
        "synthetic bounded review-window error",
    )
    try:
        assert_review_window_error_json(
            _error_envelope(scope, "8", None, payload={}),
            scope,
            "8",
            None,
            "synthetic review-window error with a success payload",
        )
    except ReleaseSmokeFailure:
        return
    raise AssertionError("review-window error assertion accepted a success payload")


def _error_envelope(
    scope: ReviewScope,
    first_affected_order: str,
    last_affected_order: str | None,
    *,
    payload: dict[str, object] | None = None,
) -> str:
    envelope: dict[str, object] = {
        "status": "error",
        "category": "domain-semantic",
        "code": REVIEW_WINDOW_EXCEEDS_HEAD_CODE,
        "message": "Synthetic review-window error.",
        "hint": "Correct the review interval.",
        "argument": "--attestation-review-file",
        "details": {
            "credentialKeyId": scope.credential_key_id,
            "firstAffectedOrder": first_affected_order,
            "lastAffectedOrder": last_affected_order,
            "verifiedHeadOrder": scope.operation_order,
        },
    }
    if payload is not None:
        envelope["payload"] = payload
    return json.dumps(envelope)
