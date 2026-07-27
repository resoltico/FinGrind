"""Typed view of the live full-capabilities command catalog.

The release-smoke matrix deliberately learns executable modes and artifact formats
from the launcher it is testing.  Static scenario routing is only the owner of
the fixture/workflow needed to exercise an operation; it is not a second source
of truth for the public capability surface.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from typing import Any

from ..models import ReleaseSmokeFailure
from ..support import require, require_string, required_list, required_mapping


@dataclass(frozen=True)
class ArtifactCapability:
    """One non-stdout artifact contract advertised by an operation."""

    format: str
    option: str

    @property
    def option_flag(self) -> str:
        """Return the command-line option token that selects this artifact."""
        return self.option.split(maxsplit=1)[0]


@dataclass(frozen=True)
class OperationCapability:
    """The live command facts needed to plan and verify matrix coverage."""

    operation_id: str
    display_label: str
    category: str
    output_modes: tuple[str, ...]
    artifact_outputs: tuple[ArtifactCapability, ...]


@dataclass(frozen=True)
class CapabilityMatrix:
    """Complete command catalog parsed from one `capabilities --detail full` result."""

    operations: Mapping[str, OperationCapability]

    @classmethod
    def from_full_capabilities(cls, envelope: Mapping[str, Any]) -> CapabilityMatrix:
        """Parse the command facts from one successful full discovery envelope."""
        require(
            envelope.get("status") == "ok",
            "field-matrix capabilities discovery did not report ok status",
        )
        payload = required_mapping(dict(envelope), "payload")
        require(
            require_string(payload, "detail") == "full",
            "field-matrix requires capabilities --detail full",
        )
        commands = required_mapping(payload, "commands")
        operations = tuple(_operations_from_categories(commands))
        operation_ids = [operation.operation_id for operation in operations]
        require(
            len(operation_ids) == len(set(operation_ids)),
            "field-matrix capabilities discovery published duplicate operation ids",
        )
        require(operation_ids, "field-matrix capabilities discovery published no operations")
        return cls({operation.operation_id: operation for operation in operations})

    def operation(self, operation_id: str) -> OperationCapability:
        try:
            return self.operations[operation_id]
        except KeyError as exc:
            raise ReleaseSmokeFailure(
                f"field-matrix received an invocation for unadvertised operation {operation_id}"
            ) from exc

    def operations_with_artifact(self, artifact_format: str) -> tuple[OperationCapability, ...]:
        return tuple(
            operation
            for operation in self.operations.values()
            if any(artifact.format == artifact_format for artifact in operation.artifact_outputs)
        )


def _operations_from_categories(
    categories: Mapping[str, Any],
) -> Iterable[OperationCapability]:
    for category, raw_operations in categories.items():
        require(
            isinstance(category, str) and category,
            "field-matrix capabilities discovery published an invalid command category",
        )
        require(
            isinstance(raw_operations, list),
            f"field-matrix capabilities discovery category {category} was not an array",
        )
        for raw_operation in raw_operations:
            require(
                isinstance(raw_operation, dict),
                f"field-matrix capabilities discovery category {category} contained a non-object command",
            )
            yield _operation_from_mapping(category, raw_operation)


def _operation_from_mapping(category: str, operation: dict[str, Any]) -> OperationCapability:
    operation_id = require_string(operation, "name")
    display_label = require_string(operation, "displayLabel")
    raw_output_modes = required_list(operation, "outputModes")
    output_modes = tuple(
        _required_non_blank_string(
            value,
            f"field-matrix operation {operation_id} published an invalid output mode",
        )
        for value in raw_output_modes
    )
    require(
        len(output_modes) == len(set(output_modes)),
        f"field-matrix operation {operation_id} published duplicate output modes",
    )
    raw_artifacts = required_list(operation, "artifactOutputs")
    artifacts = tuple(
        _artifact_from_mapping(operation_id, raw_artifact) for raw_artifact in raw_artifacts
    )
    require(
        len(artifacts) == len(set(artifacts)),
        f"field-matrix operation {operation_id} published duplicate artifact outputs",
    )
    return OperationCapability(operation_id, display_label, category, output_modes, artifacts)


def _artifact_from_mapping(operation_id: str, artifact: object) -> ArtifactCapability:
    require(
        isinstance(artifact, dict),
        f"field-matrix operation {operation_id} published a non-object artifact output",
    )
    if not isinstance(artifact, dict):
        raise TypeError("require must reject a non-object artifact")
    artifact_format = require_string(artifact, "format")
    option = require_string(artifact, "option")
    require(
        option.startswith("--") and " " in option,
        f"field-matrix operation {operation_id} published malformed artifact option {option!r}",
    )
    return ArtifactCapability(artifact_format, option)


def _required_non_blank_string(value: object, message: str) -> str:
    require(isinstance(value, str) and bool(value), message)
    if not isinstance(value, str) or not value:
        raise AssertionError("require must reject a non-string capability value")
    return value
