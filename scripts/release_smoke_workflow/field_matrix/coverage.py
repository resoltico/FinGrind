"""Coverage accounting for one live release-smoke capability matrix."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

from ..models import ReleaseSmokeFailure
from ..support import require
from .attestation_append_evidence import assert_new_attestation_append
from .capabilities import ArtifactCapability, CapabilityMatrix
from .coverage_completion import (
    artifact_label,
    assert_field_matrix_complete,
    assert_scenario_matrix_alignment,
)
from .scenario_matrix import ScenarioBinding

if TYPE_CHECKING:
    from ..attestation_head_checks import VerifiedAttestationHead


@dataclass
class FieldMatrixSession:
    """Records evidence that every fact advertised by the live binary was exercised.

    One invocation is not interchangeable with another: success, output-mode,
    artifact, and new-attestation-append evidence are deliberately independent.
    That makes a replayed write unable to satisfy a fresh mutable-operation test
    and makes an artifact option insufficient without checking the artifact.
    """

    capabilities: CapabilityMatrix
    scenario_matrix: Mapping[str, ScenarioBinding]
    successful_operations: set[str] = field(default_factory=set)
    successful_modes: set[tuple[str, str]] = field(default_factory=set)
    verified_artifacts: set[tuple[str, ArtifactCapability]] = field(default_factory=set)
    new_attestation_appends: set[str] = field(default_factory=set)

    def __post_init__(self) -> None:
        assert_scenario_matrix_alignment(self.capabilities, self.scenario_matrix)

    def record_success(self, operation_id: str, output_mode: str | None) -> None:
        """Record a command invocation that exited successfully with checked stdout."""
        operation = self.capabilities.operation(operation_id)
        if output_mode is not None:
            require(
                output_mode in operation.output_modes,
                f"field-matrix attempted unadvertised mode {output_mode} for {operation_id}",
            )
            self.successful_modes.add((operation_id, output_mode))
        self.successful_operations.add(operation_id)

    def record_verified_artifact(
        self,
        operation_id: str,
        artifact: ArtifactCapability,
    ) -> None:
        """Record one exact advertised artifact after semantic validation.

        An artifact format is not an identity: two PDF contracts with distinct
        selecting options are two separate release-smoke obligations.
        """
        operation = self.capabilities.operation(operation_id)
        require(
            isinstance(artifact, ArtifactCapability),
            f"field-matrix attempted a malformed artifact descriptor for {operation_id}",
        )
        require(
            artifact in operation.artifact_outputs,
            f"field-matrix attempted unadvertised artifact {artifact_label(artifact)} "
            f"for {operation_id}",
        )
        self.verified_artifacts.add((operation_id, artifact))

    def record_new_attestation_append(
        self,
        operation_id: str,
        envelope: Mapping[str, Any],
        *,
        before_head: VerifiedAttestationHead | None,
        after_head: VerifiedAttestationHead,
    ) -> None:
        """Record one append whose response is bound to an observed head advance.

        Callers must observe ``before_head`` and ``after_head`` with
        ``verified_attestation_head`` immediately around the mutation.  Genesis
        is the sole exception: it has no pre-existing book head and therefore
        passes ``before_head=None`` with verified order zero afterward.
        """
        assert_new_attestation_append(
            self._binding(operation_id),
            operation_id,
            envelope,
            before_head=before_head,
            after_head=after_head,
        )
        self.new_attestation_appends.add(operation_id)

    def assert_complete(self) -> None:
        """Fail closed when any advertised capability fact remains unexercised."""
        assert_field_matrix_complete(
            self.capabilities,
            self.scenario_matrix,
            self.successful_operations,
            self.successful_modes,
            self.verified_artifacts,
            self.new_attestation_appends,
        )

    def _binding(self, operation_id: str) -> ScenarioBinding:
        self.capabilities.operation(operation_id)
        try:
            return self.scenario_matrix[operation_id]
        except KeyError as exc:
            raise ReleaseSmokeFailure(
                f"field-matrix has no scenario binding for {operation_id}"
            ) from exc
