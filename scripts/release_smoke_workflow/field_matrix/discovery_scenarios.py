"""All-mode fresh-bundle coverage for discovery and raw-template commands."""

from __future__ import annotations

from ..discovery_assertions import machine_contract_protocol_version
from ..models import ReleaseSmokeConfig
from .capabilities import CapabilityMatrix
from .discovery_output_assertions import discovery_mode_assertion, environment_sqlite_version
from .discovery_template_assertions import raw_template_assertion
from .invocation import invoke_all_advertised_modes, invoke_raw_json_operation

_DISCOVERY_OPERATIONS = (
    "help",
    "version",
    "capabilities",
    "environment",
)
_RAW_TEMPLATE_OPERATIONS = ("print-request-template", "print-plan-template")


def verify_discovery_matrix(config: ReleaseSmokeConfig, matrix: CapabilityMatrix) -> None:
    """Exercise every live discovery stdout mode and each raw template command."""
    print(f"{config.label}: verifying all discovery capability modes")
    protocol_version = machine_contract_protocol_version(config.repo_root)
    loaded_sqlite_version = environment_sqlite_version(config)
    for operation_id in _DISCOVERY_OPERATIONS:
        operation = matrix.operation(operation_id)
        invoke_all_advertised_modes(
            config,
            operation,
            lambda output_mode, operation_id=operation_id: _discovery_arguments(
                operation_id, output_mode
            ),
            "discovery capability matrix",
            discovery_mode_assertion(
                config,
                operation_id,
                protocol_version,
                loaded_sqlite_version,
            ),
        )
    for operation_id in _RAW_TEMPLATE_OPERATIONS:
        invoke_raw_json_operation(
            config,
            matrix.operation(operation_id),
            purpose="raw discovery-template matrix",
            assert_semantics=raw_template_assertion(config, operation_id),
        )


def _discovery_arguments(operation_id: str, output_mode: str) -> tuple[str, ...]:
    if operation_id == "capabilities" and output_mode == "json":
        return ("--detail", "full")
    return ()
