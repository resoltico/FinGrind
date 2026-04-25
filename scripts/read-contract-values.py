#!/usr/bin/env python3
"""Print the canonical FinGrind release/runtime contract values as JSON."""

from __future__ import annotations

import json
import sys

from contract_values import load_contract_values, repository_root


def main() -> int:
    repo_root = repository_root(__file__)
    json.dump(load_contract_values(repo_root), sys.stdout, sort_keys=True)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
