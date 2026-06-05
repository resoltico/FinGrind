from __future__ import annotations

from pathlib import Path

from .models import FileBudget
from .reviewed_surfaces import REVIEWED_SURFACES


def kotlin_budget_for(relative_path: Path) -> FileBudget:
    name = relative_path.name
    path_text = relative_path.as_posix()
    if "/src/test/kotlin/" in path_text:
        return FileBudget(
            role_name="build-logic-test",
            max_physical_lines=560,
            max_logical_lines=340,
            max_import_like_lines=40,
            max_functions=22,
            max_nested_types=10,
            max_duplicate_window_lines=28,
            split_hint="split the test by scenario or helper-owner boundary.",
        )
    if name.endswith("Plugin.kt"):
        return FileBudget(
            role_name="build-logic-plugin",
            max_physical_lines=460,
            max_logical_lines=420,
            max_import_like_lines=42,
            max_functions=28,
            max_nested_types=10,
            max_duplicate_window_lines=30,
            split_hint="split plugin wiring by lifecycle owner such as distribution, runtime patching, publication, or task-registration seams.",
        )
    if name.startswith("ReviewedJava") and "Surface" in name:
        return FileBudget(
            role_name="build-logic-reviewed-surface-catalog",
            max_physical_lines=220,
            max_logical_lines=190,
            max_import_like_lines=8,
            max_functions=4,
            max_nested_types=2,
            max_duplicate_window_lines=80,
            split_hint="split the reviewed-surface inventory by one catalog family instead of growing one broad waiver registry.",
        )
    if "Contract" in name or "Manifest" in name or "Topology" in name:
        return FileBudget(
            role_name="build-logic-contract-or-catalog",
            max_physical_lines=300,
            max_logical_lines=250,
            max_import_like_lines=35,
            max_functions=40,
            max_nested_types=12,
            max_duplicate_window_lines=28,
            split_hint="split the file by one published contract family instead of keeping all readers or descriptors together.",
        )
    if name.endswith("Metrics.kt"):
        return FileBudget(
            role_name="build-logic-metrics",
            max_physical_lines=240,
            max_logical_lines=220,
            max_import_like_lines=18,
            max_functions=16,
            max_nested_types=6,
            max_duplicate_window_lines=26,
            split_hint="split measurement files by one metric family instead of mixing AST, method, and rendering concerns together.",
        )
    if name.endswith("Verification.kt") or name.endswith("Verifier.kt") or name.endswith("Task.kt"):
        return FileBudget(
            role_name="build-logic-task-or-verifier",
            max_physical_lines=450,
            max_logical_lines=400,
            max_import_like_lines=35,
            max_functions=24,
            max_nested_types=10,
            max_duplicate_window_lines=26,
            split_hint="split task logic by measurement, orchestration, and output-owner seams.",
        )
    return FileBudget(
        role_name="build-logic-support",
        max_physical_lines=240,
        max_logical_lines=210,
        max_import_like_lines=30,
        max_functions=20,
        max_nested_types=8,
        max_duplicate_window_lines=24,
        split_hint="split the file by one named support responsibility family.",
    )


def shell_budget_for(relative_path: Path) -> FileBudget:
    name = relative_path.name
    if name == "check.sh":
        return FileBudget(
            role_name="release-shell-orchestrator",
            max_physical_lines=380,
            max_logical_lines=310,
            max_import_like_lines=7,
            max_functions=18,
            max_nested_types=0,
            max_duplicate_window_lines=32,
            split_hint="split the top-level gate by stage orchestration, diagnostics, and environment-owner helpers.",
        )
    if name.startswith("test-"):
        return FileBudget(
            role_name="release-shell-test",
            max_physical_lines=720,
            max_logical_lines=620,
            max_import_like_lines=8,
            max_functions=18,
            max_nested_types=0,
            max_duplicate_window_lines=34,
            split_hint="split the test by shell harness versus fixture/assertion owner.",
        )
    if name.endswith("-support.sh") or name.endswith("-common.sh"):
        return FileBudget(
            role_name="release-shell-support",
            max_physical_lines=420,
            max_logical_lines=350,
            max_import_like_lines=6,
            max_functions=24,
            max_nested_types=0,
            max_duplicate_window_lines=30,
            split_hint="split the support script by path derivation, process control, and contract-owner helpers.",
        )
    return FileBudget(
        role_name="release-shell-entrypoint",
        max_physical_lines=260,
        max_logical_lines=220,
        max_import_like_lines=5,
        max_functions=12,
        max_nested_types=0,
        max_duplicate_window_lines=28,
        split_hint="split the entrypoint by one operational concern per script.",
    )


def python_budget_for(relative_path: Path) -> FileBudget:
    reviewed = REVIEWED_SURFACES.get(relative_path.as_posix())
    if reviewed is not None:
        return reviewed.budget
    path_text = relative_path.as_posix()
    if path_text.startswith("scripts/release_smoke_workflow/"):
        return python_release_smoke_support_budget()
    if relative_path.name.startswith("test-"):
        return python_script_test_budget()
    return python_support_budget()


def python_release_smoke_support_budget() -> FileBudget:
    return FileBudget(
        role_name="python-release-smoke-support",
        max_physical_lines=240,
        max_logical_lines=200,
        max_import_like_lines=14,
        max_functions=14,
        max_nested_types=6,
        max_duplicate_window_lines=24,
        split_hint="split the release-smoke helper by one scenario, CLI, or assertion responsibility family.",
    )


def python_script_test_budget() -> FileBudget:
    return FileBudget(
        role_name="python-script-test",
        max_physical_lines=320,
        max_logical_lines=260,
        max_import_like_lines=14,
        max_functions=16,
        max_nested_types=6,
        max_duplicate_window_lines=24,
        split_hint="split the Python regression test by fixture generation versus assertion-owner helpers.",
    )


def python_support_budget() -> FileBudget:
    return FileBudget(
        role_name="python-support",
        max_physical_lines=320,
        max_logical_lines=260,
        max_import_like_lines=14,
        max_functions=18,
        max_nested_types=8,
        max_duplicate_window_lines=24,
        split_hint="split the Python support script by one named control-plane concern.",
    )


def sql_budget_for(relative_path: Path) -> FileBudget:
    reviewed = REVIEWED_SURFACES.get(relative_path.as_posix())
    if reviewed is not None:
        return reviewed.budget
    return sqlite_sql_support_budget()


def sqlite_sql_support_budget() -> FileBudget:
    return FileBudget(
        role_name="sqlite-sql-support",
        max_physical_lines=220,
        max_logical_lines=180,
        max_import_like_lines=18,
        max_functions=0,
        max_nested_types=0,
        max_duplicate_window_lines=24,
        split_hint="split the SQL support surface by one schema or query responsibility family.",
    )
