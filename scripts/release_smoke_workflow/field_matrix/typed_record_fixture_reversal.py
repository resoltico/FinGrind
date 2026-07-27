"""Reversal typed-record fixture accounts."""

from __future__ import annotations

from .typed_record_models import AccountDeclaration

_REVERSAL_DECLARATIONS = (
    AccountDeclaration(
        "matrix-clearing",
        "Matrix Clearing",
        "ASSET",
        financial_position_line_classification="CURRENT_ASSET",
        cash_flow_asset_classification="NON_CASH",
    ),
)
