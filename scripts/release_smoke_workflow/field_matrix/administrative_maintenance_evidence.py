"""Maintenance response evidence for backup, restore, and rekey mutations."""

from __future__ import annotations

from ..attestation_head_checks import VerifiedAttestationHead
from ..support import require, require_labeled_text_value
from .administrative_attestation_output import _require_text_attestation
from .administrative_constants import _JSON_MODE
from .administrative_models import AdministrativeWorld
from .administrative_response import (
    _argument_value,
    _require_response_attestation_commit,
    _require_text_title,
    _response_payload,
)


def _assert_maintenance_response_evidence(
    world: AdministrativeWorld,
    operation_id: str,
    output_mode: str,
    output: str,
    arguments: tuple[str, ...],
    expected_head: VerifiedAttestationHead,
    label: str,
) -> None:
    text_titles = {
        "backup-book": "Book Backed Up",
        "restore-book": "Book Restored",
        "rekey-book": "Book Rekeyed",
    }
    if output_mode == _JSON_MODE:
        payload = _response_payload(world, operation_id, output, label)
        if operation_id == "backup-book":
            backup_id = _argument_value(arguments, "--backup-id", world, operation_id, label)
            require(
                payload.get("backupId") == backup_id,
                f"{world.config.label} {label} backup-book[json] did not retain its backup identity",
            )
        _require_response_attestation_commit(
            payload, expected_head, world, operation_id, "json", label
        )
        return
    _require_text_title(world, operation_id, output, text_titles[operation_id], label)
    if operation_id == "backup-book":
        require_labeled_text_value(
            output,
            "Backup ID",
            _argument_value(arguments, "--backup-id", world, operation_id, label),
            f"{world.config.label} {label} backup-book[text] did not retain its backup identity",
        )
    _require_text_attestation(
        output,
        world.config,
        label,
        operation_id,
        expected_head=expected_head,
    )
