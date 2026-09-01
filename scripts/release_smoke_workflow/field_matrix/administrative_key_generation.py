"""Generated book-key and attestation-credential workflows."""

from __future__ import annotations

from collections.abc import Mapping

from .. import attestation_arguments, cli, fixtures, support
from ..models import SmokePath
from . import artifact_publication, mutation_evidence_bootstrap
from .administrative_constants import _JSON_MODE, _TEXT_MODE
from .administrative_models import AdministrativeWorld, JsonObject
from .administrative_operation_output import _process_operation_output
from .administrative_output import (
    _artifact,
    _record_exact_artifacts,
    _require_text_artifact_publication,
)
from .administrative_paths import (
    _require_absent,
    _validate_attestation_key_file,
    _validate_book_key_file,
    _world_path,
)
from .administrative_response import _payload
from .capabilities import OperationCapability
from .output_provenance import record_proven_output_mode


def _generate_book_key(
    world: AdministrativeWorld,
    operation: OperationCapability,
    output_mode: str,
) -> None:
    _require_absent(world.config.book_key.local_path, world.config, "new book key")
    artifact_paths = {_artifact(operation, "book-key-file"): world.config.book_key}
    output = cli.run_cli(
        world.config,
        operation.operation_id,
        "--new-book-key-file",
        world.config.book_key.argument,
        "--output",
        output_mode,
    )
    envelope = _process_operation_output(
        operation, output_mode, output, world.config, "generate-book-key-file"
    )
    if output_mode == _JSON_MODE:
        artifact_publication.require_exact_json_artifact_publication(
            envelope,
            operation,
            artifact_paths,
            world.config,
            "generate-book-key-file",
            "publication-transaction",
        )
    _validate_book_key_file(world.config.book_key.local_path, world.config, "generated book key")
    if output_mode == _TEXT_MODE:
        _require_text_artifact_publication(
            output,
            operation,
            artifact_paths,
            world.config,
            "generate-book-key-file",
            "publication-transaction",
        )
    mutation_evidence_bootstrap.assert_generated_book_key_response(
        world.config,
        output_mode,
        output,
        "generate-book-key-file",
    )
    _record_exact_artifacts(
        operation,
        {artifact: path.local_path for artifact, path in artifact_paths.items()},
    )
    record_proven_output_mode(
        operation,
        output_mode,
        output,
        world.config,
        "generate-book-key-file",
    )


def _generate_attestation_key(
    world: AdministrativeWorld,
    operation: OperationCapability,
    output_mode: str,
    key_path: SmokePath,
    passphrase_path: SmokePath,
    label: str,
) -> JsonObject | None:
    _require_absent(key_path.local_path, world.config, label)
    artifact_paths = {_artifact(operation, "attestation-key-file"): key_path}
    output = cli.run_cli(
        world.config,
        operation.operation_id,
        "--attestation-custodian",
        attestation_arguments.ATTESTATION_CUSTODIAN,
        "--new-attestation-key-file",
        key_path.argument,
        "--attestation-passphrase-file",
        passphrase_path.argument,
        "--output",
        output_mode,
    )
    envelope = _process_operation_output(operation, output_mode, output, world.config, label)
    if output_mode == _JSON_MODE:
        artifact_publication.require_exact_json_artifact_publication(
            envelope,
            operation,
            artifact_paths,
            world.config,
            label,
            "publication-transaction",
        )
    _validate_attestation_key_file(key_path.local_path, world.config, label)
    if output_mode == _TEXT_MODE:
        _require_text_artifact_publication(
            output,
            operation,
            artifact_paths,
            world.config,
            label,
            "publication-transaction",
        )
    credential = mutation_evidence_bootstrap.assert_generated_attestation_key_response(
        world.config,
        output_mode,
        output,
        label,
    )
    payload = None
    if envelope is not None:
        payload = _payload(envelope, world.config, label)
        support.require(
            payload.get("credentialSpki") == credential.credential_spki
            and payload.get("keyId") == credential.key_id,
            f"{world.config.label} {label} credited a different generated credential response",
        )
    _record_exact_artifacts(
        operation,
        {artifact: path.local_path for artifact, path in artifact_paths.items()},
    )
    record_proven_output_mode(operation, output_mode, output, world.config, label)
    return payload


def _generate_additional_credential(
    world: AdministrativeWorld,
    operations: Mapping[str, OperationCapability],
    credential_label: str,
) -> str:
    key_path = _world_path(world, world.root / "credentials" / f"{credential_label}.fgatk")
    passphrase_path = _world_path(
        world, world.root / "credentials" / f"{credential_label}.passphrase"
    )
    passphrase_path.local_path.write_text(
        f"administrative-matrix-{credential_label}-passphrase\n", encoding="utf-8"
    )
    fixtures.prepare_owner_only_file(passphrase_path.local_path)
    payload = _generate_attestation_key(
        world,
        operations["generate-attestation-key-file"],
        _JSON_MODE,
        key_path,
        passphrase_path,
        "generate " + credential_label + " credential",
    )
    if payload is None:
        raise AssertionError("JSON credential generation must expose a payload")
    return support.require_string(payload, "credentialSpki")
