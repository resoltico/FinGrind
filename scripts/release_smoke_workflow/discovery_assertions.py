from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .attestation_diagnostic_catalog import (
    AttestationDiagnostic,
    admission_diagnostics,
    verification_diagnostics,
)
from .discovery_plan_assertions import assert_plan_attestation_outcome_contract
from .discovery_query_assertions import (
    assert_query_contracts,
    error_descriptor_exit_codes,
    query_commands_by_name,
)
from .discovery_runtime_assertions import (
    assert_discovery_surface,
    assert_loaded_sqlite_runtime,
)
from .field_matrix.capabilities import CapabilityMatrix
from .models import ReleaseSmokeConfig
from .support import (
    require,
    require_string,
    required_mapping,
)

_MACHINE_CONTRACT_PROTOCOL_VERSION = re.compile(
    r'^\s*private static final String PROTOCOL_VERSION = "([0-9]+)";$', re.MULTILINE
)


@dataclass(frozen=True)
class RuntimeContractFacts:
    error_exit_codes: dict[str, int]
    attestation_admission_diagnostics: dict[str, dict[str, AttestationDiagnostic]]
    attestation_verification_diagnostics: dict[str, dict[str, AttestationDiagnostic]]
    capability_matrix: CapabilityMatrix
    protected_book_format: dict[str, Any]


def assert_discovery_payloads(
    config: ReleaseSmokeConfig,
    contract: dict[str, object],
    capabilities_payload: dict[str, Any],
    environment_payload: dict[str, Any],
) -> RuntimeContractFacts:
    payload = required_mapping(capabilities_payload, "payload")
    assert_machine_contract_protocol_version(
        payload,
        machine_contract_protocol_version(config.repo_root),
        config.label,
    )
    environment = required_mapping(environment_payload, "payload")
    full_contract = required_mapping(payload, "fullContract")
    assert_plan_attestation_outcome_contract(config, full_contract)
    runtime_surface_payload = required_mapping(environment, "runtime")
    publication_surface = required_mapping(environment, "publication")
    storage = required_mapping(environment, "storage")
    sqlite = required_mapping(environment, "sqlite")
    runtime = required_mapping(sqlite, "runtime")
    request_input = required_mapping(payload, "requestInput")
    commands = required_mapping(payload, "commands")
    response_model = required_mapping(full_contract, "responseModel")

    published_error_exit_codes = error_descriptor_exit_codes(config, response_model)
    admission_diagnostic_catalog = admission_diagnostics(response_model, config.label)
    verification_diagnostic_catalog = verification_diagnostics(response_model, config.label)
    queries_by_name = query_commands_by_name(commands)
    runtime_surface = required_mapping(contract, "runtimeSurface")
    protected_book_format = required_mapping(contract, "protectedBookFormat")
    public_distribution = required_mapping(contract, "publicDistribution")
    managed_sqlite = required_mapping(contract, "managedSqlite")
    operation_ids = required_mapping(contract, "operationIds")

    assert_discovery_surface(
        config,
        payload,
        runtime_surface_payload,
        publication_surface,
        storage,
        sqlite,
        runtime_surface,
        protected_book_format,
        public_distribution,
        request_input,
    )
    assert_query_contracts(config, queries_by_name, operation_ids, published_error_exit_codes)
    assert_loaded_sqlite_runtime(config, sqlite, runtime, managed_sqlite, runtime_surface)
    return RuntimeContractFacts(
        published_error_exit_codes,
        admission_diagnostic_catalog,
        verification_diagnostic_catalog,
        CapabilityMatrix.from_full_capabilities(capabilities_payload),
        protected_book_format,
    )


def machine_contract_protocol_version(repo_root: Path) -> str:
    """Read the sole machine-protocol version owner from the Java contract source."""
    source_path = (
        repo_root
        / "contract/src/main/java/dev/erst/fingrind/contract/discovery/MachineContract.java"
    )
    require(
        source_path.is_file(),
        f"release-smoke source contract is missing {source_path}",
    )
    versions = _MACHINE_CONTRACT_PROTOCOL_VERSION.findall(source_path.read_text(encoding="utf-8"))
    require(
        len(versions) == 1,
        "release-smoke source contract did not declare one MachineContract protocol version",
    )
    return versions[0]


def assert_machine_contract_protocol_version(
    payload: dict[str, Any],
    expected_protocol_version: str,
    label: str,
) -> None:
    """Require a discovery payload to identify the exact live protocol line."""
    require(
        require_string(payload, "protocolVersion") == expected_protocol_version,
        f"{label} capabilities output did not report MachineContract protocol version "
        f"{expected_protocol_version}",
    )
