"""Fresh protected-book bootstrap for one typed-record output-mode scenario."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import replace

from .. import (
    attestation_head_checks,
    fixtures,
    open_book_support,
    support,
)
from ..models import ReleaseSmokeConfig
from .context import record_new_attestation_append
from .mutation_evidence_bootstrap import assert_open_book_response
from .output_provenance import record_proven_output_mode
from .typed_record_constants import _JSON_MODE
from .typed_record_key_provisioning import _provision_world_keys
from .typed_record_models import TypedRecordScenario, TypedRecordWorld
from .typed_record_output import (
    _operation,
    _operation_capability,
    _successful_envelope,
)
from .typed_record_paths import _world_root, _world_smoke_path


def _prepare_world(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
    scenario: TypedRecordScenario,
    output_mode: str,
) -> TypedRecordWorld:
    root = _world_root(config, scenario.scenario_id, output_mode)
    request_directory = root / "requests"
    book_path = _world_smoke_path(config, root / "books" / "typed-record.sqlite")
    book_key_path = _world_smoke_path(config, root / "keys" / "typed-record.key")
    founder_key_path = _world_smoke_path(config, root / "credentials" / "founder.fgatk")
    founder_passphrase_path = _world_smoke_path(config, root / "credentials" / "founder.passphrase")
    for directory in (
        book_path.local_path.parent,
        book_key_path.local_path.parent,
        founder_key_path.local_path.parent,
    ):
        fixtures.prepare_owner_only_directory(directory)
    request_directory.mkdir(parents=True, exist_ok=True)
    founder_passphrase_path.local_path.write_text(
        "typed-record-matrix-founder-passphrase\n", encoding="utf-8"
    )
    fixtures.prepare_owner_only_file(founder_passphrase_path.local_path)

    world_config = replace(
        config,
        label=f"{config.label} typed-record {scenario.scenario_id} {output_mode}",
        request_prefix=f"{config.request_prefix}-typed-record-{scenario.scenario_id}-{output_mode}",
        book=book_path,
        book_key=book_key_path,
        attestation_founder_key=founder_key_path,
        attestation_founder_passphrase=founder_passphrase_path,
        book_template_id=scenario.book_template_id,
        inventory_costing_doctrine=scenario.inventory_costing_doctrine,
        accounting_basis=scenario.accounting_basis,
        open_book_mode="book-key-file",
    )
    _provision_world_keys(world_config, operation_ids)

    open_operation_id = _operation(operation_ids, "openBook")
    open_output = open_book_support.open_book(
        world_config,
        dict(operation_ids),
        output_mode=_JSON_MODE,
    )
    open_envelope = _successful_envelope(
        open_output,
        world_config,
        "open-book",
    )
    open_head = attestation_head_checks.verified_attestation_head(
        world_config,
        dict(operation_ids),
        "typed-record world open-book genesis",
    )
    support.require(
        world_config.book.local_path.is_file(),
        f"{world_config.label} open-book did not create its protected book",
    )
    assert_open_book_response(
        world_config,
        _JSON_MODE,
        open_output,
        open_head,
        "typed-record world open-book genesis",
    )
    record_new_attestation_append(
        open_operation_id,
        open_envelope,
        before_head=None,
        after_head=open_head,
    )
    record_proven_output_mode(
        _operation_capability(open_operation_id),
        _JSON_MODE,
        open_output,
        world_config,
        "open-book",
    )
    return TypedRecordWorld(world_config, request_directory, config)
