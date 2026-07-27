"""Verified attestation and public-read evidence for typed-record writes."""

from __future__ import annotations

from ..attestation_head_checks import VerifiedAttestationHead
from ..cli import run_cli
from ..models import ReleaseSmokeConfig
from ..support import require
from .typed_record_constants import _JSON_MODE
from .typed_record_models import JsonObject, TypedRecordWorld
from .typed_record_output import _payload, _successful_envelope


def _require_verified_head_advance(
    before_head: VerifiedAttestationHead,
    after_head: VerifiedAttestationHead,
    config: ReleaseSmokeConfig,
    scenario_id: str,
    operation_id: str,
) -> None:
    label = f"{config.label} {scenario_id} {operation_id} text output"
    require(
        int(after_head.operation_order) == int(before_head.operation_order) + 1,
        f"{label} did not advance the verified attestation order by one",
    )
    require(
        after_head.operation_head != before_head.operation_head,
        f"{label} did not change the verified attestation head",
    )
    require(
        after_head.book_id == before_head.book_id,
        f"{label} changed the verified book identity",
    )
    require(
        after_head.previous_head == before_head.operation_head,
        f"{label} did not retain the verified prior head as its parent",
    )


def _persisted_posting(
    world: TypedRecordWorld,
    posting_id: str,
    label: str,
) -> JsonObject:
    """Read the posting back through its public query route before coverage credit."""
    output = run_cli(
        world.config,
        "get-posting",
        "--book-file",
        world.config.book.argument,
        "--book-key-file",
        world.config.book_key.argument,
        "--posting-id",
        posting_id,
        "--output",
        _JSON_MODE,
    )
    envelope = _successful_envelope(
        output,
        world.config,
        f"typed-record {label} durable posting lookup",
    )
    payload = _payload(
        envelope,
        world.config,
        f"typed-record {label} durable posting lookup",
    )
    require(
        payload.get("family") == "get-posting",
        f"{world.config.label} typed-record {label} durable posting lookup returned another query family",
    )
    posting = payload.get("posting")
    require(
        isinstance(posting, dict),
        f"{world.config.label} typed-record {label} durable posting lookup did not expose posting",
    )
    if not isinstance(posting, dict):
        raise TypeError("require must reject a missing durable posting")
    return posting


def _persisted_account(
    world: TypedRecordWorld,
    account_code: str,
    label: str,
) -> JsonObject:
    """Read the account back through the public registry query before coverage credit."""
    output = run_cli(
        world.config,
        "list-accounts",
        "--book-file",
        world.config.book.argument,
        "--book-key-file",
        world.config.book_key.argument,
        "--limit",
        "100",
        "--output",
        _JSON_MODE,
    )
    envelope = _successful_envelope(
        output,
        world.config,
        f"typed-record {label} durable account lookup",
    )
    payload = _payload(
        envelope,
        world.config,
        f"typed-record {label} durable account lookup",
    )
    require(
        payload.get("family") == "list-accounts",
        f"{world.config.label} typed-record {label} durable account lookup returned another query family",
    )
    accounts = payload.get("accounts")
    require(
        isinstance(accounts, list),
        f"{world.config.label} typed-record {label} durable account lookup did not expose accounts",
    )
    if not isinstance(accounts, list):
        raise TypeError("require must reject missing durable account rows")
    matches = [
        account
        for account in accounts
        if isinstance(account, dict) and account.get("accountCode") == account_code
    ]
    require(
        len(matches) == 1,
        f"{world.config.label} typed-record {label} durable account lookup did not expose one "
        "requested account",
    )
    if len(matches) != 1:
        raise AssertionError("require must reject an absent or duplicate durable account")
    return matches[0]
