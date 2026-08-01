"""Public read-back evidence for administrative mutation workflows."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_head_checks import VerifiedAttestationHead
from ..cli import run_cli
from ..support import parse_json_output, require
from .administrative_constants import _JSON_MODE
from .administrative_models import AdministrativeWorld, JsonObject
from .administrative_response import _payload


def _query_payload(
    world: AdministrativeWorld,
    operation_id: str,
    arguments: tuple[str, ...],
    label: str,
) -> JsonObject:
    output = run_cli(world.config, operation_id, *arguments, "--output", _JSON_MODE)
    envelope = parse_json_output(
        output,
        f"{world.config.label} {label} {operation_id} lookup did not emit valid JSON",
    )
    require(
        envelope.get("status") == "ok",
        f"{world.config.label} {label} {operation_id} lookup did not report ok status",
    )
    payload = _payload(envelope, world.config, label + " " + operation_id + " lookup")
    # Verification has its own attestational payload rather than the shared
    # query-family envelope used by the paged/posting routes.  Its identity is
    # proved by the verified chain coordinates in ``_verified_registry``;
    # demanding a fabricated ``family`` field here would turn a valid verifier
    # response into a false matrix failure.
    if operation_id != "verify-book":
        require(
            payload.get("family") == operation_id,
            f"{world.config.label} {label} {operation_id} lookup returned another query family",
        )
    return payload


def _persisted_account(world: AdministrativeWorld, account_code: str, label: str) -> JsonObject:
    payload = _query_payload(
        world,
        "list-accounts",
        (
            "--book-file",
            world.config.book.argument,
            "--book-key-file",
            world.config.book_key.argument,
            "--limit",
            "100",
        ),
        label,
    )
    accounts = payload.get("accounts")
    require(
        isinstance(accounts, list),
        f"{world.config.label} {label} list-accounts lookup did not expose accounts",
    )
    if not isinstance(accounts, list):
        raise TypeError("account state proof requires account rows")
    matches = [
        account
        for account in accounts
        if isinstance(account, dict) and account.get("accountCode") == account_code
    ]
    require(
        len(matches) == 1,
        f"{world.config.label} {label} list-accounts lookup did not expose one requested account",
    )
    if len(matches) != 1:
        raise AssertionError("account state proof requires one matching account")
    return matches[0]


def _persisted_tax_registration(
    world: AdministrativeWorld,
    registration_id: str,
    label: str,
) -> JsonObject:
    payload = _query_payload(
        world,
        "list-tax-registrations",
        (
            "--book-file",
            world.config.book.argument,
            "--book-key-file",
            world.config.book_key.argument,
            "--limit",
            "100",
        ),
        label,
    )
    registrations = payload.get("registrations")
    require(
        isinstance(registrations, list),
        f"{world.config.label} {label} tax-registration lookup did not expose registrations",
    )
    if not isinstance(registrations, list):
        raise TypeError("tax state proof requires registration rows")
    matches = [
        registration
        for registration in registrations
        if isinstance(registration, dict)
        and registration.get("taxRegistrationId") == registration_id
    ]
    require(
        len(matches) == 1,
        f"{world.config.label} {label} tax-registration lookup did not expose one requested registration",
    )
    if len(matches) != 1:
        raise AssertionError("tax state proof requires one matching registration")
    return matches[0]


def _persisted_posting(world: AdministrativeWorld, posting_id: str, label: str) -> JsonObject:
    payload = _query_payload(
        world,
        "get-posting",
        (
            "--book-file",
            world.config.book.argument,
            "--book-key-file",
            world.config.book_key.argument,
            "--posting-id",
            posting_id,
        ),
        label,
    )
    posting = payload.get("posting")
    require(
        isinstance(posting, dict),
        f"{world.config.label} {label} get-posting lookup did not expose posting",
    )
    if not isinstance(posting, dict):
        raise TypeError("posting state proof requires a posting object")
    return posting


def _verified_registry(
    world: AdministrativeWorld,
    expected_head: VerifiedAttestationHead,
    label: str,
) -> Mapping[str, object]:
    payload = _query_payload(
        world,
        "verify-book",
        (
            "--book-file",
            world.config.book.argument,
            "--book-key-file",
            world.config.book_key.argument,
        ),
        label,
    )
    verified_head = payload.get("verifiedAttestationHead")
    require(
        isinstance(verified_head, Mapping)
        and set(verified_head) == {"operationOrder", "operationHead"}
        and verified_head.get("operationOrder") == expected_head.operation_order
        and verified_head.get("operationHead") == expected_head.operation_head,
        f"{world.config.label} {label} verify-book registry lookup did not retain the mutation head",
    )
    registry = payload.get("registry")
    require(
        isinstance(registry, Mapping),
        f"{world.config.label} {label} verify-book lookup did not expose the attestation registry",
    )
    if not isinstance(registry, Mapping):
        raise TypeError("registry state proof requires registry object")
    return registry
