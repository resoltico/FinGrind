from __future__ import annotations

from .attestation_review_scope import ReviewScope, write_review_file
from .cli import run_cli_allow_failure
from .models import ReleaseSmokeConfig
from .support import parse_json_output, require

REVIEW_WINDOW_EXCEEDS_HEAD_CODE = "attestation-review-window-exceeds-head"
_REVIEW_WINDOW_EXCEEDS_HEAD_DETAIL_FIELDS = {
    "credentialKeyId",
    "firstAffectedOrder",
    "lastAffectedOrder",
    "verifiedHeadOrder",
}


def verify_review_window_refusals(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    error_exit_codes: dict[str, int],
    scope: ReviewScope,
) -> None:
    next_order = str(int(scope.operation_order) + 1)
    expected_exit_code = error_exit_codes.get(REVIEW_WINDOW_EXCEEDS_HEAD_CODE)
    require(
        isinstance(expected_exit_code, int) and not isinstance(expected_exit_code, bool),
        f"{config.label} capabilities output did not publish the {REVIEW_WINDOW_EXCEEDS_HEAD_CODE} exit code",
    )
    if not isinstance(expected_exit_code, int) or isinstance(expected_exit_code, bool):
        raise TypeError("require must reject a missing review-window error exit code")

    open_review_file = write_review_file(
        config,
        "review-window-open-exceeds-head.json",
        scope.credential_key_id,
        next_order,
        None,
    )
    open_output, open_exit_code = run_cli_allow_failure(
        config,
        operation_ids["attestationReview"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--attestation-review-file",
        open_review_file.argument,
        "--output",
        "json",
    )
    require(
        open_exit_code == expected_exit_code,
        f"{config.label} attestation-review review-window refusal exited with {open_exit_code} instead of the published {REVIEW_WINDOW_EXCEEDS_HEAD_CODE} exit code",
    )
    assert_review_window_error_json(
        open_output,
        scope,
        next_order,
        None,
        f"{config.label} attestation-review review-window refusal",
    )

    bounded_review_file = write_review_file(
        config,
        "review-window-bounded-exceeds-head.json",
        scope.credential_key_id,
        "0",
        next_order,
    )
    bounded_output, bounded_exit_code = run_cli_allow_failure(
        config,
        operation_ids["verifyBook"],
        "--book-file",
        config.book.argument,
        "--book-key-file",
        config.book_key.argument,
        "--attestation-review-file",
        bounded_review_file.argument,
        "--output",
        "json",
    )
    require(
        bounded_exit_code == expected_exit_code,
        f"{config.label} verify-book review-window refusal exited with {bounded_exit_code} instead of the published {REVIEW_WINDOW_EXCEEDS_HEAD_CODE} exit code",
    )
    assert_review_window_error_json(
        bounded_output,
        scope,
        "0",
        next_order,
        f"{config.label} verify-book review-window refusal",
    )


def assert_review_window_error_json(
    output: str,
    scope: ReviewScope,
    first_affected_order: str,
    last_affected_order: str | None,
    label: str,
) -> None:
    envelope = parse_json_output(output, f"{label} output was not valid JSON")
    require(
        envelope.get("status") == "error"
        and envelope.get("category") == "domain-semantic"
        and envelope.get("code") == REVIEW_WINDOW_EXCEEDS_HEAD_CODE
        and envelope.get("argument") == "--attestation-review-file"
        and isinstance(envelope.get("message"), str)
        and bool(envelope["message"])
        and isinstance(envelope.get("hint"), str)
        and bool(envelope["hint"])
        and "payload" not in envelope,
        f"{label} did not publish the typed {REVIEW_WINDOW_EXCEEDS_HEAD_CODE} error envelope",
    )
    details = envelope.get("details")
    require(
        isinstance(details, dict)
        and set(details) == _REVIEW_WINDOW_EXCEEDS_HEAD_DETAIL_FIELDS
        and details.get("credentialKeyId") == scope.credential_key_id
        and details.get("firstAffectedOrder") == first_affected_order
        and details.get("lastAffectedOrder") == last_affected_order
        and details.get("verifiedHeadOrder") == scope.operation_order,
        f"{label} did not publish the exact typed review-window details",
    )
