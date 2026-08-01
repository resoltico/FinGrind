"""Isolated book setup and write-path evidence for the attestation scale scenario."""

from __future__ import annotations

from dataclasses import replace
from pathlib import Path

from ..attestation_arguments import signing_credential_arguments
from ..attestation_head_checks import (
    AttestationCommit,
    attestation_commit_from_payload,
    verified_attestation_head,
)
from ..cli import run_cli
from ..fixture_payloads import sale_request
from ..fixture_writers import write_json
from ..fixtures import prepare_owner_only_directory
from ..models import ReleaseSmokeConfig
from ..open_book_support import open_book
from ..scenario_paths import smoke_path_from_local
from ..support import (
    parse_json_output,
    require,
    require_bool,
    require_string,
    required_mapping,
)
from .attestation_scale_contract import (
    SCALE_DIRECTORY,
    SCALE_EFFECTIVE_DATE,
    SCALE_POSTING_COUNT,
)


def prepare_scale_world(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> tuple[ReleaseSmokeConfig, Path]:
    """Create and attest one isolated protected book for the scale proof."""
    root = config.work_root / SCALE_DIRECTORY
    require(
        not root.exists(),
        f"{config.label} attestation provenance scale root already exists: {root}",
    )
    prepare_owner_only_directory(root)
    book_path = smoke_path_from_local(config, root / "books" / "scale.sqlite")
    book_key_path = smoke_path_from_local(config, root / "keys" / "scale.key")
    request_directory = root / "requests"
    for directory in (book_path.local_path.parent, book_key_path.local_path.parent):
        prepare_owner_only_directory(directory)
    request_directory.mkdir(parents=True, exist_ok=False)

    scale_config = replace(
        config,
        label=f"{config.label} attestation provenance scale",
        book=book_path,
        book_key=book_key_path,
        open_book_mode="book-key-file",
        request_prefix=f"{config.request_prefix}-attestation-provenance-scale",
    )
    key_envelope = parse_json_output(
        run_cli(
            scale_config,
            operation_ids["generateBookKeyFile"],
            "--new-book-key-file",
            scale_config.book_key.argument,
            "--output",
            "json",
        ),
        f"{scale_config.label} generate-book-key-file output was not valid JSON",
    )
    require(
        key_envelope.get("status") == "ok" and scale_config.book_key.local_path.is_file(),
        f"{scale_config.label} did not generate a usable isolated book key",
    )
    open_envelope = parse_json_output(
        open_book(scale_config, operation_ids),
        f"{scale_config.label} open-book output was not valid JSON",
    )
    require(
        open_envelope.get("status") == "ok" and scale_config.book.local_path.is_file(),
        f"{scale_config.label} did not create its isolated protected book",
    )
    genesis_head = verified_attestation_head(
        scale_config,
        operation_ids,
        "isolated attestation provenance scale genesis",
    )
    require(
        genesis_head.operation_order == "0",
        f"{scale_config.label} isolated book did not begin at attestation genesis",
    )
    return scale_config, request_directory


def record_scale_postings(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    request_directory: Path,
) -> tuple[dict[str, AttestationCommit], list[str]]:
    """Record the isolated scale posting set and retain each append commitment."""
    commits_by_posting_id: dict[str, AttestationCommit] = {}
    posting_ids: list[str] = []
    for index in range(1, SCALE_POSTING_COUNT + 1):
        request_path = smoke_path_from_local(
            config,
            request_directory / f"sale-{index:02d}.json",
        )
        write_json(
            request_path.local_path,
            sale_request(
                request_prefix=config.request_prefix,
                effective_date=SCALE_EFFECTIVE_DATE,
                cash_account_code=config.starter_cash_account_code,
                revenue_account_code=config.starter_revenue_account_code,
                minor_units=str(1000 + index),
                evidence_suffix=f"scale-{index}",
                command_suffix=f"scale-{index}",
                idempotency_suffix=f"scale-{index}",
                causation_suffix=f"scale-{index}",
            ),
        )
        envelope = parse_json_output(
            run_cli(
                config,
                operation_ids["recordSaleSettled"],
                "--book-file",
                config.book.argument,
                "--book-key-file",
                config.book_key.argument,
                "--request-file",
                request_path.argument,
                *signing_credential_arguments(config),
                "--output",
                "json",
            ),
            f"{config.label} scale posting {index} output was not valid JSON",
        )
        require(
            envelope.get("status") == "ok",
            f"{config.label} scale posting {index} did not report ok status",
        )
        payload = dict(required_mapping(envelope, "payload"))
        posting_id = require_string(payload, "postingId")
        commit = attestation_commit_from_payload(payload, config.label, f"scale posting {index}")
        require(
            require_bool(payload, "idempotentReplay") is False
            and commit.operation_order == str(index)
            and posting_id not in commits_by_posting_id,
            f"{config.label} scale posting {index} did not append one unique operation",
        )
        commits_by_posting_id[posting_id] = commit
        posting_ids.append(posting_id)
    return commits_by_posting_id, posting_ids
