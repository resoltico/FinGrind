"""Protected-book maintenance source-identity refusal checks."""

from __future__ import annotations

import os
from uuid import NAMESPACE_URL, uuid5

from .artifact_contracts import expected_reported_path
from .attestation_arguments import signing_credential_arguments
from .attestation_head_checks import verified_attestation_head
from .cli import run_cli_allow_failure
from .fixtures import prepare_owner_only_directory
from .models import ReleaseSmokeConfig, ReleaseSmokeFailure
from .scenario_paths import smoke_path_from_local
from .support import parse_json_output, require

_SOURCE_IDENTITY_DIRECTORY = "maintenance-source-identity"


def verify_source_artifact_identity_duplicate_refusal(
    config: ReleaseSmokeConfig, operation_ids: dict[str, str], error_exit_codes: dict[str, int]
) -> None:
    """Require source-identity admission to fail before backup target preparation."""
    root = config.work_root / _SOURCE_IDENTITY_DIRECTORY
    require(
        not root.exists(), f"{config.label} maintenance source-identity root already exists: {root}"
    )
    prepare_owner_only_directory(root)
    aliased_book_source = smoke_path_from_local(config, root / "live-book-key-alias.sqlite")
    refused_backup = smoke_path_from_local(config, root / "would-not-publish-backup.sqlite")
    refused_backup_key = smoke_path_from_local(config, root / "would-not-publish-backup.key")
    try:
        os.link(config.book_key.local_path, aliased_book_source.local_path)
    except OSError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} could not create the source-identity hard-link fixture"
        ) from exc
    require(
        aliased_book_source.local_path.is_file()
        and not aliased_book_source.local_path.is_symlink()
        and os.path.samefile(aliased_book_source.local_path, config.book_key.local_path),
        f"{config.label} source-identity fixture is not a real live-book-key hard link",
    )
    key_bytes_before = config.book_key.local_path.read_bytes()
    head_before = verified_attestation_head(
        config, operation_ids, "before source-artifact identity duplicate refusal"
    )
    output, exit_code = run_cli_allow_failure(
        config,
        operation_ids["backupBook"],
        "--book-file",
        aliased_book_source.argument,
        "--book-key-file",
        config.book_key.argument,
        "--backup-file",
        refused_backup.argument,
        "--new-backup-key-file",
        refused_backup_key.argument,
        "--backup-id",
        str(
            uuid5(
                NAMESPACE_URL,
                f"fingrind-release-smoke:{config.request_prefix}:source-artifact-identity",
            )
        ),
        *signing_credential_arguments(config),
        "--output",
        "json",
    )
    envelope = parse_json_output(
        output,
        f"{config.label} source-artifact identity duplicate refusal output was not valid JSON",
    )
    expected_exit_code = error_exit_codes.get("artifact-path-invalid")
    require(
        type(expected_exit_code) is int,
        f"{config.label} runtime contract did not publish artifact-path-invalid exit semantics",
    )
    expected_path = expected_reported_path(config, config.book_key)
    details = envelope.get("details")
    require(
        exit_code == expected_exit_code
        and envelope.get("status") == "rejected"
        and envelope.get("category") == "precondition"
        and envelope.get("code") == "artifact-path-invalid"
        and envelope.get("path") == expected_path
        and envelope.get("relatedPaths") == []
        and details
        == {
            "artifactRole": "live-book-key-source",
            "artifactPath": expected_path,
            "pathFailure": "source-artifact-identity-duplicated",
        },
        f"{config.label} source-artifact identity duplicate refusal did not publish the exact later-source artifact-path-invalid contract",
    )
    root_entry_names = {entry.name for entry in root.iterdir()}
    require(
        not refused_backup.local_path.exists()
        and not refused_backup_key.local_path.exists()
        and root_entry_names == {aliased_book_source.local_path.name},
        f"{config.label} source-artifact identity duplicate refusal created an output target, stage, or lease control",
    )
    require(
        config.book_key.local_path.read_bytes() == key_bytes_before
        and os.path.samefile(aliased_book_source.local_path, config.book_key.local_path),
        f"{config.label} source-artifact identity duplicate refusal changed a selected source",
    )
    require(
        verified_attestation_head(
            config, operation_ids, "after source-artifact identity duplicate refusal"
        )
        == head_before,
        f"{config.label} source-artifact identity duplicate refusal changed the attestation head",
    )
