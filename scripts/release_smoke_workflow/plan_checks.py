from __future__ import annotations

from .attestation_arguments import signing_credential_arguments
from .attestation_head_checks import (
    VerifiedAttestationHead,
    attestation_commit_from_payload,
    require_attestation_commit_matches_verified_head,
    require_no_attestation_commit,
    require_plan_attestation_disposition,
    verified_attestation_head,
)
from .cli import run_cli, run_cli_allow_failure
from .fixture_writers import write_ledger_plan_fixtures
from .models import ReleaseSmokeConfig
from .plan_execution_assertions import successful_plan_payload
from .plan_journal_assertions import (
    assert_administrative_plan_journal,
    assert_read_only_plan_journal,
)
from .plan_payloads import administrative_ledger_plan_id, read_only_ledger_plan_id
from .plan_posting_checks import verify_posting_plan
from .plan_reactivation_checks import verify_reactivate_rename_plan
from .support import parse_json_output, require


def verify_attested_administrative_plan_and_read_only_plan(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
) -> None:
    print(f"{config.label}: verifying aggregate plan attestation and read-only plan semantics")
    plans = write_ledger_plan_fixtures(config)
    head_before = verified_attestation_head(
        config, operation_ids, "before aggregate administrative plan"
    )
    administrative_payload = successful_plan_payload(
        run_cli(
            config,
            operation_ids["executePlan"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--request-file",
            plans.administrative.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
            "--result-detail",
            "full",
        ),
        config,
        "aggregate administrative plan",
        administrative_ledger_plan_id(config.request_prefix),
        3,
    )
    require_plan_attestation_disposition(
        administrative_payload, config.label, "aggregate administrative plan", "appended"
    )
    plan_commit = attestation_commit_from_payload(
        administrative_payload, config.label, "aggregate administrative plan"
    )
    head_after_administrative_plan = verified_attestation_head(
        config, operation_ids, "after aggregate administrative plan"
    )
    require_attestation_commit_matches_verified_head(
        plan_commit,
        head_after_administrative_plan,
        config.label,
        "aggregate administrative plan",
    )
    require(
        head_after_administrative_plan.operation_order == str(int(head_before.operation_order) + 1),
        f"{config.label} aggregate administrative plan did not append exactly one attestation operation",
    )
    assert_administrative_plan_journal(administrative_payload, config)

    replay_payload = successful_plan_payload(
        run_cli(
            config,
            operation_ids["executePlan"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--request-file",
            plans.administrative.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
            "--result-detail",
            "full",
        ),
        config,
        "replayed aggregate administrative plan",
        administrative_ledger_plan_id(config.request_prefix),
        3,
    )
    require_plan_attestation_disposition(
        replay_payload,
        config.label,
        "replayed aggregate administrative plan",
        "no-durable-child-mutation",
    )
    require_no_attestation_commit(
        replay_payload, config.label, "replayed aggregate administrative plan"
    )
    require(
        verified_attestation_head(
            config, operation_ids, "after replayed aggregate administrative plan"
        )
        == head_after_administrative_plan,
        f"{config.label} replayed aggregate administrative plan changed the verified attestation head",
    )

    verify_reactivate_rename_plan(config, operation_ids, plans)
    head_after_posting_plan = verify_posting_plan(config, operation_ids, plans)

    read_only_payload = successful_plan_payload(
        run_cli(
            config,
            operation_ids["executePlan"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--request-file",
            plans.read_only.argument,
            "--output",
            "json",
            "--result-detail",
            "full",
        ),
        config,
        "read-only account plan",
        read_only_ledger_plan_id(config.request_prefix),
        1,
    )
    require_plan_attestation_disposition(
        read_only_payload, config.label, "read-only account plan", "read-only"
    )
    require_no_attestation_commit(read_only_payload, config.label, "read-only account plan")
    assert_read_only_plan_journal(read_only_payload, config)
    require(
        verified_attestation_head(config, operation_ids, "after read-only account plan")
        == head_after_posting_plan,
        f"{config.label} read-only account plan changed the verified attestation head",
    )
    _assert_signed_read_only_plan_refusal(
        config,
        operation_ids,
        error_exit_codes,
        plans.read_only.argument,
        head_after_posting_plan,
    )


def _assert_signed_read_only_plan_refusal(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
    read_only_plan_argument: str,
    expected_head: VerifiedAttestationHead,
) -> None:
    output, exit_code = run_cli_allow_failure(
        config,
        operation_ids["executePlan"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--request-file",
        read_only_plan_argument,
        *signing_credential_arguments(config),
        "--output",
        "json",
    )
    require(
        exit_code == error_exit_codes["attestation-credentials-not-allowed"],
        f"{config.label} signed read-only plan returned exit {exit_code} instead of the declared attestation-credentials-not-allowed exit",
    )
    envelope = parse_json_output(
        output,
        f"{config.label} signed read-only plan refusal was not valid JSON",
    )
    require(
        envelope.get("status") == "error"
        and envelope.get("code") == "attestation-credentials-not-allowed"
        and envelope.get("category") == "structural-invalid"
        and "payload" not in envelope,
        f"{config.label} signed read-only plan did not publish the exact structural refusal envelope",
    )
    require(
        verified_attestation_head(config, operation_ids, "after signed read-only plan refusal")
        == expected_head,
        f"{config.label} signed read-only plan refusal changed the verified attestation head",
    )
