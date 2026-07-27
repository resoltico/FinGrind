from __future__ import annotations

from . import fixture_payloads
from .models import ReleaseSmokeConfig
from .support import parse_json_output, require, required_list, required_mapping


def successful_plan_payload(
    output: str,
    config: ReleaseSmokeConfig,
    purpose: str,
    expected_plan_id: str,
    expected_step_count: int,
) -> dict[str, object]:
    envelope = parse_json_output(
        output,
        f"{config.label} {purpose} output was not valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} {purpose} did not report ok status",
    )
    payload = required_mapping(envelope, "payload")
    require(
        payload.get("planId") == expected_plan_id,
        f"{config.label} {purpose} did not report its exact plan id",
    )
    require(
        payload.get("status") == "succeeded",
        f"{config.label} {purpose} did not report succeeded status",
    )
    require(
        payload.get("resultDetail") == "full",
        f"{config.label} {purpose} did not report full result detail",
    )
    summary = required_mapping(payload, "summary")
    require(
        summary.get("stepCount") == expected_step_count
        and summary.get("succeededStepCount") == expected_step_count
        and summary.get("failedStepCount") == 0
        and summary.get("failedStepId") is None,
        f"{config.label} {purpose} did not report a fully successful {expected_step_count}-step summary",
    )
    return payload


def successful_account_mutation_payload(
    output: str,
    config: ReleaseSmokeConfig,
    purpose: str,
    expected_outcome: str,
    expected_active: bool,
) -> dict[str, object]:
    envelope = parse_json_output(
        output,
        f"{config.label} {purpose} output was not valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} {purpose} did not report ok status",
    )
    payload = required_mapping(envelope, "payload")
    account = required_mapping(payload, "account")
    require(
        payload.get("outcome") == expected_outcome
        and account.get("accountCode") == fixture_payloads.PLAN_REACTIVATE_RENAME_ACCOUNT_CODE
        and account.get("active") is expected_active,
        f"{config.label} {purpose} did not preserve the expected account lifecycle state",
    )
    return payload


def journal_steps(
    payload: dict[str, object],
    config: ReleaseSmokeConfig,
    purpose: str,
    expected_step_count: int,
) -> list[object]:
    journal = required_mapping(payload, "journal")
    steps = required_list(journal, "steps")
    require(
        len(steps) == expected_step_count,
        f"{config.label} {purpose} did not expose exactly {expected_step_count} journal steps",
    )
    return steps


def journal_step(
    steps: list[object], index: int, config: ReleaseSmokeConfig, purpose: str
) -> dict[str, object]:
    step = steps[index]
    require(
        isinstance(step, dict),
        f"{config.label} {purpose} journal step {index + 1} was not an object",
    )
    return step
