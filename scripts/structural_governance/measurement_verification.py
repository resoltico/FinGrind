from __future__ import annotations

import hashlib
from datetime import date
from pathlib import Path

from .models import FileBudget, FileMetrics, MeasuredSurface, ReviewedSurface
from .reviewed_surface_verification import (
    default_budget_for_reviewed_surface,
    missing_reviewed_surface_violations,
    reviewed_surface_definition_violations,
    reviewed_surface_violations,
)


def check_metrics(relative_path: Path, budget: FileBudget, metrics: FileMetrics) -> list[str]:
    violations: list[str] = []
    path_text = relative_path.as_posix()
    if metrics.physical_lines > budget.max_physical_lines:
        violations.append(
            f"{path_text}: {metrics.physical_lines} physical lines exceeds "
            f"{budget.max_physical_lines} for {budget.role_name}; {budget.split_hint}"
        )
    if metrics.logical_lines > budget.max_logical_lines:
        violations.append(
            f"{path_text}: {metrics.logical_lines} logical lines exceeds "
            f"{budget.max_logical_lines} for {budget.role_name}; {budget.split_hint}"
        )
    if metrics.import_like_lines > budget.max_import_like_lines:
        violations.append(
            f"{path_text}: {metrics.import_like_lines} import/source lines exceeds "
            f"{budget.max_import_like_lines} for {budget.role_name}; reduce fan-out or split ownership."
        )
    if metrics.functions > budget.max_functions:
        violations.append(
            f"{path_text}: {metrics.functions} functions exceeds {budget.max_functions} for "
            f"{budget.role_name}; {budget.split_hint}"
        )
    if metrics.nested_types > budget.max_nested_types:
        violations.append(
            f"{path_text}: {metrics.nested_types} nested types exceeds {budget.max_nested_types} for "
            f"{budget.role_name}; move focused collaborators into their own file."
        )
    return violations


def duplicate_window_violations(
    measurements: list[MeasuredSurface],
    minimum_window_lines: int,
) -> list[str]:
    signatures: dict[str, tuple[Path, int, int]] = {}
    violations: list[str] = []
    for relative_path, budget, metrics in measurements:
        lines = metrics.normalized_nonempty_lines
        window_size = max(minimum_window_lines, budget.max_duplicate_window_lines)
        if len(lines) < window_size:
            continue
        for start in range(0, len(lines) - window_size + 1):
            window = lines[start : start + window_size]
            signature = hashlib.sha256("\n".join(window).encode("utf-8")).hexdigest()
            prior = signatures.get(signature)
            if prior is None:
                signatures[signature] = (relative_path, start + 1, start + window_size)
                continue
            prior_path, prior_start, prior_end = prior
            if prior_path == relative_path:
                continue
            violations.append(
                f"{relative_path.as_posix()}: duplicate normalized {window_size}-line block also appears in "
                f"{prior_path.as_posix()} ({prior_start}-{prior_end}); extract a shared owner instead of copying structure."
            )
            break
    return violations


def measure_files(
    repo_root: Path,
    files: list[Path],
    budget_for,
    measure_file,
) -> list[MeasuredSurface]:
    measurements: list[MeasuredSurface] = []
    for file_path in files:
        if not file_path.is_file():
            continue
        relative_path = file_path.relative_to(repo_root)
        budget = budget_for(relative_path)
        metrics = measure_file(file_path)
        measurements.append((relative_path, budget, metrics))
    return measurements


def measurement_violations(
    measurements: list[MeasuredSurface],
    duplication_candidates: list[MeasuredSurface],
    reviewed_surfaces: dict[str, ReviewedSurface] | None = None,
) -> list[str]:
    violations: list[str] = []
    current_date = date.today()
    reviewed_surfaces = reviewed_surfaces or {}
    for relative_path, budget, metrics in measurements:
        reviewed = reviewed_surfaces.get(relative_path.as_posix())
        if reviewed is None:
            violations.extend(check_metrics(relative_path, budget, metrics))
        else:
            default_budget = default_budget_for_reviewed_surface(relative_path)
            baseline_violations = check_metrics(relative_path, default_budget, metrics)
            violations.extend(
                reviewed_surface_definition_violations(relative_path, reviewed, default_budget)
            )
            violations.extend(
                reviewed_surface_violations(
                    relative_path,
                    reviewed,
                    metrics,
                    default_budget,
                    current_date,
                    baseline_violations,
                )
            )
    if reviewed_surfaces:
        violations.extend(
            missing_reviewed_surface_violations(
                reviewed_surfaces=reviewed_surfaces,
                existing_relative_paths={
                    relative_path.as_posix() for relative_path, _, _ in measurements
                },
            )
        )
    if duplication_candidates:
        min_window = min(
            budget.max_duplicate_window_lines for _, budget, _ in duplication_candidates
        )
        violations.extend(
            duplicate_window_violations(duplication_candidates, minimum_window_lines=min_window)
        )
    return violations
