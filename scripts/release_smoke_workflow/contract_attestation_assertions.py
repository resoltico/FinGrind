from __future__ import annotations

from .attestation_head_checks import (
    attestation_commit_from_payload,
    require_attestation_commit_matches_verified_head,
    require_no_attestation_commit,
    require_plan_attestation_disposition,
    verified_attestation_head_from_envelope,
)
from .models import ReleaseSmokeFailure
from .support import require


def assert_attestation_payload_contracts() -> None:
    assert_verified_attestation_head_contract()
    assert_attestation_commit_payload_contract()


def assert_verified_attestation_head_contract() -> None:
    verified_head = verified_attestation_head_from_envelope(
        {
            "status": "ok",
            "payload": {
                "bookId": "00000000-0000-4000-8000-000000000042",
                "verifiedAttestationHead": {
                    "operationOrder": "42",
                    "operationHead": "a" * 64,
                },
                "previousHead": "b" * 64,
            },
        },
        "synthetic release smoke",
        "verified-head contract",
    )
    require(
        verified_head.book_id == "00000000-0000-4000-8000-000000000042"
        and verified_head.operation_order == "42"
        and verified_head.operation_head == "a" * 64
        and verified_head.previous_head == "b" * 64,
        "verified attestation-head parsing did not retain the complete authenticated chain position",
    )
    _require_invalid_verified_head_rejected(
        {
            "status": "ok",
            "payload": {
                "bookId": "00000000-0000-4000-8000-000000000042",
                "verifiedAttestationHead": {
                    "operationOrder": "42",
                    "operationHead": "A" * 64,
                },
                "previousHead": "b" * 64,
            },
        },
        "malformed verified-head contract",
        "verified attestation-head parsing accepted a non-canonical operation head",
    )
    _require_invalid_verified_head_rejected(
        {
            "status": "ok",
            "payload": {
                "bookId": "00000000-0000-4000-8000-000000000042",
                "headOrder": "42",
                "operationHead": "a" * 64,
                "previousHead": "b" * 64,
            },
        },
        "retired flat verified-head contract",
        "verified attestation-head parsing accepted the retired flat shape",
    )


def _require_invalid_verified_head_rejected(
    envelope: dict[str, object], purpose: str, failure_message: str
) -> None:
    try:
        verified_attestation_head_from_envelope(envelope, "synthetic release smoke", purpose)
    except ReleaseSmokeFailure:
        return
    raise AssertionError(failure_message)


def assert_attestation_commit_payload_contract() -> None:
    commit = attestation_commit_from_payload(
        {
            "attestationCommit": {
                "operationOrder": "43",
                "operationHead": "b" * 64,
            }
        },
        "synthetic release smoke",
        "attestation-commit contract",
    )
    require(
        commit.operation_order == "43" and commit.operation_head == "b" * 64,
        "attestation-commit parsing did not retain the complete authenticated pair",
    )
    require_attestation_commit_matches_verified_head(
        commit,
        verified_attestation_head_from_envelope(
            {
                "status": "ok",
                "payload": {
                    "bookId": "00000000-0000-4000-8000-000000000042",
                    "verifiedAttestationHead": {
                        "operationOrder": "43",
                        "operationHead": "b" * 64,
                    },
                    "previousHead": "a" * 64,
                },
            },
            "synthetic release smoke",
            "attestation-commit matching contract",
        ),
        "synthetic release smoke",
        "attestation-commit matching contract",
    )
    require_no_attestation_commit(
        {"attestationCommit": None},
        "synthetic release smoke",
        "read-only attestation-commit contract",
    )
    require_plan_attestation_disposition(
        {
            "attestationDisposition": "appended",
            "attestationCommit": {
                "operationOrder": "43",
                "operationHead": "b" * 64,
            },
        },
        "synthetic release smoke",
        "appended plan disposition contract",
        "appended",
    )
    require_plan_attestation_disposition(
        {"attestationDisposition": "read-only", "attestationCommit": None},
        "synthetic release smoke",
        "read-only plan disposition contract",
        "read-only",
    )
    require_plan_attestation_disposition(
        {
            "attestationDisposition": "no-durable-child-mutation",
            "attestationCommit": None,
        },
        "synthetic release smoke",
        "replayed plan disposition contract",
        "no-durable-child-mutation",
    )
    try:
        attestation_commit_from_payload(
            {
                "attestationCommit": {
                    "operationOrder": "43",
                    "operationHead": "B" * 64,
                }
            },
            "synthetic release smoke",
            "malformed attestation-commit contract",
        )
    except ReleaseSmokeFailure:
        pass
    else:
        raise AssertionError("attestation-commit parsing accepted a non-canonical operation head")
    try:
        require_plan_attestation_disposition(
            {
                "attestationDisposition": "read-only",
                "attestationCommit": {
                    "operationOrder": "43",
                    "operationHead": "b" * 64,
                },
            },
            "synthetic release smoke",
            "read-only plan disposition with append",
            "read-only",
        )
    except ReleaseSmokeFailure:
        pass
    else:
        raise AssertionError("read-only plan disposition accepted an appended commitment")
    try:
        require_no_attestation_commit(
            {"attestationCommit": {"operationOrder": "43", "operationHead": "b" * 64}},
            "synthetic release smoke",
            "mutating attestation-commit contract",
        )
    except ReleaseSmokeFailure:
        return
    raise AssertionError("read-only attestation-commit parsing accepted an appended commitment")
