from __future__ import annotations

import argparse
import sys
from pathlib import Path

from .verification import (
    verify_build_logic_kotlin,
    verify_gradle_kts,
    verify_markdown_docs,
    verify_python_support,
    verify_shell_release,
    verify_sqlite_sql,
)

SUPPORTED_SURFACES = (
    "build-logic-kotlin",
    "gradle-kts",
    "markdown-docs",
    "shell-release",
    "python-support",
    "sqlite-sql",
)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Verify FinGrind structural governance for non-Java control-plane surfaces."
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
        help="Repository root to verify.",
    )
    parser.add_argument(
        "--surface",
        action="append",
        choices=SUPPORTED_SURFACES,
        help="Surface(s) to verify. Defaults to all supported surfaces.",
    )
    args = parser.parse_args(argv)
    repo_root = args.repo_root.resolve()
    selected_surfaces = args.surface or list(SUPPORTED_SURFACES)
    violations: list[str] = []
    for surface in selected_surfaces:
        if surface == "build-logic-kotlin":
            violations.extend(verify_build_logic_kotlin(repo_root))
        elif surface == "gradle-kts":
            violations.extend(verify_gradle_kts(repo_root))
        elif surface == "markdown-docs":
            violations.extend(verify_markdown_docs(repo_root))
        elif surface == "shell-release":
            violations.extend(verify_shell_release(repo_root))
        elif surface == "python-support":
            violations.extend(verify_python_support(repo_root))
        elif surface == "sqlite-sql":
            violations.extend(verify_sqlite_sql(repo_root))
    if violations:
        print("FinGrind structural-governance violations:", file=sys.stderr)
        for violation in violations:
            print(violation, file=sys.stderr)
        return 1
    return 0
