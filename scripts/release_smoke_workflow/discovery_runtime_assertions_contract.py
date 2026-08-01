"""Focused contracts for live discovery assertions with durable hard-break consequences."""

from __future__ import annotations

from pathlib import Path

from contract_value_support import read_json
from contract_values import load_contract_values

from .discovery_assertions import (
    assert_machine_contract_protocol_version,
    machine_contract_protocol_version,
)
from .discovery_runtime_assertions import assert_protected_book_format_parity
from .models import ReleaseSmokeFailure
from .support import require


def assert_discovery_runtime_assertions_contract(repo_root: Path) -> None:
    """Keep protocol and protected-format hard breaks fail-closed before field work begins."""
    _assert_protocol_hard_break(repo_root)
    canonical_format = _canonical_protected_book_format(repo_root)
    assert_protected_book_format_parity(
        "synthetic release smoke",
        dict(canonical_format),
        canonical_format,
    )
    stale_format_version = canonical_format["formatVersion"]
    require(
        type(stale_format_version) is int and stale_format_version > 0,
        "canonical protected-book format did not declare one positive integer formatVersion",
    )
    stale_format = dict(canonical_format)
    stale_format["formatVersion"] = stale_format_version - 1
    _require_rejected(
        stale_format,
        canonical_format,
        "formatVersion",
        "protected-book format parity accepted a stale format version",
    )
    for field_name in ("applicationId", "formatVersion"):
        missing_format_fact = dict(canonical_format)
        del missing_format_fact[field_name]
        _require_rejected(
            missing_format_fact,
            canonical_format,
            field_name,
            f"protected-book format parity accepted a missing {field_name}",
        )
    with_extra_format_fact = dict(canonical_format)
    with_extra_format_fact["unownedFormatFact"] = "unexpected"
    _require_rejected(
        with_extra_format_fact,
        canonical_format,
        "unownedFormatFact",
        "protected-book format parity accepted an unowned runtime format fact",
    )


def _assert_protocol_hard_break(repo_root: Path) -> None:
    actual_protocol_version = machine_contract_protocol_version(repo_root)
    try:
        retired_protocol_version = str(int(actual_protocol_version) - 1)
    except ValueError as exc:
        raise AssertionError(
            "MachineContract must declare one positive integer protocol version"
        ) from exc
    assert int(actual_protocol_version) > 0, (
        "MachineContract must declare one positive integer protocol version"
    )
    assert_machine_contract_protocol_version(
        {"protocolVersion": actual_protocol_version},
        actual_protocol_version,
        "synthetic release smoke",
    )
    try:
        assert_machine_contract_protocol_version(
            {"protocolVersion": retired_protocol_version},
            actual_protocol_version,
            "synthetic release smoke",
        )
    except ReleaseSmokeFailure as exc:
        assert actual_protocol_version in str(exc)
        return
    raise AssertionError(
        "release-smoke accepted the immediately retired discovery protocol identity"
    )


def _require_rejected(
    runtime_format: dict[str, object],
    canonical_format: dict[str, object],
    expected_message: str,
    failure_message: str,
) -> None:
    try:
        assert_protected_book_format_parity(
            "synthetic release smoke",
            runtime_format,
            canonical_format,
        )
    except ReleaseSmokeFailure as exc:
        assert expected_message in str(exc)
        return
    raise AssertionError(failure_message)


def _canonical_protected_book_format(repo_root: Path) -> dict[str, object]:
    canonical_document = read_json(
        repo_root / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/"
        "protected-book-format-contract.json"
    )
    loaded_contract = load_contract_values(repo_root)
    loaded_format = loaded_contract.get("protectedBookFormat")
    require(
        isinstance(loaded_format, dict) and loaded_format == canonical_document,
        "release-smoke contract values did not preserve the exact protected-book format contract",
    )
    if not isinstance(loaded_format, dict):
        raise TypeError("require must reject a malformed protected-book format contract")
    return dict(loaded_format)
