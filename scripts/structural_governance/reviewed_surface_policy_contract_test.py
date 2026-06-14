from __future__ import annotations

import json
import re
from datetime import date
from pathlib import Path

from structural_governance.measurement_verification import check_metrics
from structural_governance.models import (
    FileBudget,
    FileMetrics,
    ReviewedSurface,
    ReviewedSurfaceApproval,
)
from structural_governance.reviewed_surface_verification import (
    missing_reviewed_surface_violations,
    reviewed_surface_definition_violations,
    reviewed_surface_violations,
)

_REPO_ROOT = Path(__file__).resolve().parents[2]
_CONTRACT_DIRECTORY = _REPO_ROOT / "scripts/structural_governance/reviewed_surface_policy_contract"

_EXPIRED_PATTERN = re.compile(r"expired on (\d{4}-\d{2}-\d{2})")
_DRIFT_PATTERN = re.compile(r"live file on (.+?) \(approved (\d+), live (\d+)\)")


def _contract_documents() -> list[dict[str, object]]:
    document_paths = sorted(_CONTRACT_DIRECTORY.glob("*.json"))
    if not document_paths:
        raise AssertionError(
            f"No reviewed-surface policy contract cases found in {_CONTRACT_DIRECTORY}"
        )
    return [
        json.loads(document_path.read_text(encoding="utf-8")) for document_path in document_paths
    ]


def _contract_cases() -> list[dict[str, object]]:
    return [
        document for document in _contract_documents() if str(document["documentType"]) == "case"
    ]


def _profiles_by_group() -> dict[str, dict[str, dict[str, object]]]:
    profiles: dict[str, dict[str, dict[str, object]]] = {}
    for document in _contract_documents():
        document_type = str(document["documentType"])
        if document_type == "case":
            continue
        document_profiles = document["profiles"]
        assert isinstance(document_profiles, dict)
        profiles[document_type] = {
            str(profile_name): profile for profile_name, profile in document_profiles.items()
        }
    return profiles


def _profile(
    profiles: dict[str, dict[str, dict[str, object]]],
    profile_group_name: str,
    profile_name: str,
) -> dict[str, object]:
    return profiles[profile_group_name][profile_name]


def _normalize_violation(violation: str) -> str:
    if "without an explicit variance reason" in violation:
        return "variance-reason-required"
    expired_match = _EXPIRED_PATTERN.search(violation)
    if expired_match is not None:
        return f"waiver-expired:{expired_match.group(1)}"
    if "is no longer needed because the file fits the" in violation:
        return "waiver-unnecessary"
    drift_match = _DRIFT_PATTERN.search(violation)
    if drift_match is not None:
        return (
            "snapshot-drift:"
            + _normalize_dimension(drift_match.group(1))
            + f":{drift_match.group(2)}:{drift_match.group(3)}"
        )
    if "no longer resolves inside" in violation:
        return "orphaned-waiver"
    raise AssertionError(f"Unrecognized reviewed-surface violation: {violation}")


def _normalize_dimension(dimension_name: str) -> str:
    return {
        "physical lines": "physical-lines",
        "logical lines": "logical-lines",
        "import-like lines": "imports",
        "functions": "functions",
        "nested types": "nested-types",
    }[dimension_name]


def _normalize_violations(violations: list[str]) -> list[str]:
    return [_normalize_violation(violation) for violation in violations]


def _reviewed_surface(
    case: dict[str, object],
    profiles: dict[str, dict[str, dict[str, object]]],
) -> ReviewedSurface:
    reviewed_surface_profile = _profile(
        profiles,
        "reviewed-surface-profiles",
        str(case["reviewedSurfaceProfile"]),
    )
    approval = _profile(
        profiles,
        "approval-profiles",
        str(case["approvalProfile"]),
    )
    budget_variance_reason = (
        case["budgetVarianceReason"]
        if "budgetVarianceReason" in case
        else reviewed_surface_profile["budgetVarianceReason"]
    )
    assert isinstance(approval, dict)
    return ReviewedSurface(
        relative_path=str(case["relativePath"]),
        owner=str(reviewed_surface_profile["owner"]),
        reason="Shared reviewed-surface policy contract case.",
        split_trigger=str(reviewed_surface_profile["splitTrigger"]),
        reviewed_role_name=str(reviewed_surface_profile["reviewedRoleName"]),
        budget_variance_reason=budget_variance_reason,
        approval=ReviewedSurfaceApproval(
            approved_physical_lines=int(approval["physicalLines"]),
            approved_logical_lines=int(approval["logicalLines"]),
            approved_import_like_lines=int(approval["importLikeLines"]),
            approved_functions=int(approval["functions"]),
            approved_nested_types=int(approval["nestedTypes"]),
            expires_on=date.fromisoformat(str(approval["expiresOn"])),
        ),
    )


def _budget(
    case: dict[str, object],
    profiles: dict[str, dict[str, dict[str, object]]],
) -> FileBudget:
    budget = _profile(
        profiles,
        "budget-profiles",
        str(case["defaultBudgetProfile"]),
    )
    return FileBudget(
        role_name=str(budget["roleName"]),
        max_physical_lines=int(budget["physicalLines"]),
        max_logical_lines=int(budget["logicalLines"]),
        max_import_like_lines=int(budget["importLikeLines"]),
        max_functions=int(budget["functions"]),
        max_nested_types=int(budget["nestedTypes"]),
        max_duplicate_window_lines=30,
        split_hint="Split the example owner.",
    )


def _metrics(
    case: dict[str, object],
    profiles: dict[str, dict[str, dict[str, object]]],
) -> FileMetrics:
    metrics = _profile(
        profiles,
        "metrics-profiles",
        str(case["liveMetricsProfile"]),
    )
    return FileMetrics(
        physical_lines=int(metrics["physicalLines"]),
        logical_lines=int(metrics["logicalLines"]),
        import_like_lines=int(metrics["importLikeLines"]),
        functions=int(metrics["functions"]),
        nested_types=int(metrics["nestedTypes"]),
        normalized_nonempty_lines=tuple("line" for _ in range(int(metrics["logicalLines"]))),
    )


def _run_definition_case(
    case: dict[str, object],
    profiles: dict[str, dict[str, dict[str, object]]],
) -> None:
    reviewed = _reviewed_surface(case, profiles)
    default_budget = _budget(case, profiles)
    actual = _normalize_violations(
        reviewed_surface_definition_violations(
            Path(reviewed.relative_path),
            reviewed,
            default_budget,
        )
    )
    expected = [str(descriptor) for descriptor in case["expectedDescriptors"]]
    if actual != expected:
        raise AssertionError(
            f"Definition case {case['id']} drifted: expected {expected}, actual {actual}"
        )


def _run_runtime_case(
    case: dict[str, object],
    profiles: dict[str, dict[str, dict[str, object]]],
) -> None:
    reviewed = _reviewed_surface(case, profiles)
    default_budget = _budget(case, profiles)
    metrics = _metrics(case, profiles)
    relative_path = Path(reviewed.relative_path)
    baseline_violations = check_metrics(relative_path, default_budget, metrics)
    actual = _normalize_violations(
        reviewed_surface_violations(
            relative_path=relative_path,
            reviewed=reviewed,
            metrics=metrics,
            default_budget=default_budget,
            current_date=date.fromisoformat(str(case["currentDate"])),
            baseline_violations=baseline_violations,
        )
    )
    expected = [str(descriptor) for descriptor in case["expectedDescriptors"]]
    if actual != expected:
        raise AssertionError(
            f"Runtime case {case['id']} drifted: expected {expected}, actual {actual}"
        )


def _run_orphan_case(
    case: dict[str, object],
    profiles: dict[str, dict[str, dict[str, object]]],
) -> None:
    reviewed = _reviewed_surface(case, profiles)
    actual = _normalize_violations(
        missing_reviewed_surface_violations(
            reviewed_surfaces={reviewed.relative_path: reviewed},
            existing_relative_paths={str(path) for path in case["existingRelativePaths"]},
        )
    )
    expected = [str(descriptor) for descriptor in case["expectedDescriptors"]]
    if actual != expected:
        raise AssertionError(
            f"Orphan case {case['id']} drifted: expected {expected}, actual {actual}"
        )


def main() -> int:
    profiles = _profiles_by_group()
    for case in _contract_cases():
        case_type = str(case["caseType"])
        if case_type == "definition":
            _run_definition_case(case, profiles)
            continue
        if case_type == "runtime":
            _run_runtime_case(case, profiles)
            continue
        if case_type == "orphan":
            _run_orphan_case(case, profiles)
            continue
        raise AssertionError(f"Unsupported reviewed-surface policy contract case type: {case_type}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
