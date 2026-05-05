#!/usr/bin/env python3
"""Render canonical C compiler flags for the managed SQLite contract."""

from __future__ import annotations

import json
import sys
from pathlib import Path


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    contract_path = (
        Path(sys.argv[1]).resolve()
        if len(sys.argv) > 1
        else repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json"
    )
    document = json.loads(contract_path.read_text(encoding="utf-8"))
    compile_options = document.get("requiredCompileOptions")
    if not isinstance(compile_options, list) or not compile_options:
        raise SystemExit(
            "managed SQLite contract must declare one non-empty requiredCompileOptions array"
        )

    flags: list[str] = []
    for option in compile_options:
        normalized = option.strip() if isinstance(option, str) else ""
        if not normalized:
            raise SystemExit("managed SQLite compile options must be non-blank strings")
        macro = normalized if normalized.startswith("SQLITE_") else "SQLITE_" + normalized
        if "=" not in macro:
            macro += "=1"
        flags.append("-D" + macro)

    print(" ".join(flags))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
