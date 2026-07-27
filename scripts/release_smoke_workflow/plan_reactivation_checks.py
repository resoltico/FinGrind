from __future__ import annotations

from . import fixture_payloads
from .attestation_arguments import signing_credential_arguments
from .attestation_head_checks import (
    VerifiedAttestationHead,
    attestation_commit_from_payload,
    require_attestation_commit_matches_verified_head,
    require_plan_attestation_disposition,
    verified_attestation_head,
)
from .cli import run_cli
from .fixture_writers import LedgerPlanFixturePaths
from .models import ReleaseSmokeConfig
from .plan_execution_assertions import successful_account_mutation_payload, successful_plan_payload
from .plan_journal_assertions import assert_reactivate_rename_plan_journal
from .plan_payloads import reactivate_rename_ledger_plan_id
from .support import parse_json_output, require, required_list, required_mapping


def verify_reactivate_rename_plan(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    plans: LedgerPlanFixturePaths,
) -> VerifiedAttestationHead:
    print(f"{config.label}: verifying same-account aggregate-plan reactivation and rename")
    seed_payload = successful_account_mutation_payload(
        run_cli(
            config,
            operation_ids["declareAccount"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--request-file",
            plans.reactivate_rename_seed.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
        ),
        config,
        "same-account plan seed declaration",
        "declared",
        True,
    )
    seed_commit = attestation_commit_from_payload(
        seed_payload, config.label, "same-account plan seed declaration"
    )
    head_after_seed = verified_attestation_head(
        config, operation_ids, "after same-account plan seed declaration"
    )
    require_attestation_commit_matches_verified_head(
        seed_commit,
        head_after_seed,
        config.label,
        "same-account plan seed declaration",
    )

    retirement_payload = successful_account_mutation_payload(
        run_cli(
            config,
            operation_ids["retireAccount"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--request-file",
            plans.reactivate_rename_retire.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
        ),
        config,
        "same-account plan seed retirement",
        "retired",
        False,
    )
    retirement_commit = attestation_commit_from_payload(
        retirement_payload, config.label, "same-account plan seed retirement"
    )
    head_before_plan = verified_attestation_head(
        config, operation_ids, "before same-account aggregate plan"
    )
    require_attestation_commit_matches_verified_head(
        retirement_commit,
        head_before_plan,
        config.label,
        "same-account plan seed retirement",
    )

    plan_payload = successful_plan_payload(
        run_cli(
            config,
            operation_ids["executePlan"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--request-file",
            plans.reactivate_rename.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
            "--result-detail",
            "full",
        ),
        config,
        "same-account aggregate plan",
        reactivate_rename_ledger_plan_id(config.request_prefix),
        2,
    )
    require_plan_attestation_disposition(
        plan_payload, config.label, "same-account aggregate plan", "appended"
    )
    plan_commit = attestation_commit_from_payload(
        plan_payload, config.label, "same-account aggregate plan"
    )
    head_after_plan = verified_attestation_head(
        config, operation_ids, "after same-account aggregate plan"
    )
    require_attestation_commit_matches_verified_head(
        plan_commit,
        head_after_plan,
        config.label,
        "same-account aggregate plan",
    )
    require(
        head_after_plan.operation_order == str(int(head_before_plan.operation_order) + 1),
        f"{config.label} same-account aggregate plan did not append exactly one operation",
    )
    assert_reactivate_rename_plan_journal(plan_payload, config)
    _assert_reactivate_rename_final_account(config, operation_ids)
    return head_after_plan


def _assert_reactivate_rename_final_account(
    config: ReleaseSmokeConfig, operation_ids: dict[str, str]
) -> None:
    envelope = parse_json_output(
        run_cli(
            config,
            operation_ids["listAccounts"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--output",
            "json",
        ),
        f"{config.label} same-account aggregate plan final account query was not valid JSON",
    )
    accounts = required_list(required_mapping(envelope, "payload"), "accounts")
    require(
        any(
            isinstance(account, dict)
            and account.get("accountCode") == fixture_payloads.PLAN_REACTIVATE_RENAME_ACCOUNT_CODE
            and account.get("accountName") == fixture_payloads.PLAN_REACTIVATE_RENAME_FINAL_NAME
            and account.get("active") is True
            for account in accounts
        ),
        f"{config.label} same-account aggregate plan did not retain the final renamed account",
    )
