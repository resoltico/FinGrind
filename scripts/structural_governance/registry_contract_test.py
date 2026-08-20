from __future__ import annotations

import tempfile
from pathlib import Path

from structural_governance.registry import _load_fragment_documents


def main() -> int:
    with tempfile.TemporaryDirectory() as temporary_directory:
        java_directory = Path(temporary_directory) / "java"
        java_directory.mkdir()

        documents = _load_fragment_documents(java_directory, "java")

    if documents:
        raise AssertionError("empty reviewed-surface categories must load without fragments")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
