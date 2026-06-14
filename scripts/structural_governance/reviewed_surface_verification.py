from __future__ import annotations

from datetime import date
from pathlib import Path

from .budgets import (
    json_budget_for,
    python_script_test_budget,
    python_support_budget,
    sqlite_sql_support_budget,
)
from .models import FileBudget, FileMetrics, ReviewedSurface, ReviewedSurfaceApproval


def reviewed_surface_definition_violations(
    relative_path: Path, reviewed: ReviewedSurface, default_budget: FileBudget
) -> list[str]:
    if (
        approval_exceeds_default_budget(reviewed.approval, default_budget)
        and reviewed.budget_variance_reason is None
    ):
        return [
            f"{relative_path.as_posix()}: reviewed structural surface for {reviewed.owner} widens "
            f"the {default_budget.role_name} budget without an explicit variance reason."
        ]
    return []


def reviewed_surface_violations(
    relative_path: Path,
    reviewed: ReviewedSurface,
    metrics: FileMetrics,
    default_budget: FileBudget,
    current_date: date,
    baseline_violations: list[str],
) -> list[str]:
    approval = reviewed.approval
    violations: list[str] = []
    if current_date > approval.expires_on:
        violations.append(
            f"{relative_path.as_posix()}: reviewed structural waiver for {reviewed.owner} expired on "
            f"{approval.expires_on}; {reviewed.split_trigger}"
        )
    if not baseline_violations:
        violations.append(
            f"{relative_path.as_posix()}: reviewed structural waiver for {reviewed.owner} is no "
            f"longer needed because the file fits the {default_budget.role_name} budget; remove the "
            "reviewed waiver instead of carrying a stale exception."
        )
        return violations
    violations.extend(
        reviewed_surface_snapshot_drift_violations(relative_path, reviewed, metrics, approval)
    )
    return violations


def default_budget_for_reviewed_surface(relative_path: Path) -> FileBudget:
    path_text = relative_path.as_posix()
    if path_text.endswith(".sql"):
        return sqlite_sql_support_budget()
    if path_text.endswith(".json"):
        return json_budget_for(relative_path)
    if relative_path.name.startswith("test-"):
        return python_script_test_budget()
    return python_support_budget()


def approval_exceeds_default_budget(
    approval: ReviewedSurfaceApproval, default_budget: FileBudget
) -> bool:
    return (
        approval.approved_physical_lines > default_budget.max_physical_lines
        or approval.approved_logical_lines > default_budget.max_logical_lines
        or approval.approved_import_like_lines > default_budget.max_import_like_lines
        or approval.approved_functions > default_budget.max_functions
        or approval.approved_nested_types > default_budget.max_nested_types
    )


def reviewed_surface_snapshot_drift_violations(
    relative_path: Path,
    reviewed: ReviewedSurface,
    metrics: FileMetrics,
    approval: ReviewedSurfaceApproval,
) -> list[str]:
    drift_dimensions = (
        ("physical lines", approval.approved_physical_lines, metrics.physical_lines),
        ("logical lines", approval.approved_logical_lines, metrics.logical_lines),
        ("import-like lines", approval.approved_import_like_lines, metrics.import_like_lines),
        ("functions", approval.approved_functions, metrics.functions),
        ("nested types", approval.approved_nested_types, metrics.nested_types),
    )
    violations: list[str] = []
    for dimension_name, approved_value, live_value in drift_dimensions:
        if live_value == approved_value:
            continue
        violations.append(
            f"{relative_path.as_posix()}: reviewed structural approval for {reviewed.owner} no longer "
            f"matches the live file on {dimension_name} (approved {approved_value}, live {live_value}); "
            "refresh the waiver snapshot or finish the split instead of carrying drift."
        )
    return violations


def missing_reviewed_surface_violations(
    reviewed_surfaces: dict[str, ReviewedSurface],
    existing_relative_paths: set[str],
) -> list[str]:
    return [
        f"{relative_path}: reviewed structural waiver for {reviewed.owner} no longer resolves inside "
        "the repository root; remove or rewrite the orphaned waiver instead of carrying dead metadata."
        for relative_path, reviewed in reviewed_surfaces.items()
        if relative_path not in existing_relative_paths
    ]
