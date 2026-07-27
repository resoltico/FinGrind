"""Immutable retained-book facts shared between mutation and report scenarios."""

from __future__ import annotations

from dataclasses import dataclass

from ..models import ReleaseSmokeConfig


@dataclass(frozen=True)
class ReportBookContext:
    """One fresh retained book whose reports must expose a known semantic fact."""

    config: ReleaseSmokeConfig
    period_start: str
    period_end: str
    as_of: str
    account_code: str
    expected_report_tokens: tuple[tuple[str, str], ...]

    def expected_report_token(self, operation_id: str) -> str:
        """Return the semantic token that this book must expose for one report."""
        for routed_operation_id, token in self.expected_report_tokens:
            if routed_operation_id == operation_id:
                return token
        raise KeyError(f"retained report book has no expected token for operation {operation_id}")


@dataclass(frozen=True)
class TypedRecordMatrixWorlds:
    """The retained JSON worlds that make each specialized report substantive."""

    commercial: ReportBookContext
    inventory: ReportBookContext
    accrual: ReportBookContext
    payroll: ReportBookContext
    fixed_asset: ReportBookContext
    financing: ReportBookContext
    foreign_exchange: ReportBookContext
