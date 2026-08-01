"""Synthetic contracts for capability routing and independent coverage obligations."""

from __future__ import annotations

from ..models import ReleaseSmokeFailure
from ..support import require
from .capabilities import CapabilityMatrix
from .context import activate_field_matrix
from .coverage import FieldMatrixSession
from .field_matrix_contract_fixtures import (
    head,
    record_all_stdout,
    record_verified_append,
    report_artifacts,
)
from .invocation import validate_and_record_raw_json_operation
from .scenario_matrix import ScenarioBinding


def assert_coverage_contracts(
    matrix: CapabilityMatrix,
    bindings: dict[str, ScenarioBinding],
) -> None:
    """Prove routing, raw-template, artifact, and replay obligations stay independent."""
    assert_exact_binding_guard(matrix, bindings)
    assert_raw_template_has_no_invented_mode(matrix, bindings)
    assert_raw_template_semantics_are_not_envelope_bound(matrix, bindings)
    assert_artifact_requires_explicit_verification(matrix, bindings)
    assert_replayed_mutation_cannot_discharge_append(matrix, bindings)


def assert_exact_binding_guard(
    matrix: CapabilityMatrix,
    bindings: dict[str, ScenarioBinding],
) -> None:
    """Reject a live operation that lacks an explicit scenario route."""
    incomplete_bindings = dict(bindings)
    incomplete_bindings.pop("report")
    try:
        FieldMatrixSession(matrix, incomplete_bindings)
    except ReleaseSmokeFailure as exc:
        assert "missing scenario bindings: report" in str(exc)
        return
    raise AssertionError("field-matrix accepted a live operation without scenario routing")


def assert_raw_template_has_no_invented_mode(
    matrix: CapabilityMatrix,
    bindings: dict[str, ScenarioBinding],
) -> None:
    """Reject a fabricated JSON output mode for an intentionally raw template."""
    session = FieldMatrixSession(matrix, bindings)
    session.record_success("raw-template", None)
    try:
        session.record_success("raw-template", "json")
    except ReleaseSmokeFailure:
        return
    raise AssertionError("field-matrix invented a JSON stdout mode for a raw template")


def assert_raw_template_semantics_are_not_envelope_bound(
    matrix: CapabilityMatrix,
    bindings: dict[str, ScenarioBinding],
) -> None:
    """Require a template scaffold before its raw JSON response earns coverage credit."""
    session = FieldMatrixSession(matrix, bindings)

    def require_request_scaffold(payload: dict[str, object]) -> None:
        require(
            payload.get("requestId") == "fresh-template",
            "synthetic raw template did not retain its request scaffold",
        )

    with activate_field_matrix(session):
        raw_payload = validate_and_record_raw_json_operation(
            matrix.operation("raw-template"),
            '{"requestId":"fresh-template"}',
            "synthetic raw-template proof",
            require_request_scaffold,
        )
    assert raw_payload["requestId"] == "fresh-template"
    assert "raw-template" in session.successful_operations
    try:
        with activate_field_matrix(FieldMatrixSession(matrix, bindings)):
            validate_and_record_raw_json_operation(
                matrix.operation("raw-template"),
                '{"status":"ok"}',
                "synthetic raw-template proof",
                require_request_scaffold,
            )
    except ReleaseSmokeFailure as exc:
        assert "request scaffold" in str(exc)
        return
    raise AssertionError("field-matrix accepted a raw template without its declared scaffold")


def assert_artifact_requires_explicit_verification(
    matrix: CapabilityMatrix,
    bindings: dict[str, ScenarioBinding],
) -> None:
    """Keep each advertised PDF descriptor independent until its artifact is validated."""
    session = FieldMatrixSession(matrix, bindings)
    record_all_stdout(session)
    record_verified_append(session)
    first_artifact, second_artifact = report_artifacts(matrix)
    try:
        session.assert_complete()
    except ReleaseSmokeFailure as exc:
        assert f"missing verified artifacts: report[pdf via {first_artifact.option}]" in str(exc)
        assert f"report[pdf via {second_artifact.option}]" in str(exc)
    else:
        raise AssertionError(
            "field-matrix accepted an artifact option without artifact verification"
        )
    session.record_verified_artifact("report", first_artifact)
    try:
        session.assert_complete()
    except ReleaseSmokeFailure as exc:
        assert f"missing verified artifacts: report[pdf via {second_artifact.option}]" in str(exc)
    else:
        raise AssertionError("field-matrix let one PDF descriptor discharge a different PDF option")
    session.record_verified_artifact("report", second_artifact)
    session.assert_complete()


def assert_replayed_mutation_cannot_discharge_append(
    matrix: CapabilityMatrix,
    bindings: dict[str, ScenarioBinding],
) -> None:
    """A successful replay cannot satisfy the separate new-append evidence obligation."""
    session = FieldMatrixSession(matrix, bindings)
    record_all_stdout(session)
    for artifact in report_artifacts(matrix):
        session.record_verified_artifact("report", artifact)
    try:
        session.record_new_attestation_append(
            "mutable-write",
            {"payload": {"attestationCommit": None}},
            before_head=head("6", "b"),
            after_head=head("7", "a", previous_head_character="b"),
        )
    except ReleaseSmokeFailure as exc:
        assert "did not append a new attestation operation" in str(exc)
    else:
        raise AssertionError("field-matrix allowed replay/null attestationCommit append evidence")
    try:
        session.assert_complete()
    except ReleaseSmokeFailure as exc:
        assert "missing newly appended mutable operations: mutable-write" in str(exc)
        return
    raise AssertionError("field-matrix accepted replay/null evidence for a mutable operation")
