"""Named scenario, request, and fresh-world shapes for typed-record coverage."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from ..models import ReleaseSmokeConfig

JsonObject = dict[str, object]
RequestFactory = Callable[[str], tuple["TypedRecordRequest", ...]]


@dataclass(frozen=True)
class AccountDeclaration:
    """One supporting chart declaration that a fresh scenario genuinely needs."""

    account_code: str
    account_name: str
    account_type: str
    financial_position_line_classification: str | None = None
    cash_flow_asset_classification: str | None = None
    profit_and_loss_line_classification: str | None = None
    contra_of_account_code: str | None = None

    def request(self) -> JsonObject:
        payload: JsonObject = {
            "accountCode": self.account_code,
            "accountName": self.account_name,
            "accountType": self.account_type,
            "accountNodeKind": "POSTABLE",
        }
        optional_text = (
            ("financialPositionLineClassification", self.financial_position_line_classification),
            ("cashFlowAssetClassification", self.cash_flow_asset_classification),
            ("profitAndLossLineClassification", self.profit_and_loss_line_classification),
            ("contraOfAccountCode", self.contra_of_account_code),
        )
        for key, value in optional_text:
            if value is not None:
                payload[key] = value
        return payload


@dataclass(frozen=True)
class TypedRecordRequest:
    """One canonical typed-record operation and its complete request document."""

    operation_key: str
    request: JsonObject


@dataclass(frozen=True)
class TypedRecordScenario:
    """A cohesive accounting lifecycle exercised in two independently fresh books."""

    scenario_id: str
    book_template_id: str
    inventory_costing_doctrine: str | None
    accounting_basis: str
    declarations: tuple[AccountDeclaration, ...]
    requests: RequestFactory


@dataclass(frozen=True)
class TypedRecordWorld:
    """All operator-owned paths for one isolated output-mode world."""

    config: ReleaseSmokeConfig
    request_directory: Path
    path_anchor_config: ReleaseSmokeConfig
