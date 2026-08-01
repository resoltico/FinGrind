"""Synthetic contracts for protected-book pair retained-stage evidence."""

from __future__ import annotations

import os
import pathlib
import tempfile
from types import SimpleNamespace

from ..artifact_contracts import expected_public_artifact_path_hint
from ..models import SmokePath
from .field_matrix_query_identity_contract import require_rejected
from .pair_publication_output import require_maintenance_pair_publication_retention


def assert_pair_publication_retention_contract() -> None:
    """Require exact, retained final-and-stage facts for every protected-book pair."""
    with tempfile.TemporaryDirectory(prefix="fingrind-pair-retention-") as temporary_directory:
        root = pathlib.Path(temporary_directory)
        book = _published_path(root, "restored.sqlite", b"book")
        book_stage = _published_path(root, "restored.sqlite.fgar-stage", b"book-stage")
        generated_secret = _published_path(root, "restored.key", b"secret")
        generated_secret_stage = _published_path(root, "restored.key.fgar-stage", b"secret-stage")
        config = SimpleNamespace(label="synthetic pair retention", reported_work_root=None)
        require_maintenance_pair_publication_retention(
            "json",
            "",
            _pair_envelope(book, book_stage, generated_secret, generated_secret_stage),
            config,
            "valid JSON pair publication",
            book,
            generated_secret,
        )
        require_maintenance_pair_publication_retention(
            "text",
            _pair_text(book, book_stage, generated_secret, generated_secret_stage),
            None,
            config,
            "valid text pair publication",
            book,
            generated_secret,
        )
        missing_stage = _pair_envelope(book, book_stage, generated_secret, generated_secret_stage)
        payload = missing_stage["payload"]
        assert isinstance(payload, dict)
        retention = payload["pairPublicationRetention"]
        assert isinstance(retention, dict)
        generated_secret_fact = retention["generatedSecretPublication"]
        assert isinstance(generated_secret_fact, dict)
        del generated_secret_fact["retainedStage"]
        require_rejected(
            lambda: require_maintenance_pair_publication_retention(
                "json",
                "",
                missing_stage,
                config,
                "missing generated-secret retained stage",
                book,
                generated_secret,
            ),
            "retainedStage",
            "field matrix accepted a protected-book pair member without retained-stage evidence",
        )
        wrong_final = _pair_envelope(book, book_stage, generated_secret, generated_secret_stage)
        payload = wrong_final["payload"]
        assert isinstance(payload, dict)
        retention = payload["pairPublicationRetention"]
        assert isinstance(retention, dict)
        book_fact = retention["bookPublication"]
        assert isinstance(book_fact, dict)
        book_fact["path"] = generated_secret.argument
        require_rejected(
            lambda: require_maintenance_pair_publication_retention(
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


def _published_path(root: pathlib.Path, filename: str, contents: bytes) -> SmokePath:
    path = root / filename
    path.write_bytes(contents)
    os.chmod(path, 0o600)
    return SmokePath(pathlib.Path(filename), path, str(path))


def _pair_envelope(
    book: SmokePath,
    book_stage: SmokePath,
    generated_secret: SmokePath,
    generated_secret_stage: SmokePath,
) -> dict[str, object]:
    return {
        "payload": {
            "pairPublicationCompletion": "published",
            "pairPublicationRetention": {
                "bookPublication": {
                    "path": book.argument,
                    "retainedStage": book_stage.argument,
                },
                "generatedSecretPublication": {
                    "path": generated_secret.argument,
                    "retainedStage": generated_secret_stage.argument,
                },
            },
        }
    }


def _pair_text(
    book: SmokePath,
    book_stage: SmokePath,
    generated_secret: SmokePath,
    generated_secret_stage: SmokePath,
) -> str:
    return "\n".join(
        (
            "Published book file: " + expected_public_artifact_path_hint(book),
            "Book retained stage: " + expected_public_artifact_path_hint(book_stage),
            "Published generated-secret file: "
            + expected_public_artifact_path_hint(generated_secret),
            "Generated-secret retained stage: "
            + expected_public_artifact_path_hint(generated_secret_stage),
        )
    )
