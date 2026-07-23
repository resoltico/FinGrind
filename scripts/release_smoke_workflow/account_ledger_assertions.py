from __future__ import annotations

from typing import Final

from .csv_support import parse_csv_rows
from .models import ReleaseSmokeConfig
from .support import require, require_match

ACCOUNT_LEDGER_CSV_HEADER: Final[list[str]] = [
    "family",
    "accountCode",
    "postingId",
    "effectiveDate",
    "movementCurrencyCode",
    "debitTotalCurrencyCode",
    "debitTotalMinorUnits",
    "creditTotalCurrencyCode",
    "creditTotalMinorUnits",
    "netAmountCurrencyCode",
    "netAmountMinorUnits",
    "balanceSide",
    "runningNetAmountCurrencyCode",
    "runningNetAmountMinorUnits",
    "runningBalanceSide",
    "attestationOperationOrder",
    "attestationOperationHead",
]
POSTING_ID_PATTERN: Final[str] = r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
ATTESTATION_OPERATION_ORDER_PATTERN: Final[str] = r"^[1-9][0-9]*$"
ATTESTATION_OPERATION_HEAD_PATTERN: Final[str] = r"^[0-9a-f]{64}$"


def assert_account_ledger_csv(config: ReleaseSmokeConfig, account_ledger_csv_output: str) -> None:
    header, rows = parse_csv_rows(
        account_ledger_csv_output,
        f"{config.label} account-ledger CSV output",
    )
    require(
        header == ACCOUNT_LEDGER_CSV_HEADER,
        f"{config.label} account-ledger CSV output did not render the expected header",
    )
    require(
        len(rows) == 2,
        f"{config.label} account-ledger CSV output did not render one row per posting",
    )
    require(
        [row["effectiveDate"] for row in rows] == ["2026-04-07", "2026-04-08"],
        f"{config.label} account-ledger CSV output was not ordered by effective date",
    )
    _assert_ledger_row(
        config,
        rows[0],
        effective_date="2026-04-07",
        debit_minor_units="1000",
        credit_minor_units="0",
        net_minor_units="1000",
        balance_side="DEBIT",
        running_net_minor_units="1000",
        running_balance_side="DEBIT",
        row_name="sale ledger row",
    )
    _assert_ledger_row(
        config,
        rows[1],
        effective_date="2026-04-08",
        debit_minor_units="0",
        credit_minor_units="400",
        net_minor_units="400",
        balance_side="CREDIT",
        running_net_minor_units="600",
        running_balance_side="DEBIT",
        row_name="expense ledger row",
    )
    require(
        rows[0]["attestationOperationOrder"] != rows[1]["attestationOperationOrder"],
        f"{config.label} account-ledger CSV output did not link fresh postings to distinct operations",
    )


def _assert_ledger_row(
    config: ReleaseSmokeConfig,
    row: dict[str, str],
    *,
    effective_date: str,
    debit_minor_units: str,
    credit_minor_units: str,
    net_minor_units: str,
    balance_side: str,
    running_net_minor_units: str,
    running_balance_side: str,
    row_name: str,
) -> None:
    currency = config.functional_currency
    require(
        row["family"] == "account-ledger"
        and row["accountCode"] == config.starter_cash_account_code
        and row["effectiveDate"] == effective_date
        and row["movementCurrencyCode"] == currency
        and row["debitTotalCurrencyCode"] == currency
        and row["debitTotalMinorUnits"] == debit_minor_units
        and row["creditTotalCurrencyCode"] == currency
        and row["creditTotalMinorUnits"] == credit_minor_units
        and row["netAmountCurrencyCode"] == currency
        and row["netAmountMinorUnits"] == net_minor_units
        and row["balanceSide"] == balance_side
        and row["runningNetAmountCurrencyCode"] == currency
        and row["runningNetAmountMinorUnits"] == running_net_minor_units
        and row["runningBalanceSide"] == running_balance_side,
        f"{config.label} account-ledger CSV output did not render the expected values for the {row_name}",
    )
    require_match(
        row["postingId"],
        POSTING_ID_PATTERN,
        f"{config.label} account-ledger CSV output did not render a canonical posting identifier for the {row_name}",
    )
    require_match(
        row["attestationOperationOrder"],
        ATTESTATION_OPERATION_ORDER_PATTERN,
        f"{config.label} account-ledger CSV output did not render an attestation operation order for the {row_name}",
    )
    require_match(
        row["attestationOperationHead"],
        ATTESTATION_OPERATION_HEAD_PATTERN,
        f"{config.label} account-ledger CSV output did not render an attestation operation head for the {row_name}",
    )
