"""Verified chain and complete-book-state assertions for administrative workflows."""

from __future__ import annotations

import json
from dataclasses import replace

from ..attestation_head_checks import VerifiedAttestationHead, verified_attestation_head
from ..cli import run_cli
from ..models import ReleaseSmokeConfig, SmokePath
from ..support import parse_json_output, require
from .administrative_constants import _JSON_MODE, _ZERO_HEAD
from .administrative_models import AdministrativeWorld, ObservedBookState
from .administrative_response import _payload


def _verified_head(
    world: AdministrativeWorld,
    purpose: str,
    *,
    config: ReleaseSmokeConfig | None = None,
) -> VerifiedAttestationHead:
    return verified_attestation_head(
        config if config is not None else world.config,
        dict(world.operation_ids),
        purpose,
    )


def _observe_book_state(world: AdministrativeWorld, purpose: str) -> ObservedBookState:
    """Capture all durable facts a read-only command is forbidden to alter."""
    return ObservedBookState(
        _verified_head(world, purpose + " attestation head"),
        _complete_posting_state(world, purpose + " postings"),
    )


def _complete_posting_state(world: AdministrativeWorld, purpose: str) -> str:
    output = run_cli(
        world.config,
        "list-postings",
        "--book-file",
        world.config.book.argument,
        "--book-key-file",
        world.config.book_key.argument,
        "--limit",
        "100",
        "--output",
        _JSON_MODE,
    )
    envelope = parse_json_output(
        output,
        f"{world.config.label} {purpose} list-postings output was not valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{world.config.label} {purpose} list-postings did not report ok status",
    )
    payload = _payload(envelope, world.config, purpose)
    require(
        payload.get("nextCursor") is None,
        f"{world.config.label} {purpose} exceeded the complete posting-state observation limit",
    )
    postings = payload.get("postings")
    require(
        isinstance(postings, list),
        f"{world.config.label} {purpose} list-postings did not expose a postings array",
    )
    if not isinstance(postings, list):
        raise TypeError("require must reject a non-list posting state")
    require(
        all(isinstance(posting, dict) for posting in postings),
        f"{world.config.label} {purpose} list-postings exposed a non-object posting",
    )
    return json.dumps(postings, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def _require_unchanged_book_state(
    before_state: ObservedBookState,
    after_state: ObservedBookState,
    config: ReleaseSmokeConfig,
    label: str,
    operation_id: str,
) -> None:
    require(
        after_state.attestation_head == before_state.attestation_head,
        f"{config.label} {label} read-only {operation_id} changed the verified attestation state",
    )
    require(
        after_state.posting_state == before_state.posting_state,
        f"{config.label} {label} read-only {operation_id} changed durable posting state",
    )


def _require_verified_append_transition(
    operation_id: str,
    before_head: VerifiedAttestationHead | None,
    after_head: VerifiedAttestationHead,
    config: ReleaseSmokeConfig,
    label: str,
) -> None:
    if before_head is None:
        require(
            after_head.operation_order == "0",
            f"{config.label} {label} {operation_id} did not establish attestation genesis at order 0",
        )
        require(
            after_head.previous_head == _ZERO_HEAD,
            f"{config.label} {label} {operation_id} genesis did not retain the required zero parent head",
        )
        return
    require(
        int(after_head.operation_order) == int(before_head.operation_order) + 1,
        f"{config.label} {label} {operation_id} did not advance the verified attestation order by one",
    )
    require(
        after_head.operation_head != before_head.operation_head,
        f"{config.label} {label} {operation_id} did not change the verified attestation head",
    )
    require(
        after_head.book_id == before_head.book_id,
        f"{config.label} {label} {operation_id} changed the verified book identity",
    )
    require(
        after_head.previous_head == before_head.operation_head,
        f"{config.label} {label} {operation_id} did not retain the verified prior head as its parent",
    )


def _require_restored_snapshot_branch(
    snapshot_head: VerifiedAttestationHead,
    restored_head: VerifiedAttestationHead,
    config: ReleaseSmokeConfig,
) -> None:
    """Prove restore continued the retained snapshot rather than another same-length chain."""
    require(
        restored_head.book_id == snapshot_head.book_id,
        f"{config.label} restore-book did not preserve the backup snapshot book ID",
    )
    require(
        restored_head.operation_order == str(int(snapshot_head.operation_order) + 1),
        f"{config.label} restore-book did not append immediately after the backup snapshot order",
    )
    require(
        restored_head.previous_head == snapshot_head.operation_head,
        f"{config.label} restore-book did not retain the backup snapshot head as its parent",
    )
    require(
        restored_head.operation_head != snapshot_head.operation_head,
        f"{config.label} restore-book did not create a distinct restoration continuation head",
    )


def _verify_book(
    world: AdministrativeWorld,
    book: SmokePath,
    book_key: SmokePath,
    purpose: str,
) -> VerifiedAttestationHead:
    return _verified_head(
        world,
        purpose,
        config=replace(world.config, book=book, book_key=book_key),
    )
