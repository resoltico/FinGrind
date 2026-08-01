"""Foreign-exchange typed-record fixture requests and accounts."""

from __future__ import annotations

from .typed_record_models import AccountDeclaration, TypedRecordRequest
from .typed_record_payloads import _foreign_exchange, _posting_request

_FOREIGN_CURRENCY_OBLIGATION_ID = "matrix-foreign-currency-obligation"

_FOREIGN_EXCHANGE_DECLARATIONS = (
    AccountDeclaration(
        "realized-fx-gain",
        "Realized Foreign Exchange Gain",
        "REVENUE",
        profit_and_loss_line_classification="FINANCE_INCOME",
    ),
    AccountDeclaration(
        "realized-fx-loss",
        "Realized Foreign Exchange Loss",
        "EXPENSE",
        profit_and_loss_line_classification="FINANCE_EXPENSE",
    ),
)


def _foreign_exchange_requests(request_prefix: str) -> tuple[TypedRecordRequest, ...]:
    return (
        TypedRecordRequest(
            "recordForeignCurrencyObligation",
            _posting_request(
                request_prefix,
                "record-foreign-currency-obligation",
                "FOREIGN_CURRENCY_OBLIGATION",
                "2026-01-02",
                "foreign-currency-invoice",
                {
                    "receivableAccountCode": "accounts-receivable",
                    "revenueAccountCode": "service-revenue",
                    "foreignCurrencyObligationId": _FOREIGN_CURRENCY_OBLIGATION_ID,
                    "realizedGainAccountCode": "realized-fx-gain",
                    "realizedLossAccountCode": "realized-fx-loss",
                    "foreignExchange": _foreign_exchange("110000", "2026-01-02"),
                },
            ),
        ),
        TypedRecordRequest(
            "recordRealizedForeignExchangeSettlement",
            _posting_request(
                request_prefix,
                "record-realized-foreign-exchange-settlement",
                "REALIZED_FOREIGN_EXCHANGE_SETTLEMENT",
                "2026-01-03",
                "settlement-confirmation",
                {
                    "cashAccountCode": "cash",
                    "foreignCurrencyObligationId": _FOREIGN_CURRENCY_OBLIGATION_ID,
                    "foreignExchange": _foreign_exchange("115000", "2026-01-03"),
                },
            ),
        ),
    )
