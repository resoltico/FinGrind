"""Historical interim-sweep and fiscal-year-close capability workflow."""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig
from .administrative_constants import (
    _HISTORICAL_BOOK_START,
    _HISTORICAL_CLOSE_YEAR,
    _HISTORICAL_INTERIM_THROUGH,
)
from .administrative_historical import (
    _require_live_historical_close_readiness,
    _run_historical_close_seed_postings,
)
from .administrative_modes import _modes_for
from .administrative_operation_runner import _run_arguments_mutation
from .administrative_world_bootstrap import _new_world
from .capabilities import CapabilityMatrix, OperationCapability


def _verify_period_close_modes(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
    operations: Mapping[str, OperationCapability],
    matrix: CapabilityMatrix,
) -> None:
    group = (operations["interim-result-sweep"], operations["fiscal-year-close"])
    for output_mode in _modes_for(*group):
        world = _new_world(
            config,
            operation_ids,
            operations,
            "historical-period-close",
            output_mode,
            book_start_effective_date=_HISTORICAL_BOOK_START,
        )
        _require_live_historical_close_readiness(world, matrix)
        _run_historical_close_seed_postings(world, matrix)
        _run_arguments_mutation(
            world,
            operations["interim-result-sweep"],
            ("--through", _HISTORICAL_INTERIM_THROUGH),
            output_mode,
            "interim-result-sweep capability mode",
        )
        _run_arguments_mutation(
            world,
            operations["fiscal-year-close"],
            ("--year", _HISTORICAL_CLOSE_YEAR),
            output_mode,
            "fiscal-year-close capability mode",
        )
