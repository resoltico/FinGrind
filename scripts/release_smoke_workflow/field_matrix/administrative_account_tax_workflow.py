"""Account-registry and tax-registration administrative capability workflow."""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig
from .administrative_constants import _JSON_MODE
from .administrative_modes import _modes_for, _supported_mode
from .administrative_request_runner import _run_request_mutation
from .administrative_requests import _account_request, _tax_registration_request
from .administrative_world_bootstrap import _new_world
from .capabilities import OperationCapability


def _verify_account_and_tax_modes(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
    operations: Mapping[str, OperationCapability],
) -> None:
    group = (
        operations["declare-account"],
        operations["amend-account"],
        operations["retire-account"],
        operations["declare-tax-registration"],
    )
    for output_mode in _modes_for(*group):
        world = _new_world(config, operation_ids, operations, "account-tax", output_mode)
        setup_account_code = "admin-amend-target"
        _run_request_mutation(
            world,
            operations["declare-account"],
            _account_request(
                setup_account_code,
                "Administrative Amendment Target",
                "ASSET",
                financial_position="CURRENT_ASSET",
                cash_flow="NON_CASH",
            ),
            _supported_mode(operations["declare-account"], _JSON_MODE),
            "prepare amendable administrative account",
        )
        _run_request_mutation(
            world,
            operations["declare-account"],
            _account_request(
                "admin-declare-" + output_mode,
                "Administrative Declared Account",
                "ASSET",
                financial_position="CURRENT_ASSET",
                cash_flow="NON_CASH",
            ),
            output_mode,
            "declare-account capability mode",
        )
        _run_request_mutation(
            world,
            operations["amend-account"],
            _account_request(
                setup_account_code,
                "Administrative Amendment Target Renamed",
                "ASSET",
                financial_position="CURRENT_ASSET",
                cash_flow="NON_CASH",
            ),
            output_mode,
            "amend-account capability mode",
        )
        _run_request_mutation(
            world,
            operations["retire-account"],
            {"accountCode": setup_account_code},
            output_mode,
            "retire-account capability mode",
        )
        payable_account_code = "admin-tax-payable"
        recoverable_account_code = "admin-tax-recoverable"
        for account_code, account_name, account_type, classification in (
            (
                payable_account_code,
                "Administrative Tax Payable",
                "LIABILITY",
                "CURRENT_LIABILITY",
            ),
            (
                recoverable_account_code,
                "Administrative Tax Recoverable",
                "ASSET",
                "CURRENT_ASSET",
            ),
        ):
            _run_request_mutation(
                world,
                operations["declare-account"],
                _account_request(
                    account_code,
                    account_name,
                    account_type,
                    financial_position=classification,
                    cash_flow="NON_CASH" if account_type == "ASSET" else None,
                ),
                _supported_mode(operations["declare-account"], _JSON_MODE),
                f"prepare {account_code}",
            )
        _run_request_mutation(
            world,
            operations["declare-tax-registration"],
            _tax_registration_request(
                "admin-tax-registration-" + output_mode,
                payable_account_code,
                recoverable_account_code,
            ),
            output_mode,
            "declare-tax-registration capability mode",
        )
