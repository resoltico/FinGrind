from __future__ import annotations

from pathlib import Path

from .budgets import (
    json_budget_for,
    kotlin_budget_for,
    python_budget_for,
    shell_budget_for,
    sql_budget_for,
)
from .docs_budgets import gradle_kts_budget_for, markdown_budget_for
from .inventory import (
    include_json_path,
    repository_json_files,
    repository_markdown_files,
    reviewed_surfaces_matching,
)
from .measurement_verification import measure_files, measurement_violations
from .metrics import (
    measure_json_file,
    measure_kotlin_file,
    measure_markdown_file,
    measure_python_file,
    measure_shell_file,
    measure_sql_file,
)


def verify_build_logic_kotlin(repo_root: Path) -> list[str]:
    source_root = repo_root / "gradle" / "build-logic" / "src"
    if not source_root.is_dir():
        return [f"{source_root}: missing build-logic source root for structural verification."]
    files = sorted(source_root.rglob("*.kt"))
    measurements = measure_files(repo_root, files, kotlin_budget_for, measure_kotlin_file)
    duplication_candidates = [
        measurement
        for measurement in measurements
        if "src/test/kotlin" not in measurement[0].as_posix()
        and measurement[1].role_name != "build-logic-reviewed-surface-catalog"
    ]
    return measurement_violations(measurements, duplication_candidates)


def verify_shell_release(repo_root: Path) -> list[str]:
    script_files = sorted((repo_root / "scripts").glob("*.sh"))
    files = [*script_files, repo_root / "check.sh", repo_root / "check_mutation.sh"]
    measurements = measure_files(repo_root, files, shell_budget_for, measure_shell_file)
    duplication_candidates = [
        measurement for measurement in measurements if not measurement[0].name.startswith("test-")
    ]
    return measurement_violations(measurements, duplication_candidates)


def verify_python_support(repo_root: Path) -> list[str]:
    files = sorted((repo_root / "scripts").rglob("*.py"))
    measurements = measure_files(repo_root, files, python_budget_for, measure_python_file)
    return measurement_violations(
        measurements,
        measurements,
        reviewed_surfaces=reviewed_surfaces_matching(
            lambda relative_path: relative_path.suffix == ".py"
        ),
    )


def verify_sqlite_sql(repo_root: Path) -> list[str]:
    schema_root = repo_root / "sqlite" / "src" / "main" / "resources"
    if not schema_root.is_dir():
        return [f"{schema_root}: missing SQLite schema root for structural verification."]
    files = sorted(schema_root.rglob("*.sql"))
    measurements = measure_files(repo_root, files, sql_budget_for, measure_sql_file)
    return measurement_violations(
        measurements,
        measurements,
        reviewed_surfaces=reviewed_surfaces_matching(
            lambda relative_path: relative_path.suffix == ".sql"
        ),
    )


def verify_json_resource(repo_root: Path) -> list[str]:
    files = repository_json_files(repo_root)
    measurements = measure_files(repo_root, files, json_budget_for, measure_json_file)
    return measurement_violations(
        measurements,
        measurements,
        reviewed_surfaces=reviewed_surfaces_matching(
            lambda relative_path: (
                relative_path.suffix == ".json" and include_json_path(relative_path)
            )
        ),
    )


def verify_markdown_docs(repo_root: Path) -> list[str]:
    files = repository_markdown_files(repo_root)
    measurements = measure_files(
        repo_root,
        files,
        markdown_budget_for,
        measure_markdown_file,
    )
    return measurement_violations(
        measurements,
        measurements,
        reviewed_surfaces=reviewed_surfaces_matching(
            lambda relative_path: relative_path.suffix == ".md"
        ),
    )


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
    measurements = measure_files(repo_root, filtered, gradle_kts_budget_for, measure_kotlin_file)
    return measurement_violations(measurements, measurements)
