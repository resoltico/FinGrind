"""Live query lookups and book-access argument construction."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_arguments import ATTESTATION_CUSTODIAN
from ..cli import run_cli
from ..models import ReleaseSmokeConfig
from ..support import parse_json_output, require
from .query_models import AttestationHeadFacts
from .query_response_support import _required_mapping, _required_text, _success_payload


def _first_posting_id(config: ReleaseSmokeConfig) -> str:
    output = run_cli(
        config,
        "list-postings",
        *_book_access_arguments(config, "--limit", "1"),
        "--output",
        "json",
    )
    envelope = parse_json_output(
        output,
        f"{config.label} field-matrix list-postings lookup did not emit valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{config.label} field-matrix list-postings lookup did not report ok status",
    )
    payload = envelope.get("payload")
    require(
        isinstance(payload, dict),
        f"{config.label} field-matrix list-postings lookup did not expose a payload object",
    )
    if not isinstance(payload, dict):
        raise TypeError("require must reject an invalid list-postings payload")
    postings = payload.get("postings")
    require(
        isinstance(postings, list) and bool(postings),
        f"{config.label} field-matrix list-postings lookup did not expose a posting",
    )
    if not isinstance(postings, list) or not postings:
        raise AssertionError("require must reject an empty posting list")
    first_posting = postings[0]
    require(
        isinstance(first_posting, dict),
        f"{config.label} field-matrix list-postings lookup exposed an invalid posting",
    )
    if not isinstance(first_posting, dict):
        raise TypeError("require must reject an invalid posting row")
    posting_id = first_posting.get("postingId")
    require(
        isinstance(posting_id, str) and bool(posting_id),
        f"{config.label} field-matrix list-postings lookup did not expose postingId",
    )
    if not isinstance(posting_id, str) or not posting_id:
        raise AssertionError("require must reject a missing posting id")
    return posting_id


def _attestation_key_id(config: ReleaseSmokeConfig) -> str:
    payload = _success_payload(
        config,
        "inspect-attestation-key-file",
        run_cli(
            config,
            "inspect-attestation-key-file",
            "--attestation-custodian",
            ATTESTATION_CUSTODIAN,
            "--attestation-key-file",
            config.attestation_founder_key.argument,
            "--output",
            "json",
        ),
    )
    return _required_text(
        payload,
        "keyId",
        f"{config.label} field-matrix inspect-attestation-key-file lookup",
    )


def _attestation_head_facts(config: ReleaseSmokeConfig) -> AttestationHeadFacts:
    payload = _success_payload(
        config,
        "verify-book",
        run_cli(
            config,
            "verify-book",
            *_book_access_arguments(config),
            "--output",
            "json",
        ),
    )
    purpose = f"{config.label} field-matrix verify-book lookup"
    verified_head = _required_mapping(payload, "verifiedAttestationHead", purpose)
    require(
        set(verified_head) == {"operationOrder", "operationHead"},
        f"{purpose} did not expose the current-protocol verified attestation head",
    )
    return AttestationHeadFacts(
        _required_text(payload, "bookId", purpose),
        _required_text(verified_head, "operationOrder", purpose),
        _required_text(verified_head, "operationHead", purpose),
        _required_text(payload, "previousHead", purpose),
    )


def _first_tax_registration_id(config: ReleaseSmokeConfig) -> str:
    payload = _success_payload(
        config,
        "list-tax-registrations",
        run_cli(
            config,
            "list-tax-registrations",
            *_book_access_arguments(config, "--limit", "1"),
            "--output",
            "json",
        ),
    )
    registrations = payload.get("registrations")
    require(
        isinstance(registrations, list) and bool(registrations),
        f"{config.label} field-matrix list-tax-registrations lookup did not expose a registration",
    )
    if not isinstance(registrations, list) or not registrations:
        raise AssertionError("require must reject an empty tax-registration list")
    registration = registrations[0]
    require(
        isinstance(registration, Mapping),
        f"{config.label} field-matrix list-tax-registrations lookup exposed an invalid registration",
    )
    if not isinstance(registration, Mapping):
        raise TypeError("require must reject a malformed tax-registration row")
    return _required_text(
        registration,
        "taxRegistrationId",
        f"{config.label} field-matrix list-tax-registrations lookup",
    )


def _book_access_arguments(config: ReleaseSmokeConfig, *extra_arguments: str) -> tuple[str, ...]:
    return (
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        *extra_arguments,
    )
