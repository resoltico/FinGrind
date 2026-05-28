#!/usr/bin/env python3
"""Verify non-Java structural-governance surfaces for FinGrind."""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class FileBudget:
    role_name: str
    max_physical_lines: int
    max_logical_lines: int
    max_import_like_lines: int
    max_functions: int
    max_nested_types: int
    max_duplicate_window_lines: int
    split_hint: str


@dataclass(frozen=True)
class FileMetrics:
    physical_lines: int
    logical_lines: int
    import_like_lines: int
    functions: int
    nested_types: int
    normalized_nonempty_lines: tuple[str, ...]


KOTLIN_COMMENT_BLOCK_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
KOTLIN_LINE_COMMENT_RE = re.compile(r"//.*?$", re.MULTILINE)
KOTLIN_TRIPLE_STRING_RE = re.compile(r'""".*?"""', re.DOTALL)
KOTLIN_STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')
KOTLIN_CHAR_RE = re.compile(r"'(?:\\.|[^'\\])'")
KOTLIN_FUNCTION_RE = re.compile(r"\bfun\s+[A-Za-z_`][A-Za-z0-9_`<>,\s]*\(")
KOTLIN_TYPE_RE = re.compile(r"\b(?:class|interface|object)\s+[A-Z][A-Za-z0-9_`]*")
KOTLIN_IMPORT_RE = re.compile(r"^\s*import\s+", re.MULTILINE)

SHELL_COMMENT_LINE_RE = re.compile(r"^\s*#")
SHELL_FUNCTION_RE = re.compile(
    r"^\s*(?:function\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*(?:\(\))?\s*\{",
    re.MULTILINE,
)
SHELL_SOURCE_RE = re.compile(r"^\s*(?:source|\.)\s+", re.MULTILINE)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Verify FinGrind structural governance for non-Java control-plane surfaces."
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parent.parent,
        help="Repository root to verify.",
    )
    parser.add_argument(
        "--surface",
        action="append",
        choices=("build-logic-kotlin", "shell-release"),
        help="Surface(s) to verify. Defaults to all supported surfaces.",
    )
    args = parser.parse_args(argv)
    repo_root = args.repo_root.resolve()
    selected_surfaces = args.surface or ["build-logic-kotlin", "shell-release"]
    violations: list[str] = []
    for surface in selected_surfaces:
        if surface == "build-logic-kotlin":
            violations.extend(verify_build_logic_kotlin(repo_root))
        elif surface == "shell-release":
            violations.extend(verify_shell_release(repo_root))
        else:
            raise AssertionError(f"Unhandled surface {surface}")

    if violations:
        sys.stderr.write("FinGrind structural-governance violations:\n")
        for violation in violations:
            sys.stderr.write(f"{violation}\n")
        return 1
    return 0


def verify_build_logic_kotlin(repo_root: Path) -> list[str]:
    source_root = repo_root / "gradle" / "build-logic" / "src"
    if not source_root.is_dir():
        return [f"{source_root}: missing build-logic source root for structural verification."]
    files = sorted(source_root.rglob("*.kt"))
    violations: list[str] = []
    measurements: list[tuple[Path, FileBudget, FileMetrics]] = []
    for file_path in files:
        relative_path = file_path.relative_to(repo_root)
        budget = kotlin_budget_for(relative_path)
        metrics = measure_kotlin_file(file_path)
        measurements.append((relative_path, budget, metrics))
        violations.extend(check_metrics(relative_path, budget, metrics))
    duplication_candidates = [
        measurement
        for measurement in measurements
        if "src/test/kotlin" not in measurement[0].as_posix()
    ]
    if duplication_candidates:
        violations.extend(
            duplicate_window_violations(
                duplication_candidates,
                minimum_window_lines=min(
                    budget.max_duplicate_window_lines for _, budget, _ in duplication_candidates
                ),
            )
        )
    return violations


def verify_shell_release(repo_root: Path) -> list[str]:
    script_files = sorted((repo_root / "scripts").glob("*.sh"))
    files = [*script_files, repo_root / "check.sh"]
    violations: list[str] = []
    measurements: list[tuple[Path, FileBudget, FileMetrics]] = []
    for file_path in files:
        if not file_path.is_file():
            continue
        relative_path = file_path.relative_to(repo_root)
        budget = shell_budget_for(relative_path)
        metrics = measure_shell_file(file_path)
        measurements.append((relative_path, budget, metrics))
        violations.extend(check_metrics(relative_path, budget, metrics))
    shell_duplication_candidates = [
        measurement for measurement in measurements if not measurement[0].name.startswith("test-")
    ]
    if shell_duplication_candidates:
        min_window = min(
            budget.max_duplicate_window_lines for _, budget, _ in shell_duplication_candidates
        )
        violations.extend(
            duplicate_window_violations(
                shell_duplication_candidates, minimum_window_lines=min_window
            )
        )
    return violations


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
    measurements: list[tuple[Path, FileBudget, FileMetrics]], minimum_window_lines: int
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


def kotlin_budget_for(relative_path: Path) -> FileBudget:
    name = relative_path.name
    path_text = relative_path.as_posix()
    if "/src/test/kotlin/" in path_text:
        return FileBudget(
            role_name="build-logic-test",
            max_physical_lines=700,
            max_logical_lines=380,
            max_import_like_lines=40,
            max_functions=24,
            max_nested_types=12,
            max_duplicate_window_lines=28,
            split_hint="split the test by scenario or helper-owner boundary.",
        )
    if name.endswith("Plugin.kt"):
        return FileBudget(
            role_name="build-logic-plugin",
            max_physical_lines=540,
            max_logical_lines=480,
            max_import_like_lines=45,
            max_functions=30,
            max_nested_types=10,
            max_duplicate_window_lines=30,
            split_hint="split plugin wiring by lifecycle owner such as distribution, runtime patching, publication, or task-registration seams.",
        )
    if "Contract" in name or "Manifest" in name or "Topology" in name:
        return FileBudget(
            role_name="build-logic-contract-or-catalog",
            max_physical_lines=340,
            max_logical_lines=280,
            max_import_like_lines=35,
            max_functions=44,
            max_nested_types=12,
            max_duplicate_window_lines=28,
            split_hint="split the file by one published contract family instead of keeping all readers or descriptors together.",
        )
    if name.endswith("Verification.kt") or name.endswith("Verifier.kt") or name.endswith("Task.kt"):
        return FileBudget(
            role_name="build-logic-task-or-verifier",
            max_physical_lines=500,
            max_logical_lines=450,
            max_import_like_lines=35,
            max_functions=26,
            max_nested_types=10,
            max_duplicate_window_lines=26,
            split_hint="split task logic by measurement, orchestration, and output-owner seams.",
        )
    return FileBudget(
        role_name="build-logic-support",
        max_physical_lines=320,
        max_logical_lines=260,
        max_import_like_lines=30,
        max_functions=22,
        max_nested_types=8,
        max_duplicate_window_lines=24,
        split_hint="split the file by one named support responsibility family.",
    )


def shell_budget_for(relative_path: Path) -> FileBudget:
    name = relative_path.name
    if name == "check.sh":
        return FileBudget(
            role_name="release-shell-orchestrator",
            max_physical_lines=420,
            max_logical_lines=340,
            max_import_like_lines=7,
            max_functions=18,
            max_nested_types=0,
            max_duplicate_window_lines=32,
            split_hint="split the top-level gate by stage orchestration, diagnostics, and environment-owner helpers.",
        )
    if name.startswith("test-"):
        return FileBudget(
            role_name="release-shell-test",
            max_physical_lines=780,
            max_logical_lines=680,
            max_import_like_lines=8,
            max_functions=18,
            max_nested_types=0,
            max_duplicate_window_lines=34,
            split_hint="split the test by shell harness versus fixture/assertion owner.",
        )
    if name.endswith("-support.sh") or name.endswith("-common.sh"):
        return FileBudget(
            role_name="release-shell-support",
            max_physical_lines=450,
            max_logical_lines=380,
            max_import_like_lines=6,
            max_functions=28,
            max_nested_types=0,
            max_duplicate_window_lines=30,
            split_hint="split the support script by path derivation, process control, and contract-owner helpers.",
        )
    return FileBudget(
        role_name="release-shell-entrypoint",
        max_physical_lines=280,
        max_logical_lines=240,
        max_import_like_lines=5,
        max_functions=14,
        max_nested_types=0,
        max_duplicate_window_lines=28,
        split_hint="split the entrypoint by one operational concern per script.",
    )


def measure_kotlin_file(path: Path) -> FileMetrics:
    text = path.read_text(encoding="utf-8")
    sanitized = sanitize_kotlin(text)
    normalized_lines = normalized_nonempty_lines(sanitized.splitlines())
    nested_type_matches = len(KOTLIN_TYPE_RE.findall(sanitized))
    return FileMetrics(
        physical_lines=len(text.splitlines()),
        logical_lines=len(normalized_lines),
        import_like_lines=len(KOTLIN_IMPORT_RE.findall(text)),
        functions=len(KOTLIN_FUNCTION_RE.findall(sanitized)),
        nested_types=max(0, nested_type_matches - 1),
        normalized_nonempty_lines=normalized_lines,
    )


def measure_shell_file(path: Path) -> FileMetrics:
    text = path.read_text(encoding="utf-8")
    normalized_lines = normalized_nonempty_lines(strip_shell_comments(text).splitlines())
    return FileMetrics(
        physical_lines=len(text.splitlines()),
        logical_lines=len(normalized_lines),
        import_like_lines=len(SHELL_SOURCE_RE.findall(text)),
        functions=len(SHELL_FUNCTION_RE.findall(text)),
        nested_types=0,
        normalized_nonempty_lines=normalized_lines,
    )


def sanitize_kotlin(text: str) -> str:
    without_block_comments = KOTLIN_COMMENT_BLOCK_RE.sub(" ", text)
    without_triple_strings = KOTLIN_TRIPLE_STRING_RE.sub('""', without_block_comments)
    without_strings = KOTLIN_STRING_RE.sub('""', without_triple_strings)
    without_chars = KOTLIN_CHAR_RE.sub("' '", without_strings)
    without_line_comments = KOTLIN_LINE_COMMENT_RE.sub("", without_chars)
    return without_line_comments


def strip_shell_comments(text: str) -> str:
    cleaned: list[str] = []
    for line in text.splitlines():
        if SHELL_COMMENT_LINE_RE.match(line):
            cleaned.append("")
            continue
        comment_index = line.find(" #")
        if comment_index >= 0:
            line = line[:comment_index]
        cleaned.append(line)
    return "\n".join(cleaned)


def normalized_nonempty_lines(lines: Iterable[str]) -> tuple[str, ...]:
    normalized: list[str] = []
    for raw_line in lines:
        line = re.sub(r"\s+", " ", raw_line.strip())
        if not line:
            continue
        line = re.sub(r'"(?:\\.|[^"\\])*"', '""', line)
        line = re.sub(r"\b\d+\b", "0", line)
        normalized.append(line)
    return tuple(normalized)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
