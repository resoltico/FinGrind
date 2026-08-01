"""Route-specific response and durable-state assertions for plan workflows."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_head_checks import VerifiedAttestationHead
from ..support import require, require_labeled_text_value
from .administrative_attestation_output import _require_text_attestation
from .administrative_constants import _JSON_MODE
from .administrative_models import AdministrativeWorld, JsonObject
from .administrative_reads import _persisted_account
from .administrative_response import (
    _request_text,
    _require_response_attestation_commit,
    _require_text_title,
    _response_payload,
)


def _assert_preflight_entry_evidence(
    world: AdministrativeWorld,
    output_mode: str,
    output: str,
    request: JsonObject,
    label: str,
) -> None:
    expected_idempotency = _request_text(request, "provenance", "idempotencyKey", world, label)
    expected_date = _request_text(request, None, "effectiveDate", world, label)
    if output_mode == _JSON_MODE:
        payload = _response_payload(world, "preflight-entry", output, label)
        require(
            payload.get("idempotencyKey") == expected_idempotency
            and payload.get("effectiveDate") == expected_date
            and isinstance(payload.get("resolvedJournal"), Mapping),
            f"{world.config.label} {label} preflight-entry[json] did not identify its requested "
            "uncommitted journal",
        )
        return
    _require_text_title(world, "preflight-entry", output, "Entry Preflight Passed", label)
    require_labeled_text_value(
        output,
        "Idempotency key",
        expected_idempotency,
        f"{world.config.label} {label} preflight-entry[text] did not retain its request identity",
    )
    require_labeled_text_value(
        output,
        "Effective date",
        expected_date,
        f"{world.config.label} {label} preflight-entry[text] did not retain its requested date",
    )
    require_labeled_text_value(
        output,
        "Commit status",
        "Not committed",
        f"{world.config.label} {label} preflight-entry[text] did not remain uncommitted",
    )


def _assert_execute_plan_evidence(
    world: AdministrativeWorld,
    output_mode: str,
    output: str,
    request: JsonObject,
    expected_head: VerifiedAttestationHead,
    label: str,
) -> None:
    plan_id = _request_text(request, None, "planId", world, label)
    raw_steps = request.get("steps")
    require(
        isinstance(raw_steps, list) and bool(raw_steps),
        f"{world.config.label} {label} execute-plan request did not retain executable steps",
    )
    if not isinstance(raw_steps, list):
        raise TypeError("plan proof requires its requested steps")
    if output_mode == _JSON_MODE:
        payload = _response_payload(world, "execute-plan", output, label)
        summary = payload.get("summary")
        require(
            payload.get("planId") == plan_id
            and payload.get("status") == "succeeded"
            and isinstance(summary, Mapping)
            and summary.get("stepCount") == len(raw_steps)
            and summary.get("succeededStepCount") == len(raw_steps)
            and summary.get("failedStepCount") == 0,
            f"{world.config.label} {label} execute-plan[json] did not retain its successful "
            "requested plan result",
        )
        _require_response_attestation_commit(
            payload, expected_head, world, "execute-plan", "json", label
        )
    else:
        _require_text_title(world, "execute-plan", output, "Execute Plan", label)
        require_labeled_text_value(
            output,
            "Plan id",
            plan_id,
            f"{world.config.label} {label} execute-plan[text] did not retain its plan identity",
        )
        require_labeled_text_value(
            output,
            "Step count",
            str(len(raw_steps)),
            f"{world.config.label} {label} execute-plan[text] did not retain its step count",
        )
        _require_text_attestation(
            output,
            world.config,
            label,
            "execute-plan",
            expected_head=expected_head,
        )
    for step in raw_steps:
        if not isinstance(step, Mapping) or step.get("kind") != "declare-account":
            continue
        declaration = step.get("declareAccount")
        require(
            isinstance(declaration, Mapping),
            f"{world.config.label} {label} execute-plan declared account step had no account",
        )
        if not isinstance(declaration, Mapping):
            raise TypeError("declared-account plan step requires an account object")
        account_code = declaration.get("accountCode")
        account_name = declaration.get("accountName")
        require(
            isinstance(account_code, str) and isinstance(account_name, str),
            f"{world.config.label} {label} execute-plan declared account lacked its identity",
        )
        account = _persisted_account(world, account_code, label)
        require(
            account.get("accountName") == account_name and account.get("active") is True,
            f"{world.config.label} {label} execute-plan did not persist its declared account step",
        )
