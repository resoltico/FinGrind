"""Posting preflight, post-entry, and execute-plan capability workflow."""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig
from .administrative_modes import _modes_for, _required_json_mode
from .administrative_request_runner import _run_request_mutation, _run_request_without_credentials
from .administrative_requests import (
    _account_request,
    _administrative_plan_request,
    _direct_journal_request,
)
from .administrative_world_bootstrap import _new_world
from .capabilities import OperationCapability


def _verify_posting_and_plan_modes(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
    operations: Mapping[str, OperationCapability],
) -> None:
    group = (
        operations["preflight-entry"],
        operations["post-entry"],
        operations["execute-plan"],
    )
    for output_mode in _modes_for(*group):
        world = _new_world(config, operation_ids, operations, "posting-plan", output_mode)
        clearing_account_code = "admin-journal-clearing"
        _run_request_mutation(
            world,
            operations["declare-account"],
            _account_request(
                clearing_account_code,
                "Administrative Journal Clearing",
                "ASSET",
                financial_position="CURRENT_ASSET",
                cash_flow="NON_CASH",
            ),
            _required_json_mode(operations["declare-account"]),
            "prepare non-typed journal clearing account",
        )
        preflight_request = _direct_journal_request(
            world,
            "preflight",
            "2026-02-02",
            clearing_account_code,
            "cash",
        )
        _run_request_without_credentials(
            world,
            operations["preflight-entry"],
            preflight_request,
            output_mode,
            "preflight-entry capability mode",
        )
        posting_request = _direct_journal_request(
            world,
            "post",
            "2026-02-03",
            clearing_account_code,
            "cash",
        )
        _run_request_mutation(
            world,
            operations["post-entry"],
            posting_request,
            output_mode,
            "post-entry capability mode",
        )
        _run_request_mutation(
            world,
            operations["execute-plan"],
            _administrative_plan_request(world, output_mode),
            output_mode,
            "execute-plan capability mode",
            extra_arguments=("--result-detail", "full"),
        )
