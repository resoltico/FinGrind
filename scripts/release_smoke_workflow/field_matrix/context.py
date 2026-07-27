"""Scoped recorder access for the synchronous release-smoke command helpers."""

from __future__ import annotations

from collections.abc import Iterator, Mapping
from contextlib import contextmanager
from contextvars import ContextVar, Token
from typing import TYPE_CHECKING, Any

from ..models import ReleaseSmokeFailure
from .capabilities import ArtifactCapability
from .coverage import FieldMatrixSession

if TYPE_CHECKING:
    from ..attestation_head_checks import VerifiedAttestationHead

_ACTIVE_RECORDER: ContextVar[FieldMatrixSession | None] = ContextVar(
    "release_smoke_field_matrix_recorder", default=None
)


@contextmanager
def activate_field_matrix(session: FieldMatrixSession) -> Iterator[FieldMatrixSession]:
    """Make one field-matrix session available to nested command invocations."""
    token: Token[FieldMatrixSession | None] = _ACTIVE_RECORDER.set(session)
    try:
        yield session
    finally:
        _ACTIVE_RECORDER.reset(token)


def current_recorder() -> FieldMatrixSession | None:
    """Return the active matrix recorder, if the caller is inside release smoke."""
    return _ACTIVE_RECORDER.get()


def record_validated_operation(operation_id: str, output_mode: str | None) -> None:
    """Credit stdout only after its field scenario has validated its meaning.

    The generic CLI runner deliberately never records coverage.  A process exit
    and representation shape are not evidence that a command produced the
    selected operation's result.  Scenario owners call this narrow seam only
    after their native-mode and operation-specific semantic assertions pass.
    """
    recorder = current_recorder()
    if recorder is None:
        return
    recorder.capabilities.operation(operation_id)
    recorder.record_success(operation_id, output_mode)


def record_new_attestation_append(
    operation_id: str,
    envelope: Mapping[str, Any],
    *,
    before_head: VerifiedAttestationHead | None,
    after_head: VerifiedAttestationHead,
) -> None:
    """Require an observed, verified attestation-head advance for one append."""
    recorder = current_recorder()
    if recorder is None:
        raise ReleaseSmokeFailure(
            f"field-matrix append evidence for {operation_id} was reported outside release-smoke"
        )
    recorder.record_new_attestation_append(
        operation_id,
        envelope,
        before_head=before_head,
        after_head=after_head,
    )


def record_verified_artifact(operation_id: str, artifact: ArtifactCapability) -> None:
    """Record an artifact only after the caller has checked its semantic contract."""
    recorder = current_recorder()
    if recorder is None:
        return
    recorder.record_verified_artifact(operation_id, artifact)
