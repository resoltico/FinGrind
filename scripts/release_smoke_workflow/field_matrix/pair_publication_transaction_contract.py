"""Synthetic contracts for final-only protected-book pair transaction evidence."""

from __future__ import annotations

import os
import pathlib
import tempfile
from types import SimpleNamespace

from ..artifact_contracts import expected_public_artifact_path_hint
from ..models import SmokePath
from .administrative_maintenance_artifacts import (
    require_maintenance_artifact_publication_transaction,
)
from .capabilities import ArtifactCapability, OperationCapability
from .field_matrix_query_identity_contract import require_rejected
from .pair_publication_output import require_maintenance_pair_publication_transaction


def assert_pair_publication_transaction_contract() -> None:
    """Require exact final paths and a complete ID-only transaction for every pair."""
    with tempfile.TemporaryDirectory(prefix="fingrind-pair-transaction-") as temporary_directory:
        root = pathlib.Path(temporary_directory)
        book = _published_path(root, "restored.sqlite", b"book")
        generated_secret = _published_path(root, "restored.key", b"secret")
        config = SimpleNamespace(label="synthetic pair transaction", reported_work_root=None)
        require_maintenance_pair_publication_transaction(
            "json",
            "",
            _pair_envelope(book, generated_secret),
            config,
            "valid JSON pair publication",
            book,
            generated_secret,
        )
        require_maintenance_pair_publication_transaction(
            "text",
            _pair_text(book, generated_secret),
            None,
            config,
            "valid text pair publication",
            book,
            generated_secret,
        )
        missing_transaction = _pair_envelope(book, generated_secret)
        payload = missing_transaction["payload"]
        assert isinstance(payload, dict)
        publication = payload["pairPublication"]
        assert isinstance(publication, dict)
        del publication["publicationTransaction"]
        require_rejected(
            lambda: require_maintenance_pair_publication_transaction(
                "json",
                "",
                missing_transaction,
                config,
                "missing publication transaction",
                book,
                generated_secret,
            ),
            "publicationTransaction",
            "field matrix accepted a protected-book pair without transaction evidence",
        )
        wrong_final = _pair_envelope(book, generated_secret)
        payload = wrong_final["payload"]
        assert isinstance(payload, dict)
        publication = payload["pairPublication"]
        assert isinstance(publication, dict)
        book_fact = publication["bookPublication"]
        assert isinstance(book_fact, dict)
        book_fact["path"] = generated_secret.argument
        require_rejected(
            lambda: require_maintenance_pair_publication_transaction(
                "json",
                "",
                wrong_final,
                config,
                "wrong book final path",
                book,
                generated_secret,
            ),
            "bookPublication",
            "field matrix accepted a pair fact attached to the other member's final artifact",
        )
        _assert_maintenance_artifact_transaction_contract(config, book, generated_secret)


def _assert_maintenance_artifact_transaction_contract(
    config: SimpleNamespace, book: SmokePath, generated_secret: SmokePath
) -> None:
    operation = OperationCapability(
        "backup-book",
        "Backup book",
        "maintenance",
        ("json", "text"),
        (
            ArtifactCapability("backup-file", "--backup-file <path>"),
            ArtifactCapability("backup-key-file", "--new-backup-key-file <path>"),
        ),
    )
    artifacts = {
        operation.artifact_outputs[0]: book,
        operation.artifact_outputs[1]: generated_secret,
    }
    require_maintenance_artifact_publication_transaction(
        "json",
        "",
        _maintenance_envelope(book, generated_secret),
        operation,
        artifacts,
        config,
        "valid maintenance artifact transaction",
    )
    require_maintenance_artifact_publication_transaction(
        "text",
        _maintenance_text(book, generated_secret),
        None,
        operation,
        artifacts,
        config,
        "valid maintenance artifact transaction",
    )
    retained_stage = _maintenance_envelope(book, generated_secret)
    artifacts_payload = retained_stage["artifacts"]
    assert isinstance(artifacts_payload, list)
    first_artifact = artifacts_payload[0]
    assert isinstance(first_artifact, dict)
    first_artifact["retainedStage"] = "private.backup.stage"
    require_rejected(
        lambda: require_maintenance_artifact_publication_transaction(
            "json",
            "",
            retained_stage,
            operation,
            artifacts,
            config,
            "maintenance artifact with a retained stage",
        ),
        "private retainedStage",
        "field matrix accepted transaction-published maintenance output with a retained stage",
    )


def _published_path(root: pathlib.Path, filename: str, contents: bytes) -> SmokePath:
    path = root / filename
    path.write_bytes(contents)
    os.chmod(path, 0o600)
    return SmokePath(pathlib.Path(filename), path, str(path))


def _pair_envelope(book: SmokePath, generated_secret: SmokePath) -> dict[str, object]:
    return {
        "payload": {
            "pairPublicationCompletion": "published",
            "pairPublication": {
                "bookPublication": {"path": book.argument},
                "generatedSecretPublication": {"path": generated_secret.argument},
                "publicationTransaction": {
                    "id": "0123456789abcdef0123456789abcdef",
                    "state": "complete",
                    "commitOutcome": "all-committed",
                    "cleanupOutcome": "complete",
                },
            },
        }
    }


def _maintenance_envelope(book: SmokePath, generated_secret: SmokePath) -> dict[str, object]:
    pair_envelope = _pair_envelope(book, generated_secret)
    pair_envelope["artifacts"] = [
        {
            "format": "backup-file",
            "path": book.argument,
            "publicationTransaction": _publication_transaction(),
        },
        {
            "format": "backup-key-file",
            "path": generated_secret.argument,
            "publicationTransaction": _publication_transaction(),
        },
    ]
    return pair_envelope


def _publication_transaction() -> dict[str, str]:
    return {
        "id": "0123456789abcdef0123456789abcdef",
        "state": "complete",
        "commitOutcome": "all-committed",
        "cleanupOutcome": "complete",
    }


def _pair_text(book: SmokePath, generated_secret: SmokePath) -> str:
    return "\n".join(
        (
            "Published book file: " + expected_public_artifact_path_hint(book),
            "Published generated-secret file: "
            + expected_public_artifact_path_hint(generated_secret),
            "Publication transaction: 0123456789abcdef0123456789abcdef",
        )
    )


def _maintenance_text(book: SmokePath, generated_secret: SmokePath) -> str:
    return "\n".join(
        (
            "Backup file: " + expected_public_artifact_path_hint(book),
            "Backup key file: " + expected_public_artifact_path_hint(generated_secret),
            _pair_text(book, generated_secret),
        )
    )
