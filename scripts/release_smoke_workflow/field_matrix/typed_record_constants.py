"""Stable identifiers and mode names shared by typed-record matrix modules."""

from __future__ import annotations

_FIELD_MATRIX_DIRECTORY = "typed-record matrix"
_JSON_MODE = "json"
_TEXT_MODE = "text"
_STANDARD_COMMERCIAL_REPORT_TOKENS = (
    ("account-balance", "cash"),
    ("trial-balance", "cash"),
    ("account-ledger", "cash"),
    ("period-summary", "cash"),
    ("financial-position", "cash"),
    ("income-statement", "service-revenue"),
    ("cash-flow-statement", "service-revenue"),
    ("changes-in-equity", "owner-capital"),
)
