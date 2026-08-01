"""Orchestrate the isolated posting-attestation provenance scale proof."""

from __future__ import annotations

import time

from ..attestation_head_checks import (
    require_attestation_commit_matches_verified_head,
    verified_attestation_head,
)
from ..models import ReleaseSmokeConfig
from ..support import require
from .attestation_scale_account_ledger import verify_account_ledger_provenance
from .attestation_scale_contract import SCALE_POSTING_COUNT
from .attestation_scale_posting_queries import (
    verify_list_postings_csv,
    verify_paginated_list_postings,
    verify_sample_get_posting,
)
from .attestation_scale_writes import prepare_scale_world, record_scale_postings


def verify_attestation_scale_scenario(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
) -> None:
    """Exercise isolated pagination and all posting provenance read surfaces."""
    print(f"{config.label}: verifying 40-posting attestation provenance scale")
    started_at = time.perf_counter()
    scale_config, request_directory = prepare_scale_world(config, operation_ids)
    expected_commits, posting_ids = record_scale_postings(
        scale_config,
        operation_ids,
        request_directory,
    )
    final_head = verified_attestation_head(
        scale_config,
        operation_ids,
        "after 40-posting attestation provenance scale",
    )
    require(
        final_head.operation_order == str(SCALE_POSTING_COUNT),
        f"{scale_config.label} scale book did not retain exactly {SCALE_POSTING_COUNT} posting operations",
    )
    require_attestation_commit_matches_verified_head(
        expected_commits[posting_ids[-1]],
        final_head,
        scale_config.label,
        "last 40-posting scale commit",
    )
    verify_paginated_list_postings(scale_config, operation_ids, expected_commits)
    verify_list_postings_csv(scale_config, operation_ids, expected_commits)
    verify_sample_get_posting(scale_config, operation_ids, posting_ids, expected_commits)
    verify_account_ledger_provenance(scale_config, operation_ids, expected_commits)
    elapsed_seconds = time.perf_counter() - started_at
    print(f"{scale_config.label}: 40-posting provenance scale completed in {elapsed_seconds:.3f}s")
