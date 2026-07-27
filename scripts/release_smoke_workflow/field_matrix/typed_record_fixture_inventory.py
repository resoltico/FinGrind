"""Inventory-lifecycle typed-record fixture requests."""

from __future__ import annotations

from .typed_record_models import TypedRecordRequest
from .typed_record_payloads import _money, _posting_request


def _inventory_requests(request_prefix: str) -> tuple[TypedRecordRequest, ...]:
    return (
        TypedRecordRequest(
            "recordPurchaseSettled",
            _posting_request(
                request_prefix,
                "record-purchase-settled",
                "PURCHASE_SETTLED",
                "2026-01-02",
                "purchase-receipt",
                {
                    "cashAccountCode": "cash",
                    "inventoryAccountCode": "inventory",
                    "quantity": "10",
                    "unitCost": _money("100"),
                },
            ),
        ),
        TypedRecordRequest(
            "recordPurchaseOnCredit",
            _posting_request(
                request_prefix,
                "record-purchase-on-credit",
                "PURCHASE_ON_CREDIT",
                "2026-01-03",
                "supplier-invoice",
                {
                    "payableAccountCode": "accounts-payable",
                    "inventoryAccountCode": "inventory",
                    "quantity": "10",
                    "unitCost": _money("120"),
                },
            ),
        ),
        TypedRecordRequest(
            "recordInventoryCapitalizationSettled",
            _posting_request(
                request_prefix,
                "record-inventory-capitalization-settled",
                "INVENTORY_CAPITALIZATION_SETTLED",
                "2026-01-04",
                "landed-cost-invoice",
                {
                    "cashAccountCode": "cash",
                    "inventoryAccountCode": "inventory",
                    "amount": _money("50"),
                },
            ),
        ),
        TypedRecordRequest(
            "recordInventoryCapitalizationOnCredit",
            _posting_request(
                request_prefix,
                "record-inventory-capitalization-on-credit",
                "INVENTORY_CAPITALIZATION_ON_CREDIT",
                "2026-01-05",
                "landed-cost-invoice",
                {
                    "payableAccountCode": "accounts-payable",
                    "inventoryAccountCode": "inventory",
                    "amount": _money("50"),
                },
            ),
        ),
        TypedRecordRequest(
            "recordInventoryWriteDown",
            _posting_request(
                request_prefix,
                "record-inventory-write-down",
                "INVENTORY_WRITE_DOWN",
                "2026-01-06",
                "inventory-write-down-assessment",
                {
                    "inventoryAccountCode": "inventory",
                    "writeDownLossAccountCode": "inventory-write-down-loss",
                    "amount": _money("50"),
                },
            ),
        ),
        TypedRecordRequest(
            "recordInventoryShrinkage",
            _posting_request(
                request_prefix,
                "record-inventory-shrinkage",
                "INVENTORY_SHRINKAGE",
                "2026-01-07",
                "inventory-count-sheet",
                {
                    "inventoryAccountCode": "inventory",
                    "shrinkageLossAccountCode": "inventory-shrinkage-loss",
                    "quantity": "1",
                },
            ),
        ),
        TypedRecordRequest(
            "recordInventoryCountIncrease",
            _posting_request(
                request_prefix,
                "record-inventory-count-increase",
                "INVENTORY_COUNT_INCREASE",
                "2026-01-08",
                "inventory-count-sheet",
                {
                    "inventoryAccountCode": "inventory",
                    "countGainAccountCode": "inventory-count-gain",
                    "quantity": "2",
                    "unitCost": _money("110"),
                },
            ),
        ),
    )
