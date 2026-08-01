"""Synthetic contracts for native output validation and coverage-credit ordering."""

from __future__ import annotations

import inspect
from collections.abc import Callable

from ..cli import run_cli, run_cli_allow_failure
from ..models import ReleaseSmokeFailure
from .capabilities import CapabilityMatrix, OperationCapability
from .context import activate_field_matrix
from .coverage import FieldMatrixSession
from .invocation import assert_native_output_mode, validate_and_record_output_mode
from .scenario_matrix import ScenarioBinding


def assert_invocation_contracts(
    matrix: CapabilityMatrix,
    bindings: dict[str, ScenarioBinding],
) -> None:
    """Prove native documents and scenario facts are both required before credit."""
    assert_native_output_modes(matrix, bindings)
    assert_cli_runners_never_record_success()


def assert_native_output_modes(
    matrix: CapabilityMatrix,
    bindings: dict[str, ScenarioBinding],
) -> None:
    """Reject shape-valid but wrong native output documents and missing scenario facts."""
    report = matrix.operation("report")
    assert_native_output_mode(report, "json", '{"status":"ok"}', "synthetic mode proof")
    assert_native_output_mode(report, "text", "Report\ncash\n", "synthetic mode proof")
    assert_native_output_mode(
        report,
        "csv",
        "accountCode,netAmount\ncash,100\n",
        "synthetic mode proof",
    )
    require_native_mode_rejection(report, "text", '{"status":"ok"}', "emitted a JSON document")
    require_native_mode_rejection(
        report,
        "text",
        "accountCode,netAmount\ncash,100\n",
        "emitted a CSV table",
    )
    require_native_mode_rejection(
        report,
        "csv",
        "Default text\nstill default\n",
        "did not emit a multi-column CSV table",
    )
    session = FieldMatrixSession(matrix, bindings)
    with activate_field_matrix(session):
        validate_and_record_output_mode(
            report,
            "text",
            "Report\ncash\n",
            "synthetic semantic mode proof",
            require_text_token("cash"),
        )
    assert ("report", "text") in session.successful_modes
    require_mode_fact_rejection(
        session,
        report,
        "text",
        "regressed\n",
        require_text_token("cash"),
        "did not retain required text fact",
    )
    require_mode_fact_rejection(
        session,
        report,
        "csv",
        "accountCode,netAmount\nwrong-account,100\n",
        require_csv_cell("cash"),
        "did not retain required CSV fact",
    )


def assert_cli_runners_never_record_success() -> None:
    """Keep generic process runners separate from scenario-owned evidence credit."""
    assert "record_validated_operation" not in inspect.getsource(run_cli)
    assert "record_validated_operation" not in inspect.getsource(run_cli_allow_failure)


def require_native_mode_rejection(
    operation: OperationCapability,
    output_mode: str,
    output: str,
    expected_message: str,
) -> None:
    """Require a requested mode not to masquerade as another native document type."""
    try:
        assert_native_output_mode(operation, output_mode, output, "synthetic mode proof")
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError(f"field-matrix accepted a non-native {output_mode} representation")


def require_mode_fact_rejection(
    session: FieldMatrixSession,
    operation: OperationCapability,
    output_mode: str,
    output: str,
    assertion: Callable[[str, str], None],
    expected_message: str,
) -> None:
    """Require scenario-local semantic failure before a mode can receive a second credit."""
    try:
        with activate_field_matrix(session):
            validate_and_record_output_mode(
                operation,
                output_mode,
                output,
                "synthetic semantic mode proof",
                assertion,
            )
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError("field-matrix credited shape-valid output without its scenario fact")


def require_text_token(expected: str):
    """Return one output assertion requiring an exact text token."""

    def assertion(_output_mode: str, output: str) -> None:
        if expected not in output:
            raise ReleaseSmokeFailure(
                f"synthetic semantic mode proof did not retain required text fact {expected!r}"
            )

    return assertion


def require_csv_cell(expected: str):
    """Return one output assertion requiring an exact CSV cell."""

    def assertion(_output_mode: str, output: str) -> None:
        rows = tuple(line.split(",") for line in output.strip().splitlines())
        if not any(expected == cell for row in rows[1:] for cell in row):
            raise ReleaseSmokeFailure(
                f"synthetic semantic mode proof did not retain required CSV fact {expected!r}"
            )

    return assertion
