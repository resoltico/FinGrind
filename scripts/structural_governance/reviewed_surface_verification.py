from __future__ import annotations

from datetime import date
from pathlib import Path

from .budgets import python_script_test_budget, python_support_budget, sqlite_sql_support_budget
from .models import FileBudget, FileMetrics, ReviewedSurface


def reviewed_surface_definition_violations(
    relative_path: Path, reviewed: ReviewedSurface, default_budget: FileBudget
) -> list[str]:
    if budget_exceeds_default_budget(reviewed.budget, default_budget) and (
        reviewed.budget_variance_reason is None
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
    if metrics.physical_lines > approval.approved_physical_lines:
        violations.append(
            f"{relative_path.as_posix()}: reviewed structural surface for {reviewed.owner} grew from "
            f"{approval.approved_physical_lines} to {metrics.physical_lines} physical lines before "
            f"{approval.expires_on}; {reviewed.split_trigger}"
        )
    if metrics.logical_lines > approval.approved_logical_lines:
        violations.append(
            f"{relative_path.as_posix()}: reviewed structural surface for {reviewed.owner} grew from "
            f"{approval.approved_logical_lines} to {metrics.logical_lines} logical lines before "
            f"{approval.expires_on}; {reviewed.split_trigger}"
        )
    if metrics.import_like_lines > approval.approved_import_like_lines:
        violations.append(
            f"{relative_path.as_posix()}: reviewed structural surface for {reviewed.owner} grew from "
            f"{approval.approved_import_like_lines} to {metrics.import_like_lines} import-like lines before "
            f"{approval.expires_on}; {reviewed.split_trigger}"
        )
    if metrics.functions > approval.approved_functions:
        violations.append(
            f"{relative_path.as_posix()}: reviewed structural surface for {reviewed.owner} grew from "
            f"{approval.approved_functions} to {metrics.functions} functions before "
            f"{approval.expires_on}; {reviewed.split_trigger}"
        )
    if metrics.nested_types > approval.approved_nested_types:
        violations.append(
            f"{relative_path.as_posix()}: reviewed structural surface for {reviewed.owner} grew from "
            f"{approval.approved_nested_types} to {metrics.nested_types} nested types before "
            f"{approval.expires_on}; {reviewed.split_trigger}"
        )
    if not baseline_violations:
        violations.append(
            f"{relative_path.as_posix()}: reviewed structural waiver for {reviewed.owner} is no "
            f"longer needed because the file fits the {default_budget.role_name} budget; remove the "
            "reviewed waiver instead of carrying a stale exception."
        )
    return violations


def default_budget_for_reviewed_surface(relative_path: Path) -> FileBudget:
    path_text = relative_path.as_posix()
    if path_text.endswith(".sql"):
        return sqlite_sql_support_budget()
    if relative_path.name.startswith("test-"):
        return python_script_test_budget()
    return python_support_budget()


def budget_exceeds_default_budget(reviewed_budget: FileBudget, default_budget: FileBudget) -> bool:
    return (
        reviewed_budget.max_physical_lines > default_budget.max_physical_lines
        or reviewed_budget.max_logical_lines > default_budget.max_logical_lines
        or reviewed_budget.max_import_like_lines > default_budget.max_import_like_lines
        or reviewed_budget.max_functions > default_budget.max_functions
        or reviewed_budget.max_nested_types > default_budget.max_nested_types
        or reviewed_budget.max_duplicate_window_lines > default_budget.max_duplicate_window_lines
    )
