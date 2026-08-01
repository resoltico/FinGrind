"""Fixed-asset typed-record fixture requests and accounts."""

from __future__ import annotations

from .typed_record_models import AccountDeclaration, TypedRecordRequest
from .typed_record_payloads import _money, _posting_request

_FIXED_ASSET_ID = "matrix-delivery-van"

_FIXED_ASSET_DECLARATIONS = (
    AccountDeclaration(
        "delivery-van",
        "Delivery Van",
        "ASSET",
        financial_position_line_classification="NONCURRENT_ASSET",
        cash_flow_asset_classification="NON_CASH",
    ),
    AccountDeclaration(
        "delivery-van-accumulated-depreciation",
        "Delivery Van Accumulated Depreciation",
        "ASSET",
        financial_position_line_classification="NONCURRENT_ASSET",
        cash_flow_asset_classification="NON_CASH",
        contra_of_account_code="delivery-van",
    ),
    AccountDeclaration(
        "depreciation-expense",
        "Depreciation Expense",
        "EXPENSE",
        profit_and_loss_line_classification="DEPRECIATION_AND_AMORTIZATION",
    ),
    AccountDeclaration(
        "fixed-asset-disposal-gain",
        "Fixed Asset Disposal Gain",
        "REVENUE",
        profit_and_loss_line_classification="OTHER_REVENUE",
    ),
    AccountDeclaration(
        "fixed-asset-disposal-loss",
        "Fixed Asset Disposal Loss",
        "EXPENSE",
        profit_and_loss_line_classification="OTHER_EXPENSE",
    ),
)


def _fixed_asset_requests(request_prefix: str) -> tuple[TypedRecordRequest, ...]:
    return (
        TypedRecordRequest(
            "recordFixedAssetCapitalization",
            _posting_request(
                request_prefix,
                "record-fixed-asset-capitalization",
                "FIXED_ASSET_CAPITALIZATION",
                "2026-01-02",
                "supplier-invoice",
                {
                    "cashAccountCode": "cash",
                    "fixedAssetId": _FIXED_ASSET_ID,
                    "assetAccountCode": "delivery-van",
                    "accumulatedDepreciationAccountCode": "delivery-van-accumulated-depreciation",
                    "depreciationExpenseAccountCode": "depreciation-expense",
                    "disposalGainAccountCode": "fixed-asset-disposal-gain",
                    "disposalLossAccountCode": "fixed-asset-disposal-loss",
                    "cost": _money("1200000"),
                    "depreciationSchedule": {
                        "inServiceDate": "2026-01-02",
                        "usefulLifeMonths": 60,
                        "residualValue": _money("200000"),
                    },
                },
            ),
        ),
        TypedRecordRequest(
            "recordFixedAssetDepreciation",
            _posting_request(
                request_prefix,
                "record-fixed-asset-depreciation",
                "FIXED_ASSET_DEPRECIATION",
                "2026-01-03",
                "depreciation-schedule",
                {"fixedAssetId": _FIXED_ASSET_ID},
            ),
        ),
        TypedRecordRequest(
            "recordFixedAssetDisposal",
            _posting_request(
                request_prefix,
                "record-fixed-asset-disposal",
                "FIXED_ASSET_DISPOSAL",
                "2026-01-04",
                "asset-disposal-agreement",
                {
                    "cashAccountCode": "cash",
                    "fixedAssetId": _FIXED_ASSET_ID,
                    "proceeds": _money("1100000"),
                },
            ),
        ),
    )
