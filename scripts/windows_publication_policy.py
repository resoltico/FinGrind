"""Isolated command entrypoint for the Windows publication policy control plane.

The native adapter invokes this file with Python isolated mode. The entrypoint reintroduces only its
own verified helper directory, then delegates every policy concern to its named owner modules.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path


def main() -> int:
    """Read one JSON request and write one deterministic JSON policy response."""

    helper_root = str(Path(__file__).resolve().parent)
    # Isolated mode omits the script directory; put only the already-verified helper root first.
    sys.path[:] = [entry for entry in sys.path if entry != helper_root]
    sys.path.insert(0, helper_root)
    from windows_publication_policy_boundary import PublicationPolicyError, load_json_object
    from windows_publication_policy_protocol import process_request

    try:
        request = load_json_object(sys.stdin.read(), "Windows publication policy request")
        response = process_request(request)
    except PublicationPolicyError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    json.dump(response, sys.stdout, sort_keys=True, separators=(",", ":"))
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
