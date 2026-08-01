from __future__ import annotations

from typing import Any

from .models import ReleaseSmokeConfig
from .support import require, required_list, required_mapping

_PLAN_ATTESTATION_OUTCOMES: list[dict[str, str]] = [
    {
        "disposition": "appended",
        "attestationCommit": "required",
        "attestationCredentials": "required",
    },
    {
        "disposition": "read-only",
        "attestationCommit": "must-be-null",
        "attestationCredentials": "prohibited",
    },
    {
        "disposition": "no-durable-child-mutation",
        "attestationCommit": "must-be-null",
        "attestationCredentials": "required",
    },
]


def assert_plan_attestation_outcome_contract(
    config: ReleaseSmokeConfig, full_contract: dict[str, Any]
) -> None:
    plan_execution = required_mapping(full_contract, "planExecution")
    request_shapes = required_mapping(full_contract, "requestShapes")
    ledger_plan = required_mapping(request_shapes, "ledgerPlan")
    ledger_plan_execution = required_mapping(ledger_plan, "execution")

    plan_outcomes = _assert_plan_attestation_outcome_table(
        config, plan_execution, "full-contract planExecution"
    )
    nested_outcomes = _assert_plan_attestation_outcome_table(
        config, ledger_plan_execution, "ledger-plan request shape"
    )
    require(
        plan_outcomes == nested_outcomes,
        f"{config.label} capabilities output projected different plan attestation-outcome tables "
        "at planExecution and requestShapes.ledgerPlan.execution",
    )


def _assert_plan_attestation_outcome_table(
    config: ReleaseSmokeConfig,
    plan_execution: dict[str, Any],
    label: str,
) -> list[Any]:
    require(
        "attestationDispositions" not in plan_execution,
        f"{config.label} {label} retained the legacy attestationDispositions list",
    )
    outcomes = required_list(plan_execution, "attestationOutcomes")
    for outcome in outcomes:
        require(
            isinstance(outcome, dict),
            f"{config.label} {label} published a non-object plan attestation outcome",
        )
        if not isinstance(outcome, dict):
            continue
        require(
            "attestationCommitRequired" not in outcome,
            f"{config.label} {label} retained the legacy attestationCommitRequired boolean",
        )
        require(
            "attestationCredentialsRequired" not in outcome,
            f"{config.label} {label} retained the legacy attestationCredentialsRequired boolean",
        )
    require(
        outcomes == _PLAN_ATTESTATION_OUTCOMES,
        f"{config.label} {label} did not publish the exact ordered plan attestation-outcome table",
    )
    return outcomes
