from __future__ import annotations

import sys

from .config import load_config
from .models import ReleaseSmokeFailure
from .release_smoke_workflow import run_release_smoke


def main() -> int:
    try:
        run_release_smoke(load_config())
    except ReleaseSmokeFailure as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0
