from __future__ import annotations

from . import fixture_payloads
from .models import ReleaseSmokeConfig
from .plan_execution_assertions import journal_step, journal_steps
from .support import require, required_list, required_mapping


def assert_administrative_plan_journal(
    payload: dict[str, object], config: ReleaseSmokeConfig
) -> None:
    steps = journal_steps(payload, config, "aggregate administrative plan", 3)
    payable_step = journal_step(steps, 0, config, "aggregate administrative plan")
    recoverable_step = journal_step(steps, 1, config, "aggregate administrative plan")
    registration_step = journal_step(steps, 2, config, "aggregate administrative plan")

    _assert_declared_account_step(
        payable_step,
        "declare-vat-payable",
        fixture_payloads.PLAN_TAX_PAYABLE_ACCOUNT_CODE,
        config,
    )
    _assert_declared_account_step(
        recoverable_step,
        "declare-vat-recoverable",
        fixture_payloads.PLAN_TAX_RECOVERABLE_ACCOUNT_CODE,
        config,
    )
    require(
        registration_step.get("stepId") == "declare-vat-registration"
        and registration_step.get("kind") == "declare-tax-registration"
        and registration_step.get("status") == "succeeded",
        f"{config.label} aggregate administrative plan did not complete tax-registration declaration",
    )
    registration_data = required_mapping(registration_step, "data")
    registration = required_mapping(registration_data, "taxRegistration")
    require(
        registration_data.get("outcome") == "declared"
        and registration.get("taxRegistrationId") == fixture_payloads.PLAN_TAX_REGISTRATION_ID
        and registration.get("payableAccountCode") == fixture_payloads.PLAN_TAX_PAYABLE_ACCOUNT_CODE
        and registration.get("recoverableAccountCode")
        == fixture_payloads.PLAN_TAX_RECOVERABLE_ACCOUNT_CODE,
        f"{config.label} aggregate administrative plan did not preserve the declared tax-registration facts",
    )


def assert_reactivate_rename_plan_journal(
    payload: dict[str, object], config: ReleaseSmokeConfig
) -> None:
    steps = journal_steps(payload, config, "same-account aggregate plan", 2)
    reactivated_step = journal_step(steps, 0, config, "same-account aggregate plan")
    renamed_step = journal_step(steps, 1, config, "same-account aggregate plan")
    _assert_account_lifecycle_plan_step(
        reactivated_step,
        "reactivate-plan-target",
        "reactivated",
        fixture_payloads.PLAN_REACTIVATE_RENAME_INITIAL_NAME,
        config,
    )
    _assert_account_lifecycle_plan_step(
        renamed_step,
        "rename-plan-target",
        "renamed",
        fixture_payloads.PLAN_REACTIVATE_RENAME_FINAL_NAME,
        config,
    )


def posting_id_from_plan_journal(payload: dict[str, object], config: ReleaseSmokeConfig) -> str:
    steps = journal_steps(payload, config, "aggregate posting plan", 1)
    posting_step = journal_step(steps, 0, config, "aggregate posting plan")
    data = required_mapping(posting_step, "data")
    posting_id = data.get("postingId")
    require(
        posting_step.get("stepId") == "post-plan-bank-transfer"
        and posting_step.get("kind") == "post-entry"
        and posting_step.get("status") == "succeeded"
        and isinstance(posting_id, str)
        and posting_id,
        f"{config.label} aggregate posting plan did not publish its committed posting",
    )
    return posting_id


def assert_read_only_plan_journal(payload: dict[str, object], config: ReleaseSmokeConfig) -> None:
    steps = journal_steps(payload, config, "read-only account plan", 1)
    query_step = journal_step(steps, 0, config, "read-only account plan")
    require(
        query_step.get("stepId") == "list-accounts-after-administration"
        and query_step.get("kind") == "list-accounts"
        and query_step.get("status") == "succeeded",
        f"{config.label} read-only account plan did not complete its account query",
    )
    data = required_mapping(query_step, "data")
    accounts = required_list(data, "accounts")
    require(
        any(
            isinstance(account, dict)
            and account.get("accountCode") == fixture_payloads.PLAN_TAX_PAYABLE_ACCOUNT_CODE
            for account in accounts
        ),
        f"{config.label} read-only account plan did not observe the aggregate plan's declared account",
    )


def _assert_account_lifecycle_plan_step(
    step: dict[str, object],
    expected_step_id: str,
    expected_outcome: str,
    expected_account_name: str,
    config: ReleaseSmokeConfig,
) -> None:
    data = required_mapping(step, "data")
    account = required_mapping(data, "account")
    require(
        step.get("stepId") == expected_step_id
        and step.get("kind") == "declare-account"
        and step.get("status") == "succeeded"
        and data.get("outcome") == expected_outcome
        and account.get("accountCode") == fixture_payloads.PLAN_REACTIVATE_RENAME_ACCOUNT_CODE
        and account.get("accountName") == expected_account_name
        and account.get("active") is True,
        f"{config.label} same-account aggregate plan did not preserve {expected_outcome}",
    )


def _assert_declared_account_step(
    step: dict[str, object],
    expected_step_id: str,
    expected_account_code: str,
    config: ReleaseSmokeConfig,
) -> None:
    require(
        step.get("stepId") == expected_step_id
        and step.get("kind") == "declare-account"
        and step.get("status") == "succeeded",
        f"{config.label} aggregate administrative plan did not complete {expected_step_id}",
    )
    data = required_mapping(step, "data")
    account = required_mapping(data, "account")
    require(
        data.get("outcome") == "declared" and account.get("accountCode") == expected_account_code,
        f"{config.label} aggregate administrative plan did not preserve {expected_account_code}",
    )
