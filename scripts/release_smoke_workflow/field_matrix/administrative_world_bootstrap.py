"""Fresh administrative-world construction and attested genesis verification."""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import replace

from .. import fixtures, open_book_support, support
from ..models import ReleaseSmokeConfig
from . import (
    administrative_chain_state,
    administrative_key_generation,
    administrative_modes,
    administrative_operation_output,
    administrative_paths,
    mutation_evidence_bootstrap,
)
from .administrative_constants import _ADMINISTRATIVE_DIRECTORY, _JSON_MODE
from .administrative_models import AdministrativeWorld
from .capabilities import OperationCapability
from .output_provenance import record_proven_output_mode


def _new_world(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
    operations: Mapping[str, OperationCapability],
    scenario_id: str,
    output_mode: str,
    *,
    book_start_effective_date: str | None = None,
    bootstrap_output_mode: str | None = None,
) -> AdministrativeWorld:
    administrative_modes._require_mode_segment(output_mode)
    root = config.book.local_path.parent / _ADMINISTRATIVE_DIRECTORY / scenario_id / output_mode
    request_directory = root / "requests"
    artifact_directory = root / "artifacts"
    book = administrative_paths._path_from_anchor(config, root / "books" / "administrative.sqlite")
    book_key = administrative_paths._path_from_anchor(config, root / "keys" / "administrative.key")
    founder_key = administrative_paths._path_from_anchor(
        config, root / "credentials" / "founder.fgatk"
    )
    founder_passphrase = administrative_paths._path_from_anchor(
        config, root / "credentials" / "founder.passphrase"
    )
    for directory in (
        root,
        request_directory,
        artifact_directory,
        book.local_path.parent,
        book_key.local_path.parent,
        founder_key.local_path.parent,
    ):
        fixtures.prepare_owner_only_directory(directory)
    founder_passphrase.local_path.write_text(
        "administrative-matrix-founder-passphrase\n", encoding="utf-8"
    )
    if os.name == "posix":
        founder_passphrase.local_path.chmod(0o600)
    world_config = replace(
        config,
        label=f"{config.label} administrative {scenario_id} {output_mode}",
        request_prefix=f"{config.request_prefix}-administrative-{scenario_id}-{output_mode}",
        book=book,
        book_key=book_key,
        attestation_founder_key=founder_key,
        attestation_founder_passphrase=founder_passphrase,
        accounting_basis="ACCRUAL",
        book_start_effective_date=book_start_effective_date
        if book_start_effective_date is not None
        else config.book_start_effective_date,
        open_book_mode="book-key-file",
    )
    world = AdministrativeWorld(
        config=world_config,
        path_anchor_config=config,
        operation_ids=operation_ids,
        root=root,
        request_directory=request_directory,
        artifact_directory=artifact_directory,
    )
    selected_bootstrap_mode = bootstrap_output_mode or _JSON_MODE
    book_key_mode = administrative_modes._supported_mode(
        operations["generate-book-key-file"], selected_bootstrap_mode
    )
    credential_mode = administrative_modes._supported_mode(
        operations["generate-attestation-key-file"], selected_bootstrap_mode
    )
    administrative_key_generation._generate_book_key(
        world, operations["generate-book-key-file"], book_key_mode
    )
    administrative_key_generation._generate_attestation_key(
        world,
        operations["generate-attestation-key-file"],
        credential_mode,
        world.config.attestation_founder_key,
        world.config.attestation_founder_passphrase,
        "founder credential",
    )
    open_mode = administrative_modes._supported_mode(
        operations["open-book"], selected_bootstrap_mode
    )
    output = open_book_support.open_book(world.config, dict(operation_ids), output_mode=open_mode)
    genesis_head = administrative_chain_state._verified_head(
        world, "open-book fresh-world initialization"
    )
    administrative_chain_state._require_verified_append_transition(
        operations["open-book"].operation_id,
        None,
        genesis_head,
        world.config,
        "open-book fresh-world initialization",
    )
    support.require(
        world.config.book.local_path.is_file(),
        f"{world.config.label} open-book did not create its protected book",
    )
    mutation_evidence_bootstrap.assert_open_book_response(
        world.config,
        open_mode,
        output,
        genesis_head,
        "open-book fresh-world initialization",
    )
    administrative_operation_output._process_operation_output(
        operations["open-book"],
        open_mode,
        output,
        world.config,
        "open-book fresh-world initialization",
        before_head=None,
        after_head=genesis_head,
    )
    record_proven_output_mode(
        operations["open-book"],
        open_mode,
        output,
        world.config,
        "open-book fresh-world initialization",
    )
    return world
