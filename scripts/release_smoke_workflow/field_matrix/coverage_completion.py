"""Fail-closed completion accounting for the live field-matrix capability surface."""

from __future__ import annotations

from collections.abc import Mapping

from ..support import require
from .capabilities import ArtifactCapability, CapabilityMatrix
from .scenario_matrix import ScenarioBinding


def assert_scenario_matrix_alignment(
    capabilities: CapabilityMatrix,
    scenario_matrix: Mapping[str, ScenarioBinding],
) -> None:
    """Require an exact one-to-one routing from advertised operations to scenarios."""
    advertised = set(capabilities.operations)
    registered = set(scenario_matrix)
    missing_bindings = sorted(advertised - registered)
    stale_bindings = sorted(registered - advertised)
    require(
        not missing_bindings and not stale_bindings,
        scenario_matrix_mismatch_message(missing_bindings, stale_bindings),
    )


def assert_field_matrix_complete(
    capabilities: CapabilityMatrix,
    scenario_matrix: Mapping[str, ScenarioBinding],
    successful_operations: set[str],
    successful_modes: set[tuple[str, str]],
    verified_artifacts: set[tuple[str, ArtifactCapability]],
    new_attestation_appends: set[str],
) -> None:
    """Reject a run before every independently advertised evidence obligation is met."""
    missing_operations = sorted(set(capabilities.operations) - successful_operations)
    missing_modes = sorted(
        (operation.operation_id, output_mode)
        for operation in capabilities.operations.values()
        for output_mode in operation.output_modes
        if (operation.operation_id, output_mode) not in successful_modes
    )
    missing_artifacts = sorted(
        (
            (operation.operation_id, artifact)
            for operation in capabilities.operations.values()
            for artifact in operation.artifact_outputs
            if (operation.operation_id, artifact) not in verified_artifacts
        ),
        key=lambda evidence: (evidence[0], evidence[1].format, evidence[1].option),
    )
    missing_appends = sorted(
        operation_id
        for operation_id, binding in scenario_matrix.items()
        if binding.requires_new_attestation_append and operation_id not in new_attestation_appends
    )
    require(
        not missing_operations
        and not missing_modes
        and not missing_artifacts
        and not missing_appends,
        incomplete_matrix_message(
            missing_operations,
            missing_modes,
            missing_artifacts,
            missing_appends,
        ),
    )


def scenario_matrix_mismatch_message(missing_bindings: list[str], stale_bindings: list[str]) -> str:
    """Describe the two ways a scenario route can diverge from discovery."""
    parts = ["field-matrix operation-to-scenario table differs from live capabilities"]
    if missing_bindings:
        parts.append("missing scenario bindings: " + ", ".join(missing_bindings))
    if stale_bindings:
        parts.append("stale scenario bindings: " + ", ".join(stale_bindings))
    return "; ".join(parts)


def incomplete_matrix_message(
    missing_operations: list[str],
    missing_modes: list[tuple[str, str]],
    missing_artifacts: list[tuple[str, ArtifactCapability]],
    missing_appends: list[str],
) -> str:
    """Describe every unproven obligation without concealing a second failure."""
    parts = ["field-matrix did not fully exercise the live advertised capability surface"]
    if missing_operations:
        parts.append("missing successful operations: " + ", ".join(missing_operations))
    if missing_modes:
        parts.append(
            "missing successful output modes: "
            + ", ".join(f"{operation_id}[{mode}]" for operation_id, mode in missing_modes)
        )
    if missing_artifacts:
        parts.append(
            "missing verified artifacts: "
            + ", ".join(
                f"{operation_id}[{artifact_label(artifact)}]"
                for operation_id, artifact in missing_artifacts
            )
        )
    if missing_appends:
        parts.append("missing newly appended mutable operations: " + ", ".join(missing_appends))
    return "; ".join(parts)


def artifact_label(artifact: ArtifactCapability) -> str:
    """Render the descriptor identity used in field-matrix diagnostics."""
    return f"{artifact.format} via {artifact.option}"
