"""Sequential protected-book rekey workflow after standalone restore."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import replace

from ..attestation_arguments import signing_credential_arguments
from ..models import ReleaseSmokeConfig, SmokePath
from .administrative_chain_state import _verify_book
from .administrative_maintenance_artifacts import _record_maintenance_artifact_response
from .administrative_models import AdministrativeWorld, JsonObject, PostOutputAssertion
from .administrative_operation_runner import _run_operation
from .administrative_output import _artifact
from .administrative_paths import _require_absent, _validate_book_key_file, _world_path
from .capabilities import ArtifactCapability, OperationCapability


def _rekey_restored_book(
    world: AdministrativeWorld,
    operations: Mapping[str, OperationCapability],
    rekey_mode: str,
    restored_book: SmokePath,
    restored_key: SmokePath,
    restored_config: ReleaseSmokeConfig,
) -> None:
    rekey_operation = operations["rekey-book"]
    replacement_key = _new_rekey_target(world, "replacement.key", "replacement key")
    rekeyed_config = replace(restored_config, book_key=replacement_key)
    _run_rekey_epoch(
        world,
        rekey_operation,
        rekey_mode,
        restored_book,
        restored_key,
        replacement_key,
        restored_config,
        rekeyed_config,
        "rekey-book key artifact",
        "rekey-book artifact verification",
        "rekey-book capability mode",
        "rekey-book capability mode",
    )

    second_replacement_key = _new_rekey_target(
        world,
        "replacement-second.key",
        "second replacement key",
    )
    twice_rekeyed_config = replace(restored_config, book_key=second_replacement_key)
    _run_rekey_epoch(
        world,
        rekey_operation,
        rekey_mode,
        restored_book,
        replacement_key,
        second_replacement_key,
        rekeyed_config,
        twice_rekeyed_config,
        "second rekey-book key artifact",
        "second rekey-book artifact verification",
        "second rekey-book capability mode",
        "second rekey-book sequential epoch capability mode",
    )


def _new_rekey_target(
    world: AdministrativeWorld,
    filename: str,
    label: str,
) -> SmokePath:
    replacement_key = _world_path(world, world.artifact_directory / filename)
    _require_absent(replacement_key.local_path, world.config, label)
    return replacement_key


def _run_rekey_epoch(
    world: AdministrativeWorld,
    operation: OperationCapability,
    output_mode: str,
    book: SmokePath,
    current_key: SmokePath,
    replacement_key: SmokePath,
    before_config: ReleaseSmokeConfig,
    after_config: ReleaseSmokeConfig,
    key_artifact_label: str,
    verification_label: str,
    output_label: str,
    operation_label: str,
) -> None:
    artifacts = {_artifact(operation, "book-key-file"): replacement_key}
    _run_operation(
        world,
        operation,
        (
            "--book-file",
            book.argument,
            "--book-key-file",
            current_key.argument,
            "--new-book-key-file",
            replacement_key.argument,
            *signing_credential_arguments(before_config),
        ),
        output_mode,
        operation_label,
        before_head_config=before_config,
        after_head_config=after_config,
        post_output_assertion=_rekey_output_assertion(
            world,
            output_mode,
            operation,
            artifacts,
            book,
            replacement_key,
            key_artifact_label,
            verification_label,
            output_label,
        ),
    )


def _rekey_output_assertion(
    world: AdministrativeWorld,
    output_mode: str,
    operation: OperationCapability,
    artifacts: Mapping[ArtifactCapability, SmokePath],
    book: SmokePath,
    replacement_key: SmokePath,
    key_artifact_label: str,
    verification_label: str,
    output_label: str,
) -> PostOutputAssertion:
    def validate_output(envelope: JsonObject | None, output: str) -> None:
        _validate_book_key_file(replacement_key.local_path, world.config, key_artifact_label)
        _verify_book(
            world,
            book,
            replacement_key,
            verification_label,
        )
        _record_maintenance_artifact_response(
            output_mode,
            output,
            envelope,
            operation,
            artifacts,
            world.config,
            output_label,
            rekeyed_book_publication=book,
        )

    return validate_output
