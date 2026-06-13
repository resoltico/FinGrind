from __future__ import annotations

import hashlib
import subprocess
from datetime import date
from pathlib import Path

from .budgets import kotlin_budget_for, python_budget_for, shell_budget_for, sql_budget_for
from .docs_budgets import gradle_kts_budget_for, markdown_budget_for
from .metrics import (
    measure_kotlin_file,
    measure_markdown_file,
    measure_python_file,
    measure_shell_file,
    measure_sql_file,
)
from .models import FileBudget, FileMetrics, MeasuredSurface
from .reviewed_surface_verification import (
    default_budget_for_reviewed_surface,
    reviewed_surface_definition_violations,
    reviewed_surface_violations,
)
from .reviewed_surfaces import REVIEWED_SURFACES

MARKDOWN_EXCLUDED_PREFIXES = (
    ".git/",
    ".gradle/",
    "build/",
    "tmp/",
)


def verify_build_logic_kotlin(repo_root: Path) -> list[str]:
    source_root = repo_root / "gradle" / "build-logic" / "src"
    if not source_root.is_dir():
        return [f"{source_root}: missing build-logic source root for structural verification."]
    files = sorted(source_root.rglob("*.kt"))
    measurements = _measure_files(repo_root, files, kotlin_budget_for, measure_kotlin_file)
    duplication_candidates = [
        measurement
        for measurement in measurements
        if "src/test/kotlin" not in measurement[0].as_posix()
        and measurement[1].role_name != "build-logic-reviewed-surface-catalog"
    ]
    return _measurement_violations(measurements, duplication_candidates)


def verify_shell_release(repo_root: Path) -> list[str]:
    script_files = sorted((repo_root / "scripts").glob("*.sh"))
    files = [*script_files, repo_root / "check.sh"]
    measurements = _measure_files(repo_root, files, shell_budget_for, measure_shell_file)
    duplication_candidates = [
        measurement for measurement in measurements if not measurement[0].name.startswith("test-")
    ]
    return _measurement_violations(measurements, duplication_candidates)


def verify_python_support(repo_root: Path) -> list[str]:
    files = sorted((repo_root / "scripts").rglob("*.py"))
    measurements = _measure_files(repo_root, files, python_budget_for, measure_python_file)
    return _measurement_violations(measurements, measurements)


def verify_sqlite_sql(repo_root: Path) -> list[str]:
    schema_root = repo_root / "sqlite" / "src" / "main" / "resources"
    if not schema_root.is_dir():
        return [f"{schema_root}: missing SQLite schema root for structural verification."]
    files = sorted(schema_root.rglob("*.sql"))
    measurements = _measure_files(repo_root, files, sql_budget_for, measure_sql_file)
    return _measurement_violations(measurements, measurements)


def verify_markdown_docs(repo_root: Path) -> list[str]:
    files = repository_markdown_files(repo_root)
    measurements = _measure_files(
        repo_root,
        files,
        markdown_budget_for,
        measure_markdown_file,
    )
    return _measurement_violations(measurements, measurements)


def verify_gradle_kts(repo_root: Path) -> list[str]:
    candidate_files = [
        repo_root / "build.gradle.kts",
        repo_root / "settings.gradle.kts",
        *sorted(repo_root.glob("*/build.gradle.kts")),
        *sorted(repo_root.glob("*/settings.gradle.kts")),
        *sorted((repo_root / "gradle").glob("*/build.gradle.kts")),
        *sorted((repo_root / "gradle").glob("*/settings.gradle.kts")),
    ]
    filtered = [path for path in candidate_files if path.is_file()]
    measurements = _measure_files(repo_root, filtered, gradle_kts_budget_for, measure_kotlin_file)
    return _measurement_violations(measurements, measurements)


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


def _measure_files(
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


def _measurement_violations(
    measurements: list[MeasuredSurface],
    duplication_candidates: list[MeasuredSurface],
) -> list[str]:
    violations: list[str] = []
    current_date = date.today()
    for relative_path, budget, metrics in measurements:
        violations.extend(check_metrics(relative_path, budget, metrics))
        reviewed = REVIEWED_SURFACES.get(relative_path.as_posix())
        if reviewed is not None:
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
    if duplication_candidates:
        min_window = min(
            budget.max_duplicate_window_lines for _, budget, _ in duplication_candidates
        )
        violations.extend(
            duplicate_window_violations(duplication_candidates, minimum_window_lines=min_window)
        )
    return violations


def repository_markdown_files(repo_root: Path) -> list[Path]:
    tracked_files = tracked_repository_files(repo_root)
    if tracked_files is None:
        return sorted(
            path
            for path in repo_root.rglob("*.md")
            if path.is_file() and include_markdown_path(path.relative_to(repo_root))
        )
    return sorted(
        path
        for path in tracked_files
        if path.suffix == ".md" and include_markdown_path(path.relative_to(repo_root))
    )


def tracked_repository_files(repo_root: Path) -> list[Path] | None:
    result = subprocess.run(
        ["git", "-C", str(repo_root), "ls-files"],
        capture_output=True,
        check=False,
        encoding="utf-8",
    )
    if result.returncode != 0:
        return None
    return [
        repo_root / relative_path
        for relative_path in result.stdout.splitlines()
        if relative_path.strip()
    ]


def include_markdown_path(relative_path: Path) -> bool:
    path_text = relative_path.as_posix()
    return not any(path_text.startswith(prefix) for prefix in MARKDOWN_EXCLUDED_PREFIXES)
