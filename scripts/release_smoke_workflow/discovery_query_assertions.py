from __future__ import annotations

from typing import Any

from .models import ReleaseSmokeConfig
from .support import require, require_string, required_list, required_mapping


def error_descriptor_exit_codes(
    config: ReleaseSmokeConfig,
    response_model: dict[str, Any],
) -> dict[str, int]:
    error_descriptors = required_list(response_model, "errorDescriptors")
    exit_codes: dict[str, int] = {}
    for descriptor in error_descriptors:
        if not isinstance(descriptor, dict):
            continue
        code = require_string(descriptor, "code")
        category = require_string(descriptor, "category")
        exit_code = descriptor.get("exitCode")
        require(
            isinstance(exit_code, int) and not isinstance(exit_code, bool) and exit_code >= 0,
            f"{config.label} capabilities output did not publish one non-negative exitCode for {code}",
        )
        if code == "unsupported-output-selection":
            require(
                category == "unsupported-selection",
                f"{config.label} capabilities output did not classify {code} as unsupported-selection",
            )
        exit_codes[code] = exit_code
    return exit_codes


def query_commands_by_name(commands: dict[str, Any]) -> dict[str, dict[str, Any]]:
    query_commands = required_list(commands, "query")
    return {
        require_string(command, "name"): command
        for command in query_commands
        if isinstance(command, dict)
    }


def assert_query_contracts(
    config: ReleaseSmokeConfig,
    query_commands: dict[str, dict[str, Any]],
    operation_ids: dict[str, Any],
    error_exit_codes: dict[str, int],
) -> None:
    for operation_key in ("trialBalance", "accountLedger", "periodSummary"):
        command = required_mapping(query_commands, require_string(operation_ids, operation_key))
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
            error_code in error_exit_codes,
            f"{config.label} capabilities output did not report the {error_code} error descriptor",
        )
