"""Canonical typed-record scenario catalogue and cross-fixture assembly."""

from __future__ import annotations

from .typed_record_fixture_accrual import _accrual_requests
from .typed_record_fixture_financing import _FINANCING_DECLARATIONS, _financing_requests
from .typed_record_fixture_fixed_asset import _FIXED_ASSET_DECLARATIONS, _fixed_asset_requests
from .typed_record_fixture_fx import _FOREIGN_EXCHANGE_DECLARATIONS, _foreign_exchange_requests
from .typed_record_fixture_inventory import _inventory_requests
from .typed_record_fixture_payroll import _PAYROLL_DECLARATIONS, _payroll_requests
from .typed_record_fixture_reversal import _REVERSAL_DECLARATIONS
from .typed_record_fixture_standard import _opening_position_requests, _standard_commercial_requests
from .typed_record_models import TypedRecordScenario

_TYPED_RECORD_SCENARIOS = (
    TypedRecordScenario(
        "standard-commercial",
        "OWNER_MANAGED_SERVICE",
        None,
        "ACCRUAL",
        (),
        _standard_commercial_requests,
    ),
    TypedRecordScenario(
        "inventory-lifecycle",
        "OWNER_MANAGED_TRADING",
        "WEIGHTED_AVERAGE",
        "ACCRUAL",
        (),
        _inventory_requests,
    ),
    TypedRecordScenario(
        "accrual-lifecycle",
        "OWNER_MANAGED_SERVICE",
        None,
        "ACCRUAL",
        (),
        _accrual_requests,
    ),
    TypedRecordScenario(
        "latvian-payroll-lifecycle",
        "OWNER_MANAGED_SERVICE",
        None,
        "ACCRUAL",
        _PAYROLL_DECLARATIONS,
        _payroll_requests,
    ),
    TypedRecordScenario(
        "fixed-asset-lifecycle",
        "OWNER_MANAGED_SERVICE",
        None,
        "ACCRUAL",
        _FIXED_ASSET_DECLARATIONS,
        _fixed_asset_requests,
    ),
    TypedRecordScenario(
        "financing-lifecycle",
        "OWNER_MANAGED_SERVICE",
        None,
        "ACCRUAL",
        _FINANCING_DECLARATIONS,
        _financing_requests,
    ),
    TypedRecordScenario(
        "foreign-exchange-lifecycle",
        "OWNER_MANAGED_SERVICE",
        None,
        "ACCRUAL",
        _FOREIGN_EXCHANGE_DECLARATIONS,
        _foreign_exchange_requests,
    ),
    TypedRecordScenario(
        "opening-position",
        "OWNER_MANAGED_SERVICE",
        None,
        "ACCRUAL",
        (),
        _opening_position_requests,
    ),
    TypedRecordScenario(
        "reversal",
        "OWNER_MANAGED_SERVICE",
        None,
        "ACCRUAL",
        _REVERSAL_DECLARATIONS,
        lambda _request_prefix: (),
    ),
)
