"""Historical-period-close preparation and readiness evidence."""

from __future__ import annotations

from ..support import require
from .administrative_constants import _HISTORICAL_BOOK_START
from .administrative_models import AdministrativeWorld, JsonObject
from .administrative_modes import _required_json_mode
from .administrative_operation_runner import _run_operation
from .administrative_request_runner import _run_request_mutation
from .administrative_requests import _money, _typed_cash_request
from .administrative_response import _payload, _required_object
from .capabilities import CapabilityMatrix, OperationCapability


def _run_historical_close_seed_postings(
    world: AdministrativeWorld,
    matrix: CapabilityMatrix,
) -> None:
    for operation_id, request in (
        (
            "record-owner-contribution",
            _typed_cash_request(
                world,
                "owner-contribution",
                "OWNER_CONTRIBUTION",
                "2025-01-02",
                "owner-contribution",
                {
                    "cashAccountCode": "cash",
                    "equityAccountCode": "owner-capital",
                    "amount": _money("10000"),
                },
            ),
        ),
        (
            "record-sale-settled",
            _typed_cash_request(
                world,
                "historical-sale",
                "SALE_SETTLED",
                "2025-06-30",
                "cash-receipt",
                {
                    "cashAccountCode": "cash",
                    "revenueAccountCode": "service-revenue",
                    "amount": _money("1000"),
                },
            ),
        ),
        (
            "record-owner-withdrawal",
            _typed_cash_request(
                world,
                "owner-withdrawal",
                "OWNER_WITHDRAWAL",
                "2025-07-01",
                "owner-withdrawal",
                {
                    "cashAccountCode": "cash",
                    "equityAccountCode": "owner-draws",
                    "amount": _money("250"),
                },
            ),
        ),
    ):
        _run_request_mutation(
            world,
            matrix.operation(operation_id),
            request,
            _required_json_mode(matrix.operation(operation_id)),
            "prepare historical " + operation_id,
        )


def _require_live_historical_close_readiness(
    world: AdministrativeWorld,
    matrix: CapabilityMatrix,
) -> None:
    inspection = _run_operation(
        world,
        matrix.operation("inspect-book"),
        (
            "--book-file",
            world.config.book.argument,
            "--book-key-file",
            world.config.book_key.argument,
        ),
        _required_json_mode(matrix.operation("inspect-book")),
        "historical fiscal-close discovery",
    )
    if inspection is None:
        raise AssertionError("historical fiscal-close discovery must return JSON")
    payload = _payload(inspection, world.config, "historical fiscal-close discovery")
    identity = _required_object(payload, "bookIdentity", world.config, "historical close identity")
    require(
        identity.get("bookStartEffectiveDate") == _HISTORICAL_BOOK_START,
        f"{world.config.label} historical close world did not retain its requested start date",
    )
    close_readiness = _required_object(
        payload, "closeReadiness", world.config, "historical close readiness"
    )
    for readiness_key in ("interimResultTarget", "retainedAccumulatedTarget"):
        readiness = _required_object(
            close_readiness, readiness_key, world.config, "historical close readiness"
        )
        require(
            readiness.get("ready") is True,
            f"{world.config.label} historical close world is not ready for {readiness_key}",
        )
    accounts = _list_accounts_for_close_readiness(world, matrix.operation("list-accounts"))
    required_classifications = {
        "EQUITY_CONTRIBUTION",
        "RESULT_HOLDING",
        "RETAINED_ACCUMULATED",
    }
    classified_accounts = {
        account.get("financialPositionLineClassification"): account.get("accountCode")
        for account in accounts
        if account.get("financialPositionLineClassification") in required_classifications
        and account.get("active") is True
        and account.get("accountNodeKind") == "POSTABLE"
    }
    require(
        set(classified_accounts) == required_classifications
        and len(set(classified_accounts.values())) == len(required_classifications),
        f"{world.config.label} historical close discovery did not find distinct active close accounts",
    )


def _list_accounts_for_close_readiness(
    world: AdministrativeWorld,
    operation: OperationCapability,
) -> list[JsonObject]:
    envelope = _run_operation(
        world,
        operation,
        (
            "--book-file",
            world.config.book.argument,
            "--book-key-file",
            world.config.book_key.argument,
            "--limit",
            "100",
        ),
        _required_json_mode(operation),
        "historical close account discovery",
    )
    if envelope is None:
        raise AssertionError("JSON account discovery must expose an envelope")
    require(
        envelope.get("status") == "ok",
        f"{world.config.label} historical close account discovery did not report ok status",
    )
    payload = _payload(envelope, world.config, "historical close account discovery")
    raw_accounts = payload.get("accounts")
    require(
        isinstance(raw_accounts, list),
        f"{world.config.label} historical close account discovery did not expose accounts",
    )
    if not isinstance(raw_accounts, list):
        raise TypeError("require must reject a non-list account collection")
    accounts: list[JsonObject] = []
    for account in raw_accounts:
        require(
            isinstance(account, dict),
            f"{world.config.label} historical close account discovery exposed an invalid account",
        )
        if not isinstance(account, dict):
            raise TypeError("require must reject an invalid account")
        accounts.append(account)
    return accounts
