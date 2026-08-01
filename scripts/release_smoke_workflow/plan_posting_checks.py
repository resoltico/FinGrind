from __future__ import annotations

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
from .plan_execution_assertions import successful_plan_payload
from .plan_journal_assertions import posting_id_from_plan_journal
from .plan_payloads import posting_ledger_plan_id
from .plan_posting_provenance_checks import assert_posting_plan_provenance
from .support import require


def verify_posting_plan(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    plans: LedgerPlanFixturePaths,
) -> VerifiedAttestationHead:
    print(f"{config.label}: verifying aggregate-plan posting attestation provenance")
    head_before_plan = verified_attestation_head(
        config, operation_ids, "before aggregate posting plan"
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
            plans.posting.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
            "--result-detail",
            "full",
        ),
        config,
        "aggregate posting plan",
        posting_ledger_plan_id(config.request_prefix),
        1,
    )
    require_plan_attestation_disposition(
        plan_payload, config.label, "aggregate posting plan", "appended"
    )
    plan_commit = attestation_commit_from_payload(
        plan_payload, config.label, "aggregate posting plan"
    )
    head_after_plan = verified_attestation_head(
        config, operation_ids, "after aggregate posting plan"
    )
    require_attestation_commit_matches_verified_head(
        plan_commit,
        head_after_plan,
        config.label,
        "aggregate posting plan",
    )
    require(
        head_after_plan.operation_order == str(int(head_before_plan.operation_order) + 1),
        f"{config.label} aggregate posting plan did not append exactly one operation",
    )
    posting_id = posting_id_from_plan_journal(plan_payload, config)
    assert_posting_plan_provenance(config, operation_ids, posting_id, plan_commit)
    return head_after_plan
