"""One canonical proof that the credited stdout is the response just validated."""

from __future__ import annotations

from ..models import ReleaseSmokeConfig
from ..support import require
from .capabilities import OperationCapability
from .invocation import validate_and_record_output_mode


def record_proven_output_mode(
    operation: OperationCapability,
    output_mode: str,
    output: str,
    config: ReleaseSmokeConfig,
    label: str,
) -> None:
    """Credit exactly the stdout whose operation-specific facts were proven."""

    def assert_proven_stdout(checked_mode: str, checked_output: str) -> None:
        require(
            checked_mode == output_mode and checked_output == output,
            f"{config.label} {label} attempted to credit output other than the proven "
            f"{operation.operation_id} response",
        )

    validate_and_record_output_mode(
        operation,
        output_mode,
        output,
        f"{config.label} {label}",
        assert_proven_stdout,
    )
