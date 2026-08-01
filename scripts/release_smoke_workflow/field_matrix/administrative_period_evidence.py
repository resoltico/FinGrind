"""Period-close response and generated-posting evidence."""

from __future__ import annotations

from collections.abc import Mapping

from ..attestation_head_checks import VerifiedAttestationHead
from ..support import require, require_labeled_text_value
from .administrative_attestation_output import _require_text_attestation
from .administrative_constants import _HISTORICAL_BOOK_START, _JSON_MODE
from .administrative_models import AdministrativeWorld
from .administrative_reads import _persisted_posting
from .administrative_response import (
    _argument_value,
    _require_response_attestation_commit,
    _require_text_title,
    _response_payload,
)
from .pair_publication_output import single_labeled_text_value


def _assert_period_close_evidence(
    world: AdministrativeWorld,
    operation_id: str,
    output_mode: str,
    output: str,
    arguments: tuple[str, ...],
    expected_head: VerifiedAttestationHead,
    label: str,
) -> None:
    if operation_id == "interim-result-sweep":
        through = _argument_value(arguments, "--through", world, operation_id, label)
        posting_label = "Generated interim-result-sweep postings"
        expected_origin = "INTERIM_RESULT_SWEEP"
        if output_mode == _JSON_MODE:
            payload = _response_payload(world, operation_id, output, label)
            require(
                payload.get("effectiveDateTo") == through
                and payload.get("effectiveDateFrom") == _HISTORICAL_BOOK_START,
                f"{world.config.label} {label} interim-result-sweep[json] did not retain its scope",
            )
            _require_response_attestation_commit(
                payload, expected_head, world, operation_id, "json", label
            )
            posting_ids = _period_close_posting_ids(
                payload,
                "sweepPostingIds",
                world,
                operation_id,
                label,
            )
        else:
            _require_text_title(world, operation_id, output, "Interim Result Swept", label)
            require(
                through in output,
                f"{world.config.label} {label} interim-result-sweep[text] did not retain its scope",
            )
            _require_text_attestation(
                output,
                world.config,
                label,
                operation_id,
                expected_head=expected_head,
            )
            posting_ids = _period_close_text_posting_ids(
                output,
                posting_label,
                world,
                operation_id,
                label,
            )
    else:
        year = _argument_value(arguments, "--year", world, operation_id, label)
        posting_label = "Generated fiscal-year-close postings"
        expected_origin = "FISCAL_YEAR_CLOSE"
        if output_mode == _JSON_MODE:
            payload = _response_payload(world, operation_id, output, label)
            require(
                payload.get("effectiveDateFrom") == year + "-01-01"
                and payload.get("effectiveDateTo") == year + "-12-31"
                and payload.get("idempotentReplay") is False
                and isinstance(payload.get("closePostingIds"), list),
                f"{world.config.label} {label} fiscal-year-close[json] did not retain its close scope",
            )
            _require_response_attestation_commit(
                payload, expected_head, world, operation_id, "json", label
            )
            posting_ids = _period_close_posting_ids(
                payload,
                "closePostingIds",
                world,
                operation_id,
                label,
            )
        else:
            _require_text_title(world, operation_id, output, "Fiscal Year Closed", label)
            require(
                year in output,
                f"{world.config.label} {label} fiscal-year-close[text] did not retain its close year",
            )
            require_labeled_text_value(
                output,
                "Idempotent replay",
                "No",
                f"{world.config.label} {label} fiscal-year-close[text] reported a replay",
            )
            _require_text_attestation(
                output,
                world.config,
                label,
                operation_id,
                expected_head=expected_head,
            )
            posting_ids = _period_close_text_posting_ids(
                output,
                posting_label,
                world,
                operation_id,
                label,
            )
    _assert_period_close_posting_state(
        world,
        operation_id,
        posting_ids,
        expected_origin,
        expected_head,
        label,
    )


def _period_close_posting_ids(
    payload: Mapping[str, object],
    field: str,
    world: AdministrativeWorld,
    operation_id: str,
    label: str,
) -> tuple[str, ...]:
    raw_posting_ids = payload.get(field)
    require(
        isinstance(raw_posting_ids, list) and bool(raw_posting_ids),
        f"{world.config.label} {label} {operation_id}[json] did not identify generated postings",
    )
    if not isinstance(raw_posting_ids, list):
        raise TypeError("period-close proof requires a posting-id list")
    posting_ids = tuple(raw_posting_ids)
    require(
        all(isinstance(posting_id, str) and bool(posting_id.strip()) for posting_id in posting_ids)
        and len(set(posting_ids)) == len(posting_ids),
        f"{world.config.label} {label} {operation_id}[json] exposed invalid generated posting ids",
    )
    return posting_ids


def _period_close_text_posting_ids(
    output: str,
    posting_label: str,
    world: AdministrativeWorld,
    operation_id: str,
    label: str,
) -> tuple[str, ...]:
    rendered_ids = single_labeled_text_value(
        output,
        posting_label,
        f"{world.config.label} {label} {operation_id}[text] did not publish generated postings",
    )
    posting_ids = tuple(value.strip() for value in rendered_ids.split(",") if value.strip())
    require(
        bool(posting_ids)
        and posting_ids != ("(none)",)
        and len(set(posting_ids)) == len(posting_ids),
        f"{world.config.label} {label} {operation_id}[text] did not identify generated postings",
    )
    return posting_ids


def _assert_period_close_posting_state(
    world: AdministrativeWorld,
    operation_id: str,
    posting_ids: tuple[str, ...],
    expected_origin: str,
    expected_head: VerifiedAttestationHead,
    label: str,
) -> None:
    for posting_id in posting_ids:
        posting = _persisted_posting(world, posting_id, label)
        commit = posting.get("attestationCommit")
        require(
            posting.get("postingId") == posting_id
            and posting.get("postingOriginKind") == expected_origin
            and isinstance(commit, Mapping)
            and commit.get("operationOrder") == expected_head.operation_order
            and commit.get("operationHead") == expected_head.operation_head,
            f"{world.config.label} {label} {operation_id} did not persist generated posting "
            f"{posting_id} on its verified attestation operation",
        )
