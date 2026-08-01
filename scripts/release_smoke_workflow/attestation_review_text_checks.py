from __future__ import annotations

from typing import Any

from .attestation_review_json_assertions import assert_review_json, assert_strict_review_json
from .attestation_review_scope import review_scope, write_review_file
from .attestation_review_text_assertions import assert_grouped_review_text
from .attestation_review_window_assertions import verify_review_window_refusals
from .cli import run_cli, run_cli_allow_failure
from .models import ReleaseSmokeConfig
from .support import require


def verify_review_projections(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
    verification: dict[str, Any],
    review: dict[str, Any],
) -> None:
    """Prove review success and strict refusal carry the same immutable head evidence."""
    scope = review_scope(verification, config)
    assert_review_json(review, scope, config)
    review_file = write_review_file(
        config,
        "compromise-review.json",
        scope.credential_key_id,
        "0",
        scope.operation_order,
    )
    verify_review_window_refusals(config, operation_ids, error_exit_codes, scope)
    review_text = run_cli(
        config,
        operation_ids["attestationReview"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--attestation-review-file",
        review_file.argument,
        "--output",
        "text",
    )
    strict_text, strict_exit_code = run_cli_allow_failure(
        config,
        operation_ids["verifyBook"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--attestation-review-file",
        review_file.argument,
        "--require-clean-attestation",
        "--output",
        "text",
    )
    strict_json, strict_json_exit_code = run_cli_allow_failure(
        config,
        operation_ids["verifyBook"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--attestation-review-file",
        review_file.argument,
        "--require-clean-attestation",
        "--output",
        "json",
    )
    assert_grouped_review_text(
        review_text,
        scope.credential_key_id,
        scope.operation_order,
        f"{config.label} attestation-review text",
    )
    require(strict_exit_code == 2, f"{config.label} strict attestation review did not exit 2")
    assert_grouped_review_text(
        strict_text,
        scope.credential_key_id,
        scope.operation_order,
        f"{config.label} strict verify-book review text",
    )
    require(
        strict_json_exit_code == 2,
        f"{config.label} strict attestation-review JSON did not exit 2",
    )
    assert_strict_review_json(strict_json, scope, config)
