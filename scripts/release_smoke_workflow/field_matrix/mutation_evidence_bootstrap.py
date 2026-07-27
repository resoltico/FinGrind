"""Bootstrap-response evidence for field-matrix mutation scenarios.

This module owns generated key and genesis response proofs. Route-specific
posting and registry evidence live in their corresponding focused modules.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure
from ..support import parse_json_output, require, require_labeled_text_value
from .mutation_evidence_support import (
    _require_attestation_commit,
    _require_nonblank_text_label,
    _require_text_title,
    _required_labeled_text_value,
    _required_text,
    _success_payload,
)


@dataclass(frozen=True)
class AttestationCredentialEvidence:
    """Public identity returned when one encrypted credential is generated."""

    credential_spki: str
    key_id: str


def assert_generated_book_key_response(
    config: ReleaseSmokeConfig,
    output_mode: str,
    output: str,
    purpose: str,
) -> None:
    """Require the key-generation response to identify a real generated secret."""
    operation_id = "generate-book-key-file"
    if output_mode == "json":
        envelope = parse_json_output(
            output,
            f"{config.label} {purpose} {operation_id}[json] did not emit valid JSON",
        )
        payload = _success_payload(envelope, config, operation_id, purpose, "json")
        require(
            isinstance(payload.get("encoding"), str)
            and bool(payload["encoding"].strip())
            and isinstance(payload.get("entropyBits"), int)
            and payload["entropyBits"] > 0
            and isinstance(payload.get("permissions"), str)
            and bool(payload["permissions"].strip()),
            f"{config.label} {purpose} {operation_id}[json] did not identify generated key facts",
        )
        return
    if output_mode == "text":
        _require_text_title(config, operation_id, output, "Book Key File Generated", purpose)
        _require_nonblank_text_label(config, operation_id, output, "Book key file", purpose)
        _require_nonblank_text_label(config, operation_id, output, "Encoding", purpose)
        entropy = _required_labeled_text_value(
            output, "Entropy bits", config, operation_id, purpose
        )
        require(
            entropy.isdecimal() and int(entropy) > 0,
            f"{config.label} {purpose} {operation_id}[text] did not report positive entropy bits",
        )
        _require_nonblank_text_label(config, operation_id, output, "Permissions", purpose)
        return
    raise ReleaseSmokeFailure(
        f"{config.label} {purpose} {operation_id} advertised unsupported key mode {output_mode}"
    )


def assert_generated_attestation_key_response(
    config: ReleaseSmokeConfig,
    output_mode: str,
    output: str,
    purpose: str,
) -> AttestationCredentialEvidence:
    """Require a generated credential response to publish its usable public identity."""
    operation_id = "generate-attestation-key-file"
    if output_mode == "json":
        envelope = parse_json_output(
            output,
            f"{config.label} {purpose} {operation_id}[json] did not emit valid JSON",
        )
        payload = _success_payload(envelope, config, operation_id, purpose, "json")
        return AttestationCredentialEvidence(
            _required_text(payload, "credentialSpki", config, operation_id, purpose, "json"),
            _required_text(payload, "keyId", config, operation_id, purpose, "json"),
        )
    if output_mode == "text":
        _require_text_title(config, operation_id, output, "Attestation Key File Generated", purpose)
        _require_nonblank_text_label(config, operation_id, output, "Attestation key file", purpose)
        return AttestationCredentialEvidence(
            _required_labeled_text_value(output, "Credential SPKI", config, operation_id, purpose),
            _required_labeled_text_value(output, "Key ID", config, operation_id, purpose),
        )
    raise ReleaseSmokeFailure(
        f"{config.label} {purpose} {operation_id} advertised unsupported credential mode "
        f"{output_mode}"
    )


def assert_open_book_response(
    config: ReleaseSmokeConfig,
    output_mode: str,
    output: str,
    expected_head: VerifiedAttestationHead,
    purpose: str,
) -> None:
    """Bind a genesis response to the independently verified new book identity."""
    operation_id = "open-book"
    if output_mode == "json":
        envelope = parse_json_output(
            output,
            f"{config.label} {purpose} {operation_id}[json] did not emit valid JSON",
        )
        payload = _success_payload(envelope, config, operation_id, purpose, "json")
        require(
            payload.get("attestationBookId") == expected_head.book_id
            and isinstance(payload.get("bookIdentity"), Mapping)
            and isinstance(payload.get("attestationTrustRoot"), Mapping),
            f"{config.label} {purpose} {operation_id}[json] did not identify its attested book",
        )
        _require_attestation_commit(payload, expected_head, config, operation_id, purpose, "json")
        return
    if output_mode == "text":
        _require_text_title(config, operation_id, output, "Book Initialized", purpose)
        require_labeled_text_value(
            output,
            "Attestation book ID",
            expected_head.book_id,
            f"{config.label} {purpose} {operation_id}[text] did not identify its attested book",
        )
        require_labeled_text_value(
            output,
            "Attestation order",
            expected_head.operation_order,
            f"{config.label} {purpose} {operation_id}[text] did not publish genesis order",
        )
        require_labeled_text_value(
            output,
            "Attestation head",
            expected_head.operation_head,
            f"{config.label} {purpose} {operation_id}[text] did not publish genesis head",
        )
        return
    raise ReleaseSmokeFailure(
        f"{config.label} {purpose} {operation_id} advertised unsupported open-book mode "
        f"{output_mode}"
    )
