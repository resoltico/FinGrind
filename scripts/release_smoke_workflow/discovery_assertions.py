from __future__ import annotations

from typing import Any

from .discovery_runtime_assertions import (
    assert_discovery_surface,
    assert_loaded_sqlite_runtime,
)
from .models import ReleaseSmokeConfig
from .support import (
    require,
    require_string,
    required_list,
    required_mapping,
)


def assert_discovery_payloads(
    config: ReleaseSmokeConfig,
    contract: dict[str, object],
    capabilities_payload: dict[str, Any],
    environment_payload: dict[str, Any],
) -> dict[str, int]:
    payload = required_mapping(capabilities_payload, "payload")
    environment = required_mapping(environment_payload, "payload")
    full_contract = required_mapping(payload, "fullContract")
    distribution = required_mapping(environment, "distribution")
    storage = required_mapping(environment, "storage")
    sqlite = required_mapping(environment, "sqlite")
    runtime = required_mapping(sqlite, "runtime")
    request_input = required_mapping(payload, "requestInput")
    commands = required_mapping(payload, "commands")
    response_model = required_mapping(full_contract, "responseModel")

    error_descriptor_exit_codes = _error_descriptor_exit_codes(config, response_model)
    query_commands_by_name = _query_commands_by_name(commands)
    runtime_surface = required_mapping(contract, "runtimeSurface")
    protected_book_format = required_mapping(contract, "protectedBookFormat")
    public_distribution = required_mapping(contract, "publicDistribution")
    managed_sqlite = required_mapping(contract, "managedSqlite")
    operation_ids = required_mapping(contract, "operationIds")

    assert_discovery_surface(
        config,
        payload,
        distribution,
        storage,
        sqlite,
        runtime_surface,
        protected_book_format,
        public_distribution,
        request_input,
    )
    _assert_query_contracts(
        config, query_commands_by_name, operation_ids, error_descriptor_exit_codes
    )
    assert_loaded_sqlite_runtime(config, sqlite, runtime, managed_sqlite, runtime_surface)
    return error_descriptor_exit_codes


def _error_descriptor_exit_codes(
    config: ReleaseSmokeConfig,
    response_model: dict[str, Any],
) -> dict[str, int]:
    error_descriptors = required_list(response_model, "errorDescriptors")
    exit_codes: dict[str, int] = {}
    for descriptor in error_descriptors:
        if not isinstance(descriptor, dict):
            continue
        code = require_string(descriptor, "code")
        exit_code = descriptor.get("exitCode")
        require(
            isinstance(exit_code, int) and not isinstance(exit_code, bool) and exit_code >= 0,
            f"{config.label} capabilities output did not publish one non-negative exitCode for {code}",
        )
        exit_codes[code] = exit_code
    return exit_codes


def _query_commands_by_name(commands: dict[str, Any]) -> dict[str, dict[str, Any]]:
    query_commands = required_list(commands, "query")
    return {
        require_string(command, "name"): command
        for command in query_commands
        if isinstance(command, dict)
    }


def _assert_query_contracts(
    config: ReleaseSmokeConfig,
    query_commands_by_name: dict[str, dict[str, Any]],
    operation_ids: dict[str, Any],
    error_descriptor_exit_codes: dict[str, int],
) -> None:
    for operation_key in ("trialBalance", "accountLedger", "periodSummary"):
        command = required_mapping(
            query_commands_by_name, require_string(operation_ids, operation_key)
        )
        require(
            required_list(command, "outputModes") == ["json", "text", "csv"],
            f"{config.label} {require_string(operation_ids, operation_key)} did not report json,text,csv stdout modes",
        )
        if operation_key == "trialBalance":
            artifact_outputs = required_list(command, "artifactOutputs")
            require(
                len(artifact_outputs) == 1 and isinstance(artifact_outputs[0], dict),
                f"{config.label} trial-balance did not report the canonical PDF artifact contract",
            )
            artifact = artifact_outputs[0]
            require(
                require_string(artifact, "format") == "pdf"
                and require_string(artifact, "option") == "--pdf-out <path>",
                f"{config.label} trial-balance did not report the canonical PDF artifact contract",
            )
    for error_code in (
        "invalid-page-cursor",
        "interactive-prompt-unavailable",
        "protected-book-verification-failed",
    ):
        require(
            error_code in error_descriptor_exit_codes,
            f"{config.label} capabilities output did not report the {error_code} error descriptor",
        )
