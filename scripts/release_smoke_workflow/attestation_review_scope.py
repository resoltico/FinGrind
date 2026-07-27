from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any

from .models import ReleaseSmokeConfig, SmokePath
from .scenario_paths import sibling_smoke_path
from .support import require


@dataclass(frozen=True)
class ReviewScope:
    credential_key_id: str
    book_id: str
    operation_order: str
    operation_head: str
    previous_head: str


def review_scope(verification: dict[str, Any], config: ReleaseSmokeConfig) -> ReviewScope:
    """Extract the immutable review context from a verified book envelope."""
    payload = verification.get("payload")
    require(
        isinstance(payload, dict), f"{config.label} verify-book did not expose a payload object"
    )
    registry = payload.get("registry")
    require(
        isinstance(registry, dict),
        f"{config.label} verify-book did not expose an attestation registry",
    )
    credentials = registry.get("credentials")
    require(
        isinstance(credentials, list) and credentials,
        f"{config.label} verify-book did not expose an attestation credential",
    )
    require(
        all(isinstance(credential, dict) for credential in credentials),
        f"{config.label} verify-book exposed a malformed attestation credential list",
    )
    founder_credentials = [
        credential
        for credential in credentials
        if credential.get("principalId") == config.attestation_founder_principal_id
    ]
    require(
        len(founder_credentials) == 1,
        f"{config.label} verify-book did not expose exactly one credential for the attestation founder",
    )
    credential = founder_credentials[0]
    key_id = credential.get("keyId")
    verified_head = payload.get("verifiedAttestationHead")
    require(
        isinstance(key_id, str) and re.fullmatch(r"[0-9a-f]{64}", key_id) is not None,
        f"{config.label} verify-book did not expose a canonical credential key ID",
    )
    require(
        isinstance(verified_head, dict),
        f"{config.label} verify-book did not expose verifiedAttestationHead",
    )
    if not isinstance(key_id, str) or not isinstance(verified_head, dict):
        raise TypeError("require must reject an invalid verified review scope")
    operation_order = verified_head.get("operationOrder")
    operation_head = verified_head.get("operationHead")
    previous_head = payload.get("previousHead")
    require(
        isinstance(operation_order, str)
        and re.fullmatch(r"0|[1-9][0-9]*", operation_order) is not None,
        f"{config.label} verify-book did not expose a canonical verified attestation order",
    )
    require(
        isinstance(operation_head, str)
        and re.fullmatch(r"[0-9a-f]{64}", operation_head) is not None,
        f"{config.label} verify-book did not expose a canonical verified attestation head",
    )
    require(
        isinstance(previous_head, str) and re.fullmatch(r"[0-9a-f]{64}", previous_head) is not None,
        f"{config.label} verify-book did not expose a canonical previous attestation head",
    )
    book_id = payload.get("bookId")
    require(
        isinstance(book_id, str) and bool(book_id),
        f"{config.label} verify-book did not expose a book ID",
    )
    if (
        not isinstance(book_id, str)
        or not isinstance(operation_order, str)
        or not isinstance(operation_head, str)
        or not isinstance(previous_head, str)
    ):
        raise TypeError("require must reject an incomplete verified review scope")
    return ReviewScope(key_id, book_id, operation_order, operation_head, previous_head)


def write_review_file(
    config: ReleaseSmokeConfig,
    filename: str,
    credential_key_id: str,
    first_affected_order: str,
    last_affected_order: str | None,
) -> SmokePath:
    review = {
        "credentialKeyId": credential_key_id,
        "firstAffectedOrder": first_affected_order,
    }
    if last_affected_order is not None:
        review["lastAffectedOrder"] = last_affected_order
    review_file = sibling_smoke_path(config.attestation_receipt, filename)
    review_file.local_path.write_text(
        json.dumps({"compromiseReviews": [review]}, separators=(",", ":")),
        encoding="utf-8",
    )
    return review_file
