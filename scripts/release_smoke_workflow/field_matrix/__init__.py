"""Live-capability coverage matrix for fresh FinGrind release-smoke runs."""

from .capabilities import CapabilityMatrix, OperationCapability
from .context import (
    activate_field_matrix,
    current_recorder,
    record_new_attestation_append,
    record_verified_artifact,
)
from .coverage import FieldMatrixSession
from .scenario_matrix import SCENARIO_MATRIX

__all__ = [
    "SCENARIO_MATRIX",
    "CapabilityMatrix",
    "FieldMatrixSession",
    "OperationCapability",
    "activate_field_matrix",
    "current_recorder",
    "record_new_attestation_append",
    "record_verified_artifact",
]
