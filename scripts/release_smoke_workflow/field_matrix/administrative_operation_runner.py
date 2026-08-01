"""Invocation, verified-state transition, and output-credit orchestration."""

from __future__ import annotations

from .. import attestation_arguments, attestation_head_checks, cli, support
from ..models import ReleaseSmokeConfig
from . import (
    administrative_chain_state,
    administrative_evidence,
    administrative_modes,
    administrative_operation_output,
)
from .administrative_models import AdministrativeWorld, JsonObject, PostOutputAssertion
from .capabilities import OperationCapability
from .output_provenance import record_proven_output_mode
from .scenario_matrix import SCENARIO_MATRIX


def _run_arguments_mutation(
    world: AdministrativeWorld,
    operation: OperationCapability,
    extra_arguments: tuple[str, ...],
    output_mode: str,
    label: str,
    *,
    request: JsonObject | None = None,
    before_head_config: ReleaseSmokeConfig | None = None,
    after_head_config: ReleaseSmokeConfig | None = None,
    branch_predecessor: attestation_head_checks.VerifiedAttestationHead | None = None,
    post_output_assertion: PostOutputAssertion | None = None,
) -> JsonObject | None:
    return _run_operation(
        world,
        operation,
        (
            "--book-file",
            world.config.book.argument,
            "--book-key-file",
            world.config.book_key.argument,
            *extra_arguments,
            *attestation_arguments.signing_credential_arguments(world.config),
        ),
        output_mode,
        label,
        request=request,
        before_head_config=before_head_config,
        after_head_config=after_head_config,
        branch_predecessor=branch_predecessor,
        post_output_assertion=post_output_assertion,
    )


def _run_operation(
    world: AdministrativeWorld,
    operation: OperationCapability,
    arguments: tuple[str, ...],
    output_mode: str,
    label: str,
    *,
    request: JsonObject | None = None,
    before_head_config: ReleaseSmokeConfig | None = None,
    after_head_config: ReleaseSmokeConfig | None = None,
    branch_predecessor: attestation_head_checks.VerifiedAttestationHead | None = None,
    post_output_assertion: PostOutputAssertion | None = None,
) -> JsonObject | None:
    # A cohesive fresh-world group iterates the union of its members' advertised
    # modes. A member that does not support this group's current representative
    # mode runs its own first advertised mode; every one of its advertised modes
    # is still selected in a corresponding union iteration.
    output_mode = administrative_modes._supported_mode(operation, output_mode)
    binding = SCENARIO_MATRIX[operation.operation_id]
    before_head = None
    before_state = None
    if binding.requires_new_attestation_append:
        before_head = branch_predecessor or administrative_chain_state._verified_head(
            world,
            label + " before " + operation.operation_id,
            config=before_head_config,
        )
    else:
        support.require(
            before_head_config is None and after_head_config is None and branch_predecessor is None,
            f"{world.config.label} {label} supplied append-only head controls to read-only "
            f"{operation.operation_id}",
        )
        before_state = administrative_chain_state._observe_book_state(
            world, label + " before " + operation.operation_id
        )
    output = cli.run_cli(
        world.config,
        operation.operation_id,
        *arguments,
        "--output",
        output_mode,
    )
    after_head = (
        administrative_chain_state._verified_head(
            world,
            label + " after " + operation.operation_id,
            config=after_head_config,
        )
        if binding.requires_new_attestation_append
        else None
    )
    after_state = (
        administrative_chain_state._observe_book_state(
            world, label + " after " + operation.operation_id
        )
        if not binding.requires_new_attestation_append
        else None
    )
    if binding.requires_new_attestation_append:
        support.require(
            after_head is not None,
            f"{world.config.label} {label} did not verify the post-mutation attestation head",
        )
        if after_head is None:
            raise AssertionError("required append evidence must have a verified post-mutation head")
        administrative_chain_state._require_verified_append_transition(
            operation.operation_id,
            before_head,
            after_head,
            world.config,
            label,
        )
    else:
        support.require(
            before_state is not None and after_state is not None,
            f"{world.config.label} {label} did not retain complete read-only book evidence",
        )
        if before_state is None or after_state is None:
            raise AssertionError("read-only operation must retain before/after book state")
        administrative_chain_state._require_unchanged_book_state(
            before_state,
            after_state,
            world.config,
            label,
            operation.operation_id,
        )
    administrative_evidence._assert_administrative_operation_evidence(
        world,
        operation,
        output_mode,
        output,
        label,
        request=request,
        arguments=arguments,
        after_head=after_head,
    )
    envelope = administrative_operation_output._process_operation_output(
        operation,
        output_mode,
        output,
        world.config,
        label,
        before_head=before_head,
        after_head=after_head,
    )
    if operation.artifact_outputs:
        support.require(
            post_output_assertion is not None,
            f"{world.config.label} {label} {operation.operation_id} needs artifact proof before "
            "output-mode credit",
        )
        if post_output_assertion is None:
            raise AssertionError("artifact-producing operations require post-output proof")
        post_output_assertion(envelope, output)
    else:
        support.require(
            post_output_assertion is None,
            f"{world.config.label} {label} supplied artifact proof for non-artifact "
            f"{operation.operation_id}",
        )
        record_proven_output_mode(operation, output_mode, output, world.config, label)
    return envelope
