"""Stable modes, routing, and textual facts for administrative matrix scenarios."""

from __future__ import annotations

import re

from .scenario_matrix import ScenarioDomain

_ADMINISTRATIVE_DIRECTORY = "administrative matrix"
_JSON_MODE = "json"
_TEXT_MODE = "text"
_HISTORICAL_BOOK_START = "2025-01-01"
_HISTORICAL_CLOSE_YEAR = "2025"
_HISTORICAL_INTERIM_THROUGH = "2025-06-30"
_MODE_SEGMENT = re.compile(r"[a-z0-9][a-z0-9-]*")
_BOOK_KEY_TEXT = re.compile(r"[A-Za-z0-9_-]{32,}")
_ZERO_HEAD = "0" * 64

_TEXT_ARTIFACT_LABELS = {
    ("generate-book-key-file", "book-key-file"): "Book key file",
    ("generate-attestation-key-file", "attestation-key-file"): "Attestation key file",
    ("backup-book", "backup-file"): "Backup file",
    ("backup-book", "backup-key-file"): "Backup key file",
    ("restore-book", "book-file"): "Book file",
    ("restore-book", "book-key-file"): "Book key file",
    ("rekey-book", "book-key-file"): "New book key file",
}

_TEXT_RETAINED_STAGE_LABELS = {
    ("generate-book-key-file", "book-key-file"): "Retained stage",
    ("backup-book", "backup-file"): "Book retained stage",
    ("backup-book", "backup-key-file"): "Generated-secret retained stage",
    ("restore-book", "book-file"): "Book retained stage",
    ("restore-book", "book-key-file"): "Generated-secret retained stage",
    ("rekey-book", "book-key-file"): "Generated-secret retained stage",
}

_TEXT_PUBLICATION_TRANSACTION_LABELS = {
    ("generate-attestation-key-file", "attestation-key-file"): "Publication transaction",
}

_ADMINISTRATIVE_DOMAINS = frozenset(
    {
        ScenarioDomain.BOOK_LIFECYCLE,
        ScenarioDomain.KEY_MANAGEMENT,
        ScenarioDomain.BOOK_MAINTENANCE,
        ScenarioDomain.ATTESTATION_REGISTRY,
        ScenarioDomain.ACCOUNT_REGISTRY,
        ScenarioDomain.TAX_ADMINISTRATION,
        ScenarioDomain.PERIOD_CLOSE,
        ScenarioDomain.PLAN,
        ScenarioDomain.POSTING,
    }
)

_ADMINISTRATIVE_OPERATION_IDS = frozenset(
    {
        "generate-book-key-file",
        "open-book",
        "generate-attestation-key-file",
        "declare-account",
        "amend-account",
        "retire-account",
        "declare-tax-registration",
        "enroll-key",
        "rollover-key",
        "revoke-key",
        "alter-policy",
        "interim-result-sweep",
        "fiscal-year-close",
        "execute-plan",
        "preflight-entry",
        "post-entry",
        "backup-book",
        "rekey-book",
        "restore-book",
    }
)
