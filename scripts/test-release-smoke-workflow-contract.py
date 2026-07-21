#!/usr/bin/env python3
"""Regression checks for the shared release-smoke workflow package contract."""

from __future__ import annotations

import pathlib
import sys


def main(argv: list[str]) -> int:
    if len(argv) != 1:
        raise SystemExit("usage: test-release-smoke-workflow-contract.py <repo-root>")
    repo_root = pathlib.Path(argv[0]).resolve()
    sys.path.insert(0, str(repo_root / "scripts"))

    from release_smoke_workflow.contract_test_suite import run_contract_suite

    run_contract_suite(repo_root)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
