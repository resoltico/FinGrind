"""Advertised-output-mode selection and safe world-path validation."""

from __future__ import annotations

from ..support import require
from .administrative_constants import _JSON_MODE, _MODE_SEGMENT
from .capabilities import OperationCapability


def _supported_mode(operation: OperationCapability, preferred_mode: str) -> str:
    require(
        operation.output_modes,
        f"field-matrix {operation.operation_id} advertised no output modes",
    )
    return preferred_mode if preferred_mode in operation.output_modes else operation.output_modes[0]


def _required_json_mode(operation: OperationCapability) -> str:
    require(
        _JSON_MODE in operation.output_modes,
        f"field-matrix {operation.operation_id} must advertise JSON for semantic verification",
    )
    return _JSON_MODE


def _modes_for(*operations: OperationCapability) -> tuple[str, ...]:
    modes = tuple(
        dict.fromkeys(mode for operation in operations for mode in operation.output_modes)
    )
    require(modes, "field-matrix administrative operation group advertised no output modes")
    for output_mode in modes:
        _require_mode_segment(output_mode)
    return modes


def _require_mode_segment(output_mode: str) -> None:
    require(
        _MODE_SEGMENT.fullmatch(output_mode) is not None,
        f"field-matrix administrative output mode is unsafe for a world path: {output_mode!r}",
    )
