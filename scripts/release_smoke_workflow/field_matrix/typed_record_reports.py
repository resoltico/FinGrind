"""Retained report-world assembly for typed-record lifecycle scenarios."""

from __future__ import annotations

from collections.abc import Mapping

from ..support import require
from .report_contexts import ReportBookContext, TypedRecordMatrixWorlds
from .typed_record_constants import _STANDARD_COMMERCIAL_REPORT_TOKENS
from .typed_record_fixture_accrual import _PREPAYMENT_CUTOFF_ID
from .typed_record_fixture_financing import _FINANCING_ARRANGEMENT_ID
from .typed_record_fixture_fixed_asset import _FIXED_ASSET_ID
from .typed_record_fixture_fx import _FOREIGN_CURRENCY_OBLIGATION_ID
from .typed_record_fixture_payroll import _PAYROLL_RUN_ID
from .typed_record_models import TypedRecordWorld


def _typed_record_report_worlds(
    retained_json_worlds: Mapping[str, TypedRecordWorld],
) -> TypedRecordMatrixWorlds:
    return TypedRecordMatrixWorlds(
        commercial=_report_book_context(
            retained_json_worlds,
            "standard-commercial",
            "2026-01-02",
            "2026-01-09",
            "2026-01-09",
            _STANDARD_COMMERCIAL_REPORT_TOKENS,
        ),
        inventory=_report_book_context(
            retained_json_worlds,
            "inventory-lifecycle",
            "2026-01-02",
            "2026-01-08",
            "2026-01-08",
            (("inventory-valuation", "inventory"),),
        ),
        accrual=_report_book_context(
            retained_json_worlds,
            "accrual-lifecycle",
            "2026-01-02",
            "2026-01-06",
            "2026-01-06",
            (("accrual-cutoff-schedule", _PREPAYMENT_CUTOFF_ID),),
        ),
        payroll=_report_book_context(
            retained_json_worlds,
            "latvian-payroll-lifecycle",
            "2026-01-31",
            "2026-02-24",
            "2026-02-24",
            (("latvian-payroll-register", _PAYROLL_RUN_ID),),
        ),
        fixed_asset=_report_book_context(
            retained_json_worlds,
            "fixed-asset-lifecycle",
            "2026-01-02",
            "2026-01-04",
            "2026-01-04",
            (("fixed-asset-register", _FIXED_ASSET_ID),),
        ),
        financing=_report_book_context(
            retained_json_worlds,
            "financing-lifecycle",
            "2026-01-02",
            "2026-01-05",
            "2026-01-05",
            (("financing-register", _FINANCING_ARRANGEMENT_ID),),
        ),
        foreign_exchange=_report_book_context(
            retained_json_worlds,
            "foreign-exchange-lifecycle",
            "2026-01-02",
            "2026-01-03",
            "2026-01-03",
            (("realized-foreign-exchange-register", _FOREIGN_CURRENCY_OBLIGATION_ID),),
        ),
    )


def _report_book_context(
    retained_json_worlds: Mapping[str, TypedRecordWorld],
    scenario_id: str,
    period_start: str,
    period_end: str,
    as_of: str,
    expected_report_tokens: tuple[tuple[str, str], ...],
) -> ReportBookContext:
    world = retained_json_worlds.get(scenario_id)
    require(
        world is not None,
        f"typed-record matrix did not retain its {scenario_id} JSON report world",
    )
    if world is None:
        raise AssertionError("require must reject a missing retained JSON world")
    return ReportBookContext(
        config=world.config,
        period_start=period_start,
        period_end=period_end,
        as_of=as_of,
        account_code="cash",
        expected_report_tokens=expected_report_tokens,
    )
