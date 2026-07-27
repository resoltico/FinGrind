#!/usr/bin/env python3
"""Render the canonical FinGrind release-publication plan as JSON."""

from __future__ import annotations

import argparse
import json
import sys

from contract_values import repository_root
from release_publication_contract import load_release_publication_plan


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Print the canonical FinGrind release-publication plan as JSON."
    )
    parser.add_argument("--version", required=True, help="Release version without the v prefix.")
    parser.add_argument(
        "--repository-owner",
        help="Optional GitHub repository owner used to render the public container image reference.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = repository_root(__file__)
    json.dump(
        load_release_publication_plan(
            repo_root,
            version=args.version,
            repository_owner=args.repository_owner,
        ),
        sys.stdout,
        sort_keys=True,
    )
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
