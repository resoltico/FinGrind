"""Small, explicit building blocks for live all-mode command invocation."""

from __future__ import annotations

import csv
import io
from collections.abc import Callable
from typing import Any

from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure
from ..support import parse_json_output, require
from .capabilities import OperationCapability
from .native_text_output_assertions import assert_native_text_document

ModeArguments = Callable[[str], tuple[str, ...]]
ModeSemanticAssertion = Callable[[str, str], None]
RawSemanticAssertion = Callable[[dict[str, Any]], None]


def invoke_all_advertised_modes(
    config: ReleaseSmokeConfig,
    operation: OperationCapability,
    mode_arguments: ModeArguments,
    purpose: str,
    assert_mode_semantics: ModeSemanticAssertion,
) -> dict[str, str]:
    """Execute every output mode published by one live operation descriptor.

    The caller supplies only operation-specific input arguments.  Output modes
    themselves always come from the live capability descriptor, so a newly
    advertised stdout mode becomes a field-test obligation immediately.
    """
    require(
        operation.output_modes,
        f"field-matrix {purpose} expected {operation.operation_id} to advertise stdout modes",
    )
    from ..cli import run_cli

    outputs: dict[str, str] = {}
    for output_mode in operation.output_modes:
        output = run_cli(
            config,
            operation.operation_id,
            *mode_arguments(output_mode),
            "--output",
            output_mode,
        )
        validate_and_record_output_mode(
            operation,
            output_mode,
            output,
            purpose,
            assert_mode_semantics,
        )
        outputs[output_mode] = output
    return outputs


def invoke_raw_json_operation(
    config: ReleaseSmokeConfig,
    operation: OperationCapability,
    *arguments: str,
    purpose: str,
    assert_semantics: RawSemanticAssertion,
) -> dict[str, Any]:
    """Execute a raw JSON operation without inventing an unsupported output mode."""
    require(
        not operation.output_modes,
        f"field-matrix {purpose} expected {operation.operation_id} to be raw-output only",
    )
    from ..cli import run_cli

    output = run_cli(config, operation.operation_id, *arguments)
    return validate_and_record_raw_json_operation(
        operation,
        output,
        purpose,
        assert_semantics,
    )


def validate_and_record_raw_json_operation(
    operation: OperationCapability,
    output: str,
    purpose: str,
    assert_semantics: RawSemanticAssertion,
) -> dict[str, Any]:
    """Validate and credit one intentionally bare JSON operation.

    Unlike executable JSON-envelope commands, raw templates deliberately emit
    their document scaffold directly.  The supplied assertion owns that
    scaffold's schema and is required before the operation can be credited.
    """
    require(
        not operation.output_modes,
        f"field-matrix {purpose} expected {operation.operation_id} to be raw-output only",
    )
    raw_payload = parse_json_output(output, f"field-matrix {purpose} did not emit valid raw JSON")
    # Templates intentionally publish their raw scaffold rather than the
    # JSON-envelope protocol used by executable commands.  Their caller owns
    # the exact schema assertion, which is stronger than fabricating a status
    # field that the public operation deliberately does not emit.
    assert_semantics(raw_payload)
    _record_validated_operation(operation.operation_id, None)
    return raw_payload


def validate_and_record_output_mode(
    operation: OperationCapability,
    output_mode: str,
    output: str,
    purpose: str,
    assert_mode_semantics: ModeSemanticAssertion,
) -> None:
    """Credit one mode only after structural and scenario-specific proof.

    Generic process success and output shape are necessary but insufficient:
    a text or CSV response must still demonstrate the selected command's
    retained fact.  Both all-mode loops and bespoke fresh-world writers use
    this one seam so the coverage ledger cannot be advanced early.
    """
    assert_native_output_mode(operation, output_mode, output, purpose)
    assert_mode_semantics(output_mode, output)
    _record_validated_operation(operation.operation_id, output_mode)


def _record_validated_operation(operation_id: str, output_mode: str | None) -> None:
    # Import lazily: context owns recorder lifetime while this module owns
    # representation validation, and a top-level import would create a cycle.
    from .context import record_validated_operation

    record_validated_operation(operation_id, output_mode)


def assert_native_output_mode(
    operation: OperationCapability,
    output_mode: str,
    output: str,
    purpose: str,
) -> None:
    """Reject a requested mode that actually emitted another representation."""
    require(
        bool(output.strip()),
        f"field-matrix {purpose} {operation.operation_id}[{output_mode}] emitted empty stdout",
    )
    require(
        output_mode in operation.output_modes,
        f"field-matrix {purpose} requested unadvertised mode {output_mode} "
        f"for {operation.operation_id}",
    )
    if output_mode == "text":
        assert_native_text_document(operation, output, purpose, csv_table_rows)
        return
    if output_mode == "csv":
        csv_table_rows(
            output,
            f"field-matrix {purpose} {operation.operation_id}[csv]",
        )
        return
    if output_mode == "json":
        envelope = parse_json_output(
            output,
            f"field-matrix {purpose} {operation.operation_id}[json] did not emit valid JSON",
        )
        require(
            envelope.get("status") == "ok",
            f"field-matrix {purpose} {operation.operation_id}[json] did not report ok status",
        )
        return
    raise ReleaseSmokeFailure(
        f"field-matrix has no native output contract for advertised mode {output_mode} "
        f"of {operation.operation_id}"
    )


def csv_table_rows(output: str, purpose: str) -> tuple[tuple[str, ...], ...]:
    """Parse and validate one non-empty, rectangular CSV result table."""
    try:
        parsed_rows = tuple(
            tuple(row)
            for row in csv.reader(io.StringIO(output, newline=""), strict=True)
            if any(cell.strip() for cell in row)
        )
    except csv.Error as exc:
        raise ReleaseSmokeFailure(f"{purpose} did not emit parseable CSV") from exc
    require(parsed_rows, f"{purpose} did not emit a CSV header row")
    header = parsed_rows[0]
    require(
        bool(header) and all(cell.strip() for cell in header),
        f"{purpose} emitted an invalid CSV header row",
    )
    require(
        len(header) >= 2,
        f"{purpose} did not emit a multi-column CSV table",
    )
    require(
        len(header) == len({cell.strip() for cell in header}),
        f"{purpose} emitted duplicate CSV header names",
    )
    data_rows = parsed_rows[1:]
    require(data_rows, f"{purpose} emitted no CSV data rows")
    require(
        all(len(row) == len(header) for row in data_rows),
        f"{purpose} emitted non-rectangular CSV rows",
    )
    return parsed_rows
