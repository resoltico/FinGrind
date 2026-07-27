"""Typed-record request execution, including its data-dependent reversal lifecycle."""

from __future__ import annotations

from collections.abc import Mapping

from .. import attestation_arguments, attestation_head_checks, cli, support
from .context import record_new_attestation_append
from .mutation_evidence_posting import (
    assert_committed_posting_response,
    assert_persisted_posting_state,
)
from .output_provenance import record_proven_output_mode
from .typed_record_constants import _JSON_MODE, _TEXT_MODE
from .typed_record_evidence import _persisted_posting, _require_verified_head_advance
from .typed_record_models import TypedRecordRequest, TypedRecordWorld
from .typed_record_output import (
    _operation,
    _operation_capability,
    _payload,
    _successful_envelope,
)
from .typed_record_paths import _request_path, _write_json
from .typed_record_payloads import _direct_journal_request, _posting_request


def _run_reversal_sequence(
    world: TypedRecordWorld,
    operation_ids: Mapping[str, str],
    output_mode: str,
) -> None:
    seed_request = _direct_journal_request(
        world.config.request_prefix,
        "reversal-seed",
        "2026-01-02",
        "matrix-clearing",
        "cash",
        "100",
        "bank-deposit",
    )
    seed_path = _request_path(world, "01-reversal-seed.json")
    _write_json(seed_path.local_path, seed_request)
    before_head = attestation_head_checks.verified_attestation_head(
        world.config,
        dict(operation_ids),
        "typed-record reversal seed before",
    )
    post_entry_operation_id = _operation(operation_ids, "postEntry")
    seed_output = cli.run_cli(
        world.config,
        post_entry_operation_id,
        "--book-file",
        world.config.book.argument,
        "--book-key-file",
        world.config.book_key.argument,
        "--request-file",
        seed_path.argument,
        *attestation_arguments.signing_credential_arguments(world.config),
        "--output",
        _JSON_MODE,
    )
    seed_envelope = _successful_envelope(
        seed_output,
        world.config,
        "reversal seed post-entry",
    )
    after_head = attestation_head_checks.verified_attestation_head(
        world.config,
        dict(operation_ids),
        "typed-record reversal seed after",
    )
    seed_evidence = assert_committed_posting_response(
        world.config,
        post_entry_operation_id,
        seed_request,
        _JSON_MODE,
        seed_output,
        after_head,
        "reversal seed post-entry",
    )
    assert_persisted_posting_state(
        _persisted_posting(world, seed_evidence.posting_id, "reversal seed post-entry"),
        seed_evidence,
        purpose=f"{world.config.label} reversal seed post-entry",
    )
    record_new_attestation_append(
        post_entry_operation_id,
        seed_envelope,
        before_head=before_head,
        after_head=after_head,
    )
    record_proven_output_mode(
        _operation_capability(post_entry_operation_id),
        _JSON_MODE,
        seed_output,
        world.config,
        "reversal seed post-entry",
    )
    seed_payload = _payload(seed_envelope, world.config, "reversal seed post-entry")
    prior_posting_id = support.require_string(seed_payload, "postingId")
    reversal_request = _posting_request(
        world.config.request_prefix,
        "record-reversal",
        "REVERSAL",
        "2026-01-03",
        "reversal-support",
        {
            "reversal": {
                "priorPostingId": prior_posting_id,
                "reason": "operator-correction",
            }
        },
    )
    _run_typed_request(
        world,
        operation_ids,
        "reversal",
        output_mode,
        2,
        TypedRecordRequest("recordReversal", reversal_request),
    )


def _run_typed_request(
    world: TypedRecordWorld,
    operation_ids: Mapping[str, str],
    scenario_id: str,
    output_mode: str,
    index: int,
    request: TypedRecordRequest,
) -> None:
    operation_id = _operation(operation_ids, request.operation_key)
    operation = _operation_capability(operation_id)
    request_path = _request_path(world, f"{index:02d}-{operation_id}.json")
    _write_json(request_path.local_path, request.request)
    before_head = attestation_head_checks.verified_attestation_head(
        world.config,
        dict(operation_ids),
        f"typed-record {scenario_id} {operation_id} before",
    )
    output = cli.run_cli(
        world.config,
        operation_id,
        "--book-file",
        world.config.book.argument,
        "--book-key-file",
        world.config.book_key.argument,
        "--request-file",
        request_path.argument,
        *attestation_arguments.signing_credential_arguments(world.config),
        "--output",
        output_mode,
    )
    after_head = attestation_head_checks.verified_attestation_head(
        world.config,
        dict(operation_ids),
        f"typed-record {scenario_id} {operation_id} after",
    )
    posting_evidence = assert_committed_posting_response(
        world.config,
        operation_id,
        request.request,
        output_mode,
        output,
        after_head,
        f"typed-record {scenario_id} {operation_id}",
    )
    assert_persisted_posting_state(
        _persisted_posting(world, posting_evidence.posting_id, f"{scenario_id} {operation_id}"),
        posting_evidence,
        purpose=f"{world.config.label} typed-record {scenario_id} {operation_id}",
    )
    if output_mode == _JSON_MODE:
        envelope = _successful_envelope(output, world.config, operation_id)
        record_new_attestation_append(
            operation_id,
            envelope,
            before_head=before_head,
            after_head=after_head,
        )
        record_proven_output_mode(
            operation,
            _JSON_MODE,
            output,
            world.config,
            f"typed-record {scenario_id} {operation_id}",
        )
        return
    _require_verified_head_advance(
        before_head,
        after_head,
        world.config,
        scenario_id,
        operation_id,
    )
    record_proven_output_mode(
        operation,
        _TEXT_MODE,
        output,
        world.config,
        f"typed-record {scenario_id} {operation_id}",
    )
