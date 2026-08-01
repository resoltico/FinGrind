"""Direct idempotent-posting replay evidence for release smoke."""

from __future__ import annotations

from .attestation_arguments import signing_credential_arguments
from .attestation_head_checks import (
    attestation_commit_from_payload,
    require_attestation_commit_matches_verified_head,
    require_no_attestation_commit,
    verified_attestation_head,
)
from .cli import run_cli
from .models import ReleaseSmokeConfig
from .support import (
    parse_json_output,
    require,
    require_bool,
    require_string,
    required_mapping,
)


def verify_direct_posting_replay(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    committed_sale_output: str,
) -> None:
    """Prove a direct replay retains the original posting and appends nothing."""
    print(f"{config.label}: verifying direct posting idempotent replay")
    initial_envelope = parse_json_output(
        committed_sale_output,
        f"{config.label} initial sale commit output was not valid JSON",
    )
    require(
        initial_envelope.get("status") == "ok",
        f"{config.label} initial sale commit did not report ok status",
    )
    initial_payload = dict(required_mapping(initial_envelope, "payload"))
    require(
        require_bool(initial_payload, "idempotentReplay") is False,
        f"{config.label} initial sale unexpectedly reported an idempotent replay",
    )
    initial_posting_id = require_string(initial_payload, "postingId")
    initial_idempotency_key = require_string(initial_payload, "idempotencyKey")
    initial_commit = attestation_commit_from_payload(
        initial_payload,
        config.label,
        "initial direct sale",
    )
    head_before_replay = verified_attestation_head(
        config,
        operation_ids,
        "before direct posting replay",
    )
    require_attestation_commit_matches_verified_head(
        initial_commit,
        head_before_replay,
        config.label,
        "initial direct sale",
    )

    replay_envelope = parse_json_output(
        run_cli(
            config,
            operation_ids["recordSaleSettled"],
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--request-file",
            config.request_sale.argument,
            *signing_credential_arguments(config),
            "--output",
            "json",
        ),
        f"{config.label} direct sale replay output was not valid JSON",
    )
    require(
        replay_envelope.get("status") == "ok",
        f"{config.label} direct sale replay did not report ok status",
    )
    replay_payload = dict(required_mapping(replay_envelope, "payload"))
    require(
        require_bool(replay_payload, "idempotentReplay")
        and require_string(replay_payload, "postingId") == initial_posting_id
        and require_string(replay_payload, "idempotencyKey") == initial_idempotency_key,
        f"{config.label} direct sale replay did not retain the original posting identity",
    )
    require_no_attestation_commit(replay_payload, config.label, "direct sale replay")
    require(
        verified_attestation_head(config, operation_ids, "after direct posting replay")
        == head_before_replay,
        f"{config.label} direct sale replay appended an attestation operation",
    )

    persisted_posting = required_mapping(
        required_mapping(
            parse_json_output(
                run_cli(
                    config,
                    operation_ids["getPosting"],
                    "--book-file",
                    config.book.argument,
                    "--book-key-file",
                    config.book_key.argument,
                    "--posting-id",
                    initial_posting_id,
                    "--output",
                    "json",
                ),
                f"{config.label} direct sale replay get-posting output was not valid JSON",
            ),
            "payload",
        ),
        "posting",
    )
    persisted_commit = attestation_commit_from_payload(
        dict(persisted_posting),
        config.label,
        "direct sale replay persisted posting",
    )
    require(
        require_string(persisted_posting, "postingId") == initial_posting_id
        and require_string(persisted_posting, "idempotencyKey") == initial_idempotency_key,
        f"{config.label} direct sale replay get-posting did not retain the original posting identity",
    )
    require_attestation_commit_matches_verified_head(
        persisted_commit,
        head_before_replay,
        config.label,
        "direct sale replay persisted posting",
    )
