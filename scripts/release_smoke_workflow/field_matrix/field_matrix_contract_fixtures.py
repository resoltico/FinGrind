"""Shared synthetic capabilities, heads, and assertions for field-matrix contracts."""

from __future__ import annotations

from collections.abc import Callable

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeFailure
from .capabilities import ArtifactCapability, CapabilityMatrix, OperationCapability
from .coverage import FieldMatrixSession
from .scenario_matrix import ScenarioBinding, ScenarioDomain


def synthetic_matrix() -> tuple[CapabilityMatrix, dict[str, ScenarioBinding]]:
    """Build the smallest live-capability matrix that exercises every evidence kind."""
    matrix = CapabilityMatrix.from_full_capabilities(synthetic_capabilities())
    bindings = {
        "raw-template": ScenarioBinding(ScenarioDomain.DISCOVERY),
        "report": ScenarioBinding(ScenarioDomain.REPORT),
        "mutable-write": ScenarioBinding(ScenarioDomain.POSTING, True),
    }
    return matrix, bindings


def synthetic_capabilities() -> dict[str, object]:
    """Return discovery-shaped capabilities with raw, report, and mutable routes."""
    return {
        "status": "ok",
        "payload": {
            "detail": "full",
            "commands": {
                "discovery": [
                    {
                        "name": "raw-template",
                        "displayLabel": "Raw Template",
                        "outputModes": [],
                        "artifactOutputs": [],
                    }
                ],
                "query": [
                    {
                        "name": "report",
                        "displayLabel": "Report",
                        "outputModes": ["json", "text", "csv"],
                        "artifactOutputs": [
                            {"format": "pdf", "option": "--pdf-out <path>"},
                            {"format": "pdf", "option": "--print-pdf <path>"},
                        ],
                    }
                ],
                "write": [
                    {
                        "name": "mutable-write",
                        "displayLabel": "Mutable Write",
                        "outputModes": ["json", "text"],
                        "artifactOutputs": [],
                    }
                ],
            },
        },
    }


def assert_capability_display_labels_are_required() -> None:
    """Reject a discovery descriptor without a durable display label."""
    for display_label in (None, ""):
        malformed_capabilities = {
            "status": "ok",
            "payload": {
                "detail": "full",
                "commands": {
                    "query": [
                        {
                            "name": "report",
                            "displayLabel": display_label,
                            "outputModes": [],
                            "artifactOutputs": [],
                        }
                    ]
                },
            },
        }
        try:
            CapabilityMatrix.from_full_capabilities(malformed_capabilities)
        except ReleaseSmokeFailure as exc:
            assert "displayLabel" in str(exc)
            continue
        raise AssertionError("field-matrix accepted a missing or blank command display label")


def record_all_stdout(session: FieldMatrixSession) -> None:
    """Credit the synthetic successful output modes, but no artifact or append proof."""
    session.record_success("raw-template", None)
    for output_mode in ("json", "text", "csv"):
        session.record_success("report", output_mode)
    for output_mode in ("json", "text"):
        session.record_success("mutable-write", output_mode)


def report_artifacts(matrix: CapabilityMatrix) -> tuple[ArtifactCapability, ArtifactCapability]:
    """Read the two independent PDF descriptor obligations from the synthetic report."""
    artifacts = matrix.operation("report").artifact_outputs
    assert len(artifacts) == 2
    return artifacts[0], artifacts[1]


def append_envelope(operation_order: str, operation_head_character: str) -> dict[str, object]:
    """Build the minimal JSON response commitment used by append evidence tests."""
    return {
        "payload": {
            "attestationCommit": {
                "operationOrder": operation_order,
                "operationHead": operation_head_character * 64,
            }
        }
    }


def new_append_envelope() -> dict[str, object]:
    """Return the standard post-mutation commitment fixture."""
    return append_envelope("7", "a")


def head(
    operation_order: str,
    operation_head_character: str,
    *,
    previous_head_character: str | None = None,
    book_id: str = "00000000-0000-4000-8000-000000000042",
) -> VerifiedAttestationHead:
    """Build one canonical verified head with a configurable parent edge."""
    previous_head = "0" * 64 if operation_order == "0" else (previous_head_character or "f") * 64
    return VerifiedAttestationHead(
        book_id,
        operation_order,
        operation_head_character * 64,
        previous_head,
    )


def record_verified_append(session: FieldMatrixSession) -> None:
    """Record the standard one-step verified head transition."""
    session.record_new_attestation_append(
        "mutable-write",
        new_append_envelope(),
        before_head=head("6", "b"),
        after_head=head("7", "a", previous_head_character="b"),
    )


def require_rejected(
    action: Callable[[], None], expected_message: str, failure_message: str
) -> None:
    """Require a synthetic contract action to fail with its expected diagnostic."""
    try:
        action()
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError(failure_message)


def trial_balance_operation() -> OperationCapability:
    """Return the report route used by report-semantic synthetic fixtures."""
    return OperationCapability("trial-balance", "Trial Balance", "query", (), ())
