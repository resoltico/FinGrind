from __future__ import annotations

from .assertions import assert_discovery_payloads
from .cli import run_cli
from .models import ReleaseSmokeConfig
from .support import parse_json_output, payload_field, project_version, require, require_match


def verify_version_command(config: ReleaseSmokeConfig, operation_ids: dict[str, str]) -> None:
    print(f"{config.label}: verifying version command")
    version_payload = parse_json_output(
        run_cli(config, operation_ids["version"], "--output", "json"),
        f"{config.label} version output was not valid JSON",
    )
    require(
        version_payload.get("status") == "ok",
        f"{config.label} version output did not report ok status",
    )
    require(
        payload_field(version_payload, "payload", "application") == "FinGrind",
        f"{config.label} version output did not include application name",
    )
    require(
        payload_field(version_payload, "payload", "version") == project_version(config.repo_root),
        f"{config.label} version output did not report the expected version",
    )


def verify_runtime_contract(
    config: ReleaseSmokeConfig,
    contract: dict[str, object],
    operation_ids: dict[str, str],
) -> dict[str, int]:
    print(f"{config.label}: verifying runtime contract")
    capabilities_payload = parse_json_output(
        run_cli(config, operation_ids["capabilities"], "--output", "json", "--detail", "full"),
        f"{config.label} capabilities output was not valid JSON",
    )
    environment_payload = parse_json_output(
        run_cli(config, operation_ids["environment"], "--output", "json"),
        f"{config.label} environment output was not valid JSON",
    )
    return assert_discovery_payloads(config, contract, capabilities_payload, environment_payload)


def verify_help_and_template_surfaces(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    print(f"{config.label}: verifying help and template discovery surfaces")
    help_text = run_cli(config, operation_ids["help"], "--output", "text")
    require_match(
        help_text,
        r"First Successful Run",
        f"{config.label} help text did not publish the front-door first-success section",
    )
    require_match(
        help_text,
        r"Generate one key file",
        f"{config.label} help text did not lead with key generation as the first operator action",
    )
    require_match(
        help_text,
        r"Open one protected book",
        f"{config.label} help text did not publish the protected-book opening path",
    )
    request_template = parse_json_output(
        run_cli(config, operation_ids["printRequestTemplate"]),
        f"{config.label} print-request-template output was not valid JSON",
    )
    require(
        payload_field(request_template, "effectiveDate") == "2026-01-15",
        f"{config.label} print-request-template did not publish the canonical effectiveDate scaffold",
    )
    require(
        payload_field(request_template, "provenance", "actorType") == "PERSON",
        f"{config.label} print-request-template did not publish the canonical provenance scaffold",
    )
    plan_template = parse_json_output(
        run_cli(config, operation_ids["printPlanTemplate"]),
        f"{config.label} print-plan-template output was not valid JSON",
    )
    require(
        payload_field(plan_template, "planId") == "plan-1",
        f"{config.label} print-plan-template did not publish the canonical planId scaffold",
    )
    require(
        payload_field(plan_template, "steps", 0, "stepId") == "initialize-book",
        f"{config.label} print-plan-template did not publish the canonical initialize-book step",
    )
