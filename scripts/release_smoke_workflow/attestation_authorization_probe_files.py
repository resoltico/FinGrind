from __future__ import annotations

import copy
import json
import os
from dataclasses import dataclass
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

from .models import ReleaseSmokeConfig, SmokePath
from .scenario_paths import sibling_smoke_path
from .support import require


@dataclass(frozen=True)
class AuthorizationProbe:
    principal_id: str
    key: SmokePath
    passphrase: SmokePath


def prepare_authorization_probe(config: ReleaseSmokeConfig) -> AuthorizationProbe:
    prefix = config.request_prefix + "-authorization-probe"
    passphrase = sibling_smoke_path(config.attestation_founder_passphrase, prefix + ".passphrase")
    passphrase.local_path.write_text(
        "release-smoke-authorization-probe-passphrase\n", encoding="utf-8"
    )
    if os.name == "posix":
        passphrase.local_path.chmod(0o600)
    return AuthorizationProbe(
        principal_id=str(
            uuid5(
                NAMESPACE_URL,
                "fingrind-release-smoke:" + config.request_prefix + ":authorization-probe",
            )
        ),
        key=sibling_smoke_path(config.attestation_founder_key, prefix + ".fgatk"),
        passphrase=passphrase,
    )


def write_authorization_probe_requests(
    config: ReleaseSmokeConfig,
    probe: AuthorizationProbe,
    spki: str,
) -> dict[str, SmokePath]:
    paths = {
        name: sibling_smoke_path(config.request_sale, config.request_prefix + "-" + file_name)
        for name, file_name in _REQUEST_FILE_NAMES.items()
    }
    _write_json(
        paths["enroll"].local_path,
        {
            "principalId": probe.principal_id,
            "credentialSpki": spki,
            "credentialPurpose": "operator",
        },
    )
    _write_json(
        paths["postQuorumTwo"].local_path,
        {
            "policyRules": [{"capability": "post", "quorum": 2}],
            "capabilityGrants": [
                {"principalId": probe.principal_id, "capability": "post", "state": "grant"}
            ],
        },
    )
    _write_json(
        paths["postQuorumOne"].local_path,
        {"policyRules": [{"capability": "post", "quorum": 1}]},
    )
    _write_json(
        paths["revokeSecondPost"].local_path,
        {
            "capabilityGrants": [
                {"principalId": probe.principal_id, "capability": "post", "state": "revoke"}
            ]
        },
    )
    base_sale = _base_sale_request(config)
    for name, suffix in _SALE_REQUEST_SUFFIXES.items():
        _write_json(
            paths[name].local_path,
            _sale_request(base_sale, config.request_prefix, suffix),
        )
    return paths


def _base_sale_request(config: ReleaseSmokeConfig) -> dict[str, object]:
    request = json.loads(config.request_sale.local_path.read_text(encoding="utf-8"))
    require(isinstance(request, dict), "release-smoke sale fixture must be a JSON object")
    return request


def _sale_request(base_sale: dict[str, object], request_prefix: str, suffix: str) -> object:
    request = copy.deepcopy(base_sale)
    evidence = request["evidence"]
    provenance = request["provenance"]
    require(isinstance(evidence, dict), "release-smoke sale fixture must expose evidence")
    require(isinstance(provenance, dict), "release-smoke sale fixture must expose provenance")
    source_documents = evidence["sourceDocuments"]
    require(
        isinstance(source_documents, list) and len(source_documents) == 1,
        "release-smoke sale fixture must expose exactly one source document",
    )
    source_document = source_documents[0]
    require(
        isinstance(source_document, dict), "release-smoke sale fixture source document is invalid"
    )
    source_document["sourceDocumentId"] = request_prefix + "-authorization-probe-" + suffix
    provenance["commandId"] = str(
        uuid5(NAMESPACE_URL, "fingrind-release-smoke:" + request_prefix + ":" + suffix)
    )
    provenance["idempotencyKey"] = request_prefix + "-authorization-probe-" + suffix
    provenance["causationId"] = request_prefix + "-authorization-probe-" + suffix
    return request


def _write_json(path: Path, payload: object) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


_REQUEST_FILE_NAMES = {
    "enroll": "enroll-authorization-probe.json",
    "postQuorumTwo": "post-quorum-two.json",
    "postQuorumOne": "post-quorum-one.json",
    "revokeSecondPost": "revoke-authorization-probe-post.json",
    "saleQuorumTwo": "sale-quorum-two.json",
    "saleQuorumExcess": "sale-quorum-excess.json",
    "saleCapabilityInvalid": "sale-capability-invalid.json",
    "saleKeyPrincipalMismatch": "sale-key-principal-mismatch.json",
}

_SALE_REQUEST_SUFFIXES = {
    "saleQuorumTwo": "quorum-two",
    "saleQuorumExcess": "quorum-excess",
    "saleCapabilityInvalid": "capability-invalid",
    "saleKeyPrincipalMismatch": "key-principal-mismatch",
}
