from __future__ import annotations

from pathlib import Path

from .models import FileBudget


def markdown_budget_for(relative_path: Path) -> FileBudget:
    path_text = relative_path.as_posix()
    name = relative_path.name
    if name == "CHANGELOG.md":
        return FileBudget(
            role_name="docs-changelog-history",
            max_physical_lines=3200,
            max_logical_lines=2600,
            max_import_like_lines=240,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=42,
            split_hint="keep changelog entries concise and move evergreen guidance into focused docs instead of growing the history surface.",
        )
    if path_text == "docs/sqlite/SCHEMA_CORE.md":
        return FileBudget(
            role_name="docs-schema-reference",
            max_physical_lines=1450,
            max_logical_lines=1320,
            max_import_like_lines=180,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=36,
            split_hint="split the schema reference by one schema responsibility family before adding another independent contract cluster.",
        )
    if path_text == "docs/RELEASE_PROTOCOL.md":
        return FileBudget(
            role_name="docs-release-protocol",
            max_physical_lines=880,
            max_logical_lines=720,
            max_import_like_lines=120,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=34,
            split_hint="split release prose by one publication phase or operator path instead of one broad protocol narrative.",
        )
    if path_text.startswith("docs/USER_"):
        return FileBudget(
            role_name="docs-user-guide",
            max_physical_lines=760,
            max_logical_lines=640,
            max_import_like_lines=110,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=34,
            split_hint="split the user guide by one operator journey or command family instead of one umbrella document.",
        )
    if path_text.startswith("docs/DOC_"):
        return FileBudget(
            role_name="docs-canonical-reference",
            max_physical_lines=950,
            max_logical_lines=700,
            max_import_like_lines=120,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=34,
            split_hint="split the canonical reference by one contract or subsystem family instead of growing one broad document.",
        )
    if path_text.startswith("docs/DEVELOPER"):
        return FileBudget(
            role_name="docs-developer-guide",
            max_physical_lines=620,
            max_logical_lines=520,
            max_import_like_lines=100,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=34,
            split_hint="split the developer guide by one build, runtime, or workflow responsibility family.",
        )
    if path_text.startswith("docs/ADR_"):
        return FileBudget(
            role_name="docs-adr",
            max_physical_lines=240,
            max_logical_lines=190,
            max_import_like_lines=36,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=32,
            split_hint="split the ADR or move operational detail into a guide before the decision record grows into a mixed document.",
        )
    if name == "README.md" or name == "AGENTS.md":
        return FileBudget(
            role_name="docs-root-landing",
            max_physical_lines=260,
            max_logical_lines=190,
            max_import_like_lines=44,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=30,
            split_hint="keep the landing surface concise and move deep reference detail into focused docs.",
        )
    return FileBudget(
        role_name="docs-support",
        max_physical_lines=220,
        max_logical_lines=170,
        max_import_like_lines=30,
        max_functions=0,
        max_nested_types=0,
        max_duplicate_window_lines=30,
        split_hint="split the prose surface by one published responsibility family.",
    )


def gradle_kts_budget_for(relative_path: Path) -> FileBudget:
    path_text = relative_path.as_posix()
    name = relative_path.name
    if path_text == "gradle/build-logic/build.gradle.kts":
        return FileBudget(
            role_name="gradle-included-build-script",
            max_physical_lines=130,
            max_logical_lines=115,
            max_import_like_lines=18,
            max_functions=2,
            max_nested_types=0,
            max_duplicate_window_lines=24,
            split_hint="keep included-build bootstrap wiring thin and move extra logic into typed build-logic owners.",
        )
    if name == "settings.gradle.kts" or path_text.endswith("/settings.gradle.kts"):
        return FileBudget(
            role_name="gradle-settings-script",
            max_physical_lines=36,
            max_logical_lines=28,
            max_import_like_lines=6,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=20,
            split_hint="keep settings scripts declarative and move extra logic into typed build owners.",
        )
    return FileBudget(
        role_name="gradle-module-script",
        max_physical_lines=110,
        max_logical_lines=95,
        max_import_like_lines=14,
        max_functions=2,
        max_nested_types=0,
        max_duplicate_window_lines=22,
        split_hint="keep module build scripts declarative and move behavior into shared typed plugins.",
    )
