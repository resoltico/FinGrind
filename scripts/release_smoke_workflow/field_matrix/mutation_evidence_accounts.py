"""Account-registry mutation and durable-state evidence."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure
from ..support import parse_json_output, require, require_labeled_text_value
from .mutation_evidence_support import (
    _require_attestation_commit,
    _required_text,
    _success_payload,
)


@dataclass(frozen=True)
class AccountMutationEvidence:
    """The account identity returned by one account-registry mutation."""

    account_code: str
    account_name: str | None
    active: bool


def assert_account_mutation_response(
    config: ReleaseSmokeConfig,
    operation_id: str,
    request: Mapping[str, object],
    output_mode: str,
    output: str,
    expected_head: VerifiedAttestationHead,
    purpose: str,
) -> AccountMutationEvidence:
    """Bind an account lifecycle response to its request and attested result."""
    expected_code = _required_text(request, "accountCode", config, operation_id, purpose, "request")
    expected_name = request.get("accountName")
    require(
        expected_name is None or isinstance(expected_name, str),
        f"{config.label} {purpose} {operation_id} request had an invalid accountName",
    )
    expected_active = operation_id != "retire-account"
    expected_outcome = {
        "declare-account": "declared",
        "amend-account": "amended",
        "retire-account": "retired",
    }.get(operation_id)
    require(
        expected_outcome is not None,
        f"{config.label} {purpose} has no account lifecycle proof for {operation_id}",
    )
    if expected_outcome is None:
        raise AssertionError("account evidence must have an operation-specific expected outcome")
    expected_title = {
        "declare-account": "Account Declared",
        "amend-account": "Account Amended",
        "retire-account": "Account Retired",
    }[operation_id]
    if output_mode == "json":
        envelope = parse_json_output(
            output,
            f"{config.label} {purpose} {operation_id}[json] did not emit valid JSON",
        )
        payload = _success_payload(envelope, config, operation_id, purpose, "json")
        account = payload.get("account")
        require(
            payload.get("outcome") == expected_outcome
            and isinstance(account, Mapping)
            and account.get("accountCode") == expected_code
            and (expected_name is None or account.get("accountName") == expected_name)
            and account.get("active") is expected_active,
            f"{config.label} {purpose} {operation_id}[json] did not identify its requested "
            "account lifecycle result",
        )
        if not isinstance(account, Mapping):
            raise AssertionError("account mutation requires an account response object")
        _require_attestation_commit(payload, expected_head, config, operation_id, purpose, "json")
        returned_name = account.get("accountName")
        require(
            isinstance(returned_name, str) and bool(returned_name.strip()),
            f"{config.label} {purpose} {operation_id}[json] did not expose the persisted account name",
        )
        if not isinstance(returned_name, str):
            raise AssertionError("account mutation must expose account name")
        return AccountMutationEvidence(expected_code, returned_name, expected_active)
    if output_mode == "text":
        first_line = next((line.strip() for line in output.splitlines() if line.strip()), "")
        require(
            first_line == expected_title,
            f"{config.label} {purpose} {operation_id}[text] did not emit its account lifecycle "
            f"title {expected_title!r}",
        )
        require_labeled_text_value(
            output,
            "Account code",
            expected_code,
            f"{config.label} {purpose} {operation_id}[text] did not retain the requested account code",
        )
        if isinstance(expected_name, str):
            require_labeled_text_value(
                output,
                "Account name",
                expected_name,
                f"{config.label} {purpose} {operation_id}[text] did not retain the requested account name",
            )
        require_labeled_text_value(
            output,
            "Active",
            "Yes" if expected_active else "No",
            f"{config.label} {purpose} {operation_id}[text] did not retain the account active state",
        )
        require_labeled_text_value(
            output,
            "Attestation order",
            expected_head.operation_order,
            f"{config.label} {purpose} {operation_id}[text] did not publish its verified attestation order",
        )
        require_labeled_text_value(
            output,
            "Attestation head",
            expected_head.operation_head,
            f"{config.label} {purpose} {operation_id}[text] did not publish its verified attestation head",
        )
        return AccountMutationEvidence(
            expected_code,
            expected_name if isinstance(expected_name, str) else None,
            expected_active,
        )
    raise ReleaseSmokeFailure(
        f"{config.label} {purpose} {operation_id} advertised unsupported account mode {output_mode}"
    )


def assert_persisted_account_state(
    account: Mapping[str, Any],
    expected: AccountMutationEvidence,
    *,
    purpose: str,
) -> None:
    """Require the account list view to expose the mutation's durable result."""
    require(
        account.get("accountCode") == expected.account_code
        and (expected.account_name is None or account.get("accountName") == expected.account_name)
        and account.get("active") is expected.active,
        f"{purpose} did not persist the account lifecycle result returned by the mutation",
    )
