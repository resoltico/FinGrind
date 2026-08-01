"""Synthetic verified-head transition contracts for mutable matrix evidence."""

from __future__ import annotations

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeFailure
from .capabilities import CapabilityMatrix
from .coverage import FieldMatrixSession
from .field_matrix_contract_fixtures import (
    append_envelope,
    head,
    new_append_envelope,
    record_all_stdout,
    report_artifacts,
    require_rejected,
)
from .scenario_matrix import ScenarioBinding


def assert_verified_append_requires_observed_advance(
    matrix: CapabilityMatrix,
    bindings: dict[str, ScenarioBinding],
) -> None:
    """Require exact next-order, parent, and book-identity evidence before append credit."""
    session = FieldMatrixSession(matrix, bindings)
    record_all_stdout(session)
    for artifact in report_artifacts(matrix):
        session.record_verified_artifact("report", artifact)
    replayed_head = head("7", "a")
    require_append_evidence_rejection(
        session,
        before_head=replayed_head,
        after_head=replayed_head,
        expected_message="did not advance the verified attestation order by exactly one",
        failure_message="field-matrix accepted a replayed verified attestation head",
    )
    require_append_evidence_rejection(
        session,
        before_head=head("6", "b"),
        after_head=head("7", "a", previous_head_character="c"),
        expected_message="prior head as its parent",
        failure_message="field-matrix accepted an append with the wrong verified parent head",
    )
    require_append_evidence_rejection(
        session,
        before_head=head("6", "b"),
        after_head=head(
            "7",
            "a",
            previous_head_character="b",
            book_id="00000000-0000-4000-8000-000000000043",
        ),
        expected_message="changed the verified book identity",
        failure_message="field-matrix accepted an append that changed its verified book identity",
    )
    genesis_session = FieldMatrixSession(matrix, bindings)
    genesis_session.record_new_attestation_append(
        "mutable-write",
        append_envelope("0", "c"),
        before_head=None,
        after_head=head("0", "c"),
    )
    assert "mutable-write" in genesis_session.new_attestation_appends
    require_append_evidence_rejection(
        FieldMatrixSession(matrix, bindings),
        before_head=None,
        after_head=head("0", "c", book_id="not-a-canonical-book-id"),
        expected_message="invalid book ID",
        failure_message="field-matrix accepted a genesis append with a non-canonical book ID",
        envelope=append_envelope("0", "c"),
    )
    try:
        session.assert_complete()
    except ReleaseSmokeFailure as exc:
        assert "missing newly appended mutable operations: mutable-write" in str(exc)
        return
    raise AssertionError("field-matrix recorded an append without an observed head advance")


def require_append_evidence_rejection(
    session: FieldMatrixSession,
    *,
    before_head: VerifiedAttestationHead | None,
    after_head: VerifiedAttestationHead,
    expected_message: str,
    failure_message: str,
    envelope: dict[str, object] | None = None,
) -> None:
    """Require malformed head evidence to fail before it can affect session accounting."""
    require_rejected(
        lambda: session.record_new_attestation_append(
            "mutable-write",
            new_append_envelope() if envelope is None else envelope,
            before_head=before_head,
            after_head=after_head,
        ),
        expected_message,
        failure_message,
    )
