"""Operational refusal and request-artifact checks for noncurrent protected-book formats."""

from __future__ import annotations

import json
from collections.abc import Mapping

from ..artifact_contracts import expected_reported_path
from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from ..open_book_support import attempt_open_book
from ..support import parse_json_output, require, required_mapping
from .format_boundary_artifacts import _anchored_smoke_path


def _write_operational_boundary_requests(
    config: ReleaseSmokeConfig,
    boundary_name: str,
) -> Mapping[str, SmokePath]:
    """Write boundary-specific requests without mutating the copied protected book."""
    boundary_root = config.book.local_path.parent / "format-boundary"
    payloads: Mapping[str, Mapping[str, object]] = {
        "declare-account": {
            "accountCode": f"format-boundary-{boundary_name}",
            "accountName": "Format Boundary Account",
            "accountType": "ASSET",
            "accountNodeKind": "POSTABLE",
            "financialPositionLineClassification": "CURRENT_ASSET",
            "cashFlowAssetClassification": "NON_CASH",
        },
        "read-only-plan": {
            "planId": f"{config.request_prefix}-format-boundary-{boundary_name}-read-only",
            "steps": [
                {
                    "stepId": "list-accounts",
                    "kind": "list-accounts",
                    "query": {"limit": 1},
                }
            ],
        },
        "mutating-plan": {
            "planId": f"{config.request_prefix}-format-boundary-{boundary_name}-mutating",
            "steps": [
                {
                    "stepId": "declare-boundary-account",
                    "kind": "declare-account",
                    "declareAccount": {
                        "accountCode": f"format-boundary-plan-{boundary_name}",
                        "accountName": "Format Boundary Plan Account",
                        "accountType": "ASSET",
                        "accountNodeKind": "POSTABLE",
                        "financialPositionLineClassification": "CURRENT_ASSET",
                        "cashFlowAssetClassification": "NON_CASH",
                    },
                }
            ],
        },
    }
    paths: dict[str, SmokePath] = {}
    for request_name, payload in payloads.items():
        local_path = boundary_root / f"{boundary_name}-{request_name}.json"
        require(
            not local_path.exists(),
            f"{config.label} format-boundary request target already exists: {local_path}",
        )
        try:
            local_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        except OSError as exc:
            raise ReleaseSmokeFailure(
                f"{config.label} could not write {boundary_name}-format operational request "
                f"{request_name}"
            ) from exc
        paths[request_name] = _anchored_smoke_path(config, local_path)
    return paths


def _require_unsupported_format_envelope(
    config: ReleaseSmokeConfig,
    output: str,
    exit_code: int,
    expected_exit_code: object,
    command_name: str,
    boundary_name: str,
    boundary_format: int,
    supported_format: int,
) -> None:
    """Require the precise public refusal contract for every operational surface."""
    envelope = parse_json_output(
        output,
        f"{config.label} {boundary_name}-format {command_name} refusal was not valid JSON",
    )
    details = required_mapping(envelope, "details")
    expected_message = (
        "The selected FinGrind book uses format version "
        f"{boundary_format}, but this FinGrind binary supports version {supported_format} only."
    )
    expected_hint = (
        "Use a FinGrind binary that supports the selected book's exact format version. "
        "FinGrind neither migrates nor opens non-current formats."
    )
    require(
        exit_code == expected_exit_code
        and envelope.get("status") == "error"
        and envelope.get("code") == "unsupported-book-format-version"
        and envelope.get("category") == "precondition"
        and envelope.get("argument") == "--book-file"
        and envelope.get("message") == expected_message
        and envelope.get("hint") == expected_hint
        and details.get("detectedBookFormatVersion") == boundary_format
        and details.get("supportedBookFormatVersion") == supported_format,
        f"{config.label} {command_name} did not publish the exact {boundary_name}-format "
        "unsupported-book-format-version contract",
    )


def _require_open_book_does_not_replace_boundary(
    config: ReleaseSmokeConfig,
    boundary_book: SmokePath,
    boundary_key: SmokePath,
    boundary_name: str,
    error_exit_codes: Mapping[str, int],
    open_book_operation: str,
) -> None:
    """Require open-book to refuse an existing boundary artifact without replacement."""
    output, exit_code = attempt_open_book(
        config,
        {"openBook": open_book_operation},
        book=boundary_book,
        book_key=boundary_key,
    )
    expected_exit_code = error_exit_codes.get("book-destination-occupied")
    require(
        isinstance(expected_exit_code, int),
        f"{config.label} runtime contract did not publish book-destination-occupied exit semantics",
    )
    envelope = parse_json_output(
        output,
        f"{config.label} {boundary_name}-format open-book refusal was not valid JSON",
    )
    expected_path = expected_reported_path(config, boundary_book)
    require(
        exit_code == expected_exit_code
        and envelope.get("status") == "error"
        and envelope.get("code") == "book-destination-occupied"
        and envelope.get("category") == "precondition"
        and envelope.get("argument") == "--book-file"
        and envelope.get("message")
        == "The selected --book-file destination already exists; open-book will not access or replace it."
        and envelope.get("hint")
        == "Choose a missing --book-file destination before opening a new book."
        and envelope.get("path") == expected_path
        and envelope.get("relatedPaths") == [],
        f"{config.label} open-book did not refuse the existing {boundary_name}-format "
        f"book without replacement (exit {exit_code}; output {output!r})",
    )
