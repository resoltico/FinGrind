from __future__ import annotations

import re

from .support import require, require_match


def assert_grouped_review_text(
    text_output: str, credential_key_id: str, head_order: str, label: str
) -> None:
    require_match(
        text_output,
        r"^Attestation Review$|^Book Attestation Review Required$",
        f"{label} did not render its title",
    )
    require(
        text_output.count(f"Credential key ID: {credential_key_id}") == 1,
        f"{label} did not group the credential declaration exactly once",
    )
    require(
        text_output.count("Review declaration") == 1,
        f"{label} did not render exactly one grouped review declaration",
    )
    require_match(
        text_output,
        rf"Review window:\s+0 through {re.escape(head_order)}",
        f"{label} did not render the full review window",
    )
    require_match(
        text_output,
        rf"Affected operation orders:\s+0-{re.escape(head_order)}$",
        f"{label} did not render the complete consecutive affected-order range",
    )
