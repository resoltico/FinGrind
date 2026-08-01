"""Semantic proof for account, registration, and posting query lists."""

from __future__ import annotations

from ..models import ReleaseSmokeConfig
from ..support import require
from .query_response_support import (
    _require_mapping_list_fact,
    _required_mapping,
    _success_payload,
)
from .query_text_contract import _require_csv_fact, _require_query_family, _require_text_facts


def _assert_list_accounts_mode(
    config: ReleaseSmokeConfig,
    output_mode: str,
    output: str,
) -> None:
    expected_account_code = config.starter_cash_account_code
    if output_mode == "json":
        payload = _success_payload(config, "list-accounts", output)
        _require_query_family(
            payload,
            "list-accounts",
            f"{config.label} field-matrix list-accounts[json]",
        )
        _require_mapping_list_fact(
            payload,
            "accounts",
            "accountCode",
            expected_account_code,
            f"{config.label} field-matrix list-accounts[json]",
        )
        return
    if output_mode == "csv":
        _require_csv_fact(
            config,
            "list-accounts",
            output,
            ("exportFamily", "recordKind", "accountCode"),
            expected_account_code,
        )
        return
    _require_text_facts(
        config,
        "list-accounts",
        output_mode,
        output,
        expected_account_code,
        config.starter_cash_account_name,
    )


def _assert_list_tax_registrations_mode(
    config: ReleaseSmokeConfig,
    output_mode: str,
    output: str,
    tax_registration_id: str,
) -> None:
    if output_mode == "json":
        payload = _success_payload(config, "list-tax-registrations", output)
        _require_query_family(
            payload,
            "list-tax-registrations",
            f"{config.label} field-matrix list-tax-registrations[json]",
        )
        _require_mapping_list_fact(
            payload,
            "registrations",
            "taxRegistrationId",
            tax_registration_id,
            f"{config.label} field-matrix list-tax-registrations[json]",
        )
        return
    if output_mode == "csv":
        _require_csv_fact(
            config,
            "list-tax-registrations",
            output,
            ("exportFamily", "recordKind", "taxRegistrationId"),
            tax_registration_id,
        )
        return
    _require_text_facts(
        config,
        "list-tax-registrations",
        output_mode,
        output,
        tax_registration_id,
    )


def _assert_list_postings_mode(
    config: ReleaseSmokeConfig,
    output_mode: str,
    output: str,
    posting_id: str,
) -> None:
    if output_mode == "json":
        payload = _success_payload(config, "list-postings", output)
        _require_query_family(
            payload,
            "list-postings",
            f"{config.label} field-matrix list-postings[json]",
        )
        _require_mapping_list_fact(
            payload,
            "postings",
            "postingId",
            posting_id,
            f"{config.label} field-matrix list-postings[json]",
        )
        return
    if output_mode == "csv":
        _require_csv_fact(
            config,
            "list-postings",
            output,
            (
                "exportFamily",
                "recordKind",
                "postingId",
                "attestationOperationOrder",
                "attestationOperationHead",
            ),
            posting_id,
        )
        return
    _require_text_facts(config, "list-postings", output_mode, output, posting_id)


def _assert_get_posting_mode(
    config: ReleaseSmokeConfig,
    output_mode: str,
    output: str,
    posting_id: str,
) -> None:
    if output_mode == "json":
        payload = _success_payload(config, "get-posting", output)
        _require_query_family(
            payload,
            "get-posting",
            f"{config.label} field-matrix get-posting[json]",
        )
        posting = _required_mapping(
            payload,
            "posting",
            f"{config.label} field-matrix get-posting[json]",
        )
        require(
            posting.get("postingId") == posting_id
            and _required_mapping(
                payload,
                "resolvedQuery",
                f"{config.label} field-matrix get-posting[json]",
            ).get("postingId")
            == posting_id,
            f"{config.label} field-matrix get-posting[json] did not retain the selected posting",
        )
        return
    _require_text_facts(config, "get-posting", output_mode, output, posting_id)
