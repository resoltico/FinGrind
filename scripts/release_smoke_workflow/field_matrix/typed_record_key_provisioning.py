"""Credential and book-key provisioning for a fresh typed-record world."""

from __future__ import annotations

from collections.abc import Mapping

from .. import attestation_arguments, cli, support
from ..models import ReleaseSmokeConfig
from .artifact_publication import require_exact_json_artifact_publication
from .mutation_evidence_bootstrap import (
    assert_generated_attestation_key_response,
    assert_generated_book_key_response,
)
from .output_provenance import record_proven_output_mode
from .typed_record_constants import _JSON_MODE
from .typed_record_output import (
    _artifact,
    _operation,
    _operation_capability,
    _payload,
    _record_exact_artifacts,
    _require_nonempty_file,
    _successful_envelope,
)


def _provision_world_keys(
    world_config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
) -> None:
    book_key_operation_id = _operation(operation_ids, "generateBookKeyFile")
    book_key_operation = _operation_capability(book_key_operation_id)
    book_key_output = cli.run_cli(
        world_config,
        book_key_operation_id,
        "--new-book-key-file",
        world_config.book_key.argument,
        "--output",
        _JSON_MODE,
    )
    book_key_envelope = _successful_envelope(
        book_key_output,
        world_config,
        "generate-book-key-file",
    )
    book_key_artifacts = {
        _artifact(book_key_operation, "book-key-file"): world_config.book_key,
    }
    _require_nonempty_file(
        world_config.book_key.local_path,
        world_config,
        "generate-book-key-file",
    )
    require_exact_json_artifact_publication(
        book_key_envelope,
        book_key_operation,
        book_key_artifacts,
        world_config,
        "generate-book-key-file",
    )
    assert_generated_book_key_response(
        world_config,
        _JSON_MODE,
        book_key_output,
        "generate-book-key-file",
    )
    _record_exact_artifacts(
        book_key_operation,
        {artifact: path.local_path for artifact, path in book_key_artifacts.items()},
    )
    record_proven_output_mode(
        book_key_operation,
        _JSON_MODE,
        book_key_output,
        world_config,
        "generate-book-key-file",
    )

    attestation_key_operation_id = _operation(operation_ids, "generateAttestationKeyFile")
    attestation_key_operation = _operation_capability(attestation_key_operation_id)
    attestation_key_output = cli.run_cli(
        world_config,
        attestation_key_operation_id,
        "--attestation-custodian",
        attestation_arguments.ATTESTATION_CUSTODIAN,
        "--new-attestation-key-file",
        world_config.attestation_founder_key.argument,
        "--attestation-passphrase-file",
        world_config.attestation_founder_passphrase.argument,
        "--output",
        _JSON_MODE,
    )
    attestation_key_envelope = _successful_envelope(
        attestation_key_output,
        world_config,
        "generate-attestation-key-file",
    )
    attestation_key_artifacts = {
        _artifact(
            attestation_key_operation, "attestation-key-file"
        ): world_config.attestation_founder_key,
    }
    _require_nonempty_file(
        world_config.attestation_founder_key.local_path,
        world_config,
        "generate-attestation-key-file",
    )
    require_exact_json_artifact_publication(
        attestation_key_envelope,
        attestation_key_operation,
        attestation_key_artifacts,
        world_config,
        "generate-attestation-key-file",
    )
    credential_evidence = assert_generated_attestation_key_response(
        world_config,
        _JSON_MODE,
        attestation_key_output,
        "generate-attestation-key-file",
    )
    attestation_key_payload = _payload(
        attestation_key_envelope,
        world_config,
        "generate-attestation-key-file",
    )
    support.require(
        attestation_key_payload.get("credentialSpki") == credential_evidence.credential_spki
        and attestation_key_payload.get("keyId") == credential_evidence.key_id,
        f"{world_config.label} generate-attestation-key-file did not retain one credential identity",
    )
    _record_exact_artifacts(
        attestation_key_operation,
        {artifact: path.local_path for artifact, path in attestation_key_artifacts.items()},
    )
    record_proven_output_mode(
        attestation_key_operation,
        _JSON_MODE,
        attestation_key_output,
        world_config,
        "generate-attestation-key-file",
    )
