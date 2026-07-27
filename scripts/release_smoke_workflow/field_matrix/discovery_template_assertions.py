"""Schema evidence for raw discovery templates that intentionally bypass envelopes."""

from __future__ import annotations

from collections.abc import Callable, Mapping
from typing import Any

from ..models import ReleaseSmokeConfig
from ..support import require
from .discovery_output_assertions import required_mapping

RawTemplateAssertion = Callable[[dict[str, Any]], None]


def raw_template_assertion(
    config: ReleaseSmokeConfig,
    operation_id: str,
) -> RawTemplateAssertion:
    """Build the durable scaffold assertion for one raw template operation."""

    def assertion(payload: dict[str, Any]) -> None:
        if operation_id == "print-request-template":
            require(
                payload.get("effectiveDate") == "2026-01-15"
                and required_mapping(
                    payload,
                    "provenance",
                    f"{config.label} field-matrix print-request-template",
                ).get("commandId")
                == "ffffffff-ffff-7fff-bfff-fffffffffff1",
                f"{config.label} field-matrix print-request-template did not retain "
                "the canonical effective-date and provenance facts",
            )
            return
        if operation_id == "print-plan-template":
            steps = payload.get("steps")
            require(
                payload.get("planId") == "general-workflow"
                and isinstance(steps, list)
                and bool(steps)
                and isinstance(steps[0], Mapping)
                and steps[0].get("stepId") == "record-sale-settled",
                f"{config.label} field-matrix print-plan-template did not retain "
                "the canonical executable first step",
            )
            return
        raise AssertionError(f"unrouted raw template operation: {operation_id}")

    return assertion
