#!/usr/bin/env python3
"""Print the canonical FinGrind release-publication contract as JSON."""

from __future__ import annotations

import json
import sys

from contract_values import repository_root
from release_publication_contract import load_release_publication_contract


def main() -> int:
    repo_root = repository_root(__file__)
    json.dump(load_release_publication_contract(repo_root), sys.stdout, sort_keys=True)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
