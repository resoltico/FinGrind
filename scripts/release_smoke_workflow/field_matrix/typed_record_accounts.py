"""Supporting-account declaration workflow for typed-record scenarios."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_arguments import signing_credential_arguments
from ..attestation_head_checks import verified_attestation_head
from ..cli import run_cli
from .context import record_new_attestation_append
from .mutation_evidence_accounts import (
    assert_account_mutation_response,
    assert_persisted_account_state,
)
from .output_provenance import record_proven_output_mode
from .typed_record_constants import _JSON_MODE
from .typed_record_evidence import _persisted_account
from .typed_record_models import TypedRecordScenario, TypedRecordWorld
from .typed_record_output import (
    _operation,
    _operation_capability,
    _successful_envelope,
)
from .typed_record_paths import _request_path, _write_json


def _declare_supporting_accounts(
    world: TypedRecordWorld,
    operation_ids: Mapping[str, str],
    scenario: TypedRecordScenario,
) -> None:
    for index, declaration in enumerate(scenario.declarations, start=1):
        request_path = _request_path(world, f"{index:02d}-declare-{declaration.account_code}.json")
        _write_json(request_path.local_path, declaration.request())
        operation_id = _operation(operation_ids, "declareAccount")
        before_head = verified_attestation_head(
            world.config,
            dict(operation_ids),
            f"typed-record supporting account {declaration.account_code} before",
        )
        output = run_cli(
            world.config,
            operation_id,
            "--book-file",
            world.config.book.argument,
            "--book-key-file",
            world.config.book_key.argument,
            "--request-file",
            request_path.argument,
            *signing_credential_arguments(world.config),
            "--output",
            _JSON_MODE,
        )
        envelope = _successful_envelope(
            output,
            world.config,
            f"declare supporting account {declaration.account_code}",
        )
        after_head = verified_attestation_head(
            world.config,
            dict(operation_ids),
            f"typed-record supporting account {declaration.account_code} after",
        )
        account_evidence = assert_account_mutation_response(
            world.config,
            operation_id,
            declaration.request(),
            _JSON_MODE,
            output,
            after_head,
            f"declare supporting account {declaration.account_code}",
        )
        assert_persisted_account_state(
            _persisted_account(
                world,
                account_evidence.account_code,
                f"supporting account {declaration.account_code}",
            ),
            account_evidence,
            purpose=f"{world.config.label} supporting account {declaration.account_code}",
        )
        record_new_attestation_append(
            operation_id,
            envelope,
            before_head=before_head,
            after_head=after_head,
        )
        record_proven_output_mode(
            _operation_capability(operation_id),
            _JSON_MODE,
            output,
            world.config,
            f"declare supporting account {declaration.account_code}",
        )
