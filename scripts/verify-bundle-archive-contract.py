#!/usr/bin/env python3
"""Verify the extracted self-contained bundle against the canonical release contract."""

from __future__ import annotations

import argparse
from pathlib import Path

from bundle_archive_verification_support import (
    verify_bundle_manifest,
    verify_bundle_root_files,
    verify_bundled_runtime,
    verify_distributed_module_identity,
)
from contract_values import load_contract_values


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify one extracted FinGrind bundle root against the canonical release contract."
    )
    parser.add_argument(
        "--repo-root",
        required=True,
        type=Path,
        help="Repository root that owns the canonical contract resources.",
    )
    parser.add_argument(
        "--bundle-root",
        required=True,
        type=Path,
        help="Extracted FinGrind bundle root to verify.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = args.repo_root.resolve()
    bundle_root = args.bundle_root.resolve()
    contract = load_contract_values(repo_root)

    verify_bundle_root_files(bundle_root, contract)
    verify_bundle_manifest(bundle_root, contract)
    verify_bundled_runtime(bundle_root, contract)
    verify_distributed_module_identity(bundle_root, contract)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
