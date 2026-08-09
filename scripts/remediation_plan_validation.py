"""Read-only validation for the public Ledger-1 remediation projection."""

from __future__ import annotations

import datetime as datetime_module
import subprocess
import sys
from pathlib import Path

from remediation_plan_checkpoint import validate_successor_checkpoint
from remediation_plan_graph_validation import validate_graph
from remediation_plan_support import (
    JsonValue,
    RemediationError,
    canonical_bytes,
    canonical_json,
    collect_index,
    public_key_fingerprint,
    remap,
    source_digest,
    verify_signature,
)

PLAN_CATEGORIES = (
    "actors",
    "baselines",
    "contexts",
    "decisions",
    "defects",
    "evidence",
    "nodes",
    "releases",
    "scenarios",
    "statusHistory",
    "workUnits",
)
PROJECTION_DOMAIN = "fingrind-remediation-public-projection:v1"
SCHEMA_DOMAIN = "fingrind-remediation-public-schema:v1"
RECEIPT_SCHEMA = "urn:fingrind:remediation:public-projection-receipt:v2"
PUBLIC_ACTION = "AUTHORIZE_PUBLIC_REMEDIATION_PROJECTION_V2"


def validate_authority(authority_root: Path) -> None:
    """Run the owner-only authority verifier before reading its approved source."""
    tool = authority_root / "tools" / "authority_control.py"
    if not tool.is_file():
        raise RemediationError("restricted root lacks the private authority verifier")
    result = subprocess.run(
        [sys.executable, str(tool), "validate"],
        cwd=authority_root,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode:
        raise RemediationError(f"private authority validation failed: {result.stderr.strip()}")


def _record_paths(root: Path, directory: str) -> dict[str, bytes]:
    records: dict[str, bytes] = {}
    for path in sorted((root / directory).rglob("*.json")):
        value = canonical_json(path)
        output_path = path.relative_to(root).as_posix()
        source_path = remap(output_path, "remediation/", "reference/public/")
        if not isinstance(source_path, str):
            raise RemediationError("remediation path rebase failed")
        records[source_path] = canonical_bytes(_source_equivalent(value))
    return records


def _source_equivalent(value: JsonValue, field: str | None = None) -> JsonValue:
    """Restore only projection pointers, never declared future-output paths such as dossierRef."""
    if isinstance(value, dict):
        return {key: _source_equivalent(item, key) for key, item in value.items()}
    if isinstance(value, list):
        return [_source_equivalent(item, field) for item in value]
    pointer_fields = {"children", "leaves", *PLAN_CATEGORIES}
    if isinstance(value, str) and field in pointer_fields and value.startswith("remediation/"):
        return "reference/public/" + value.removeprefix("remediation/")
    return value


def _digest_claim(receipt: dict[str, JsonValue], name: str, domain: str) -> str:
    value = receipt.get(name)
    if not isinstance(value, dict):
        raise RemediationError(f"receipt lacks {name}")
    expected = {"classification", "digest", "digestAlgorithm", "digestName", "domain"}
    if set(value) != expected or value.get("classification") != "PUBLIC_SAFE":
        raise RemediationError(f"receipt {name} is malformed")
    if value.get("digestAlgorithm") != "SHA-256" or value.get("domain") != domain:
        raise RemediationError(f"receipt {name} has the wrong digest domain")
    digest = value.get("digest")
    if not isinstance(digest, str) or len(digest) != 64:
        raise RemediationError(f"receipt {name} has an invalid digest")
    return digest


def validate_receipt(root: Path) -> tuple[str, str]:
    """Validate the public signed receipt and return its projection/schema digest claims."""
    receipt_path = root / "remediation" / "projection-receipt.json"
    public_key = root / "remediation" / "projection-receipt-public.pem"
    receipt = canonical_json(receipt_path)
    if not isinstance(receipt, dict):
        raise RemediationError("projection receipt is not an object")
    required = {
        "approvedAction",
        "classification",
        "keyId",
        "publicKeySha256",
        "receiptId",
        "schema",
        "schemaDigest",
        "signature",
        "signatureAlgorithm",
        "signatureEncoding",
        "signedAt",
        "sourceEnvelopeId",
        "subjectDigest",
        "version",
    }
    if set(receipt) != required or receipt.get("approvedAction") != PUBLIC_ACTION:
        raise RemediationError("projection receipt has an unexpected shape")
    if receipt.get("classification") != "PUBLIC_SAFE" or receipt.get("schema") != RECEIPT_SCHEMA:
        raise RemediationError("projection receipt is not public-safe active-plan evidence")
    fingerprint = public_key_fingerprint(public_key)
    if (
        receipt.get("publicKeySha256") != fingerprint
        or receipt.get("keyId") != f"KEY-ED25519-{fingerprint[:24].upper()}"
    ):
        raise RemediationError("projection receipt key identity does not match its public key")
    if (
        receipt.get("signatureAlgorithm") != "Ed25519"
        or receipt.get("signatureEncoding") != "base64url-no-padding"
    ):
        raise RemediationError("projection receipt uses an unsupported signature encoding")
    signed_at = receipt.get("signedAt")
    if not isinstance(signed_at, str):
        raise RemediationError("projection receipt lacks a signing time")
    try:
        parsed_time = datetime_module.datetime.fromisoformat(signed_at)
    except ValueError as error:
        raise RemediationError("projection receipt signing time is invalid") from error
    if parsed_time.utcoffset() is None:
        raise RemediationError("projection receipt signing time lacks a UTC offset")
    signature = receipt.get("signature")
    if not isinstance(signature, str):
        raise RemediationError("projection receipt lacks a signature")
    verify_signature(
        public_key,
        canonical_bytes({key: value for key, value in receipt.items() if key != "signature"}),
        signature,
    )
    return (
        _digest_claim(receipt, "subjectDigest", PROJECTION_DOMAIN),
        _digest_claim(receipt, "schemaDigest", SCHEMA_DOMAIN),
    )


def _schema_registry(root: Path):
    try:
        from jsonschema import Draft202012Validator, FormatChecker
        from referencing import Registry, Resource
    except ImportError as error:
        raise RemediationError("pinned jsonschema tooling is required") from error
    schemas: dict[str, dict[str, JsonValue]] = {}
    for path in collect_index(root, "remediation/schema/index.json"):
        value = canonical_json(root / path)
        if not isinstance(value, dict) or not isinstance(value.get("$id"), str):
            raise RemediationError(f"public schema lacks $id: {path}")
        schemas[value["$id"]] = value
    registry = Registry().with_resources(
        (identifier, Resource.from_contents(value)) for identifier, value in schemas.items()
    )
    return schemas, registry, Draft202012Validator, FormatChecker


def _validate_records(root: Path) -> dict[str, dict[str, JsonValue]]:
    schemas, registry, validator_type, format_checker = _schema_registry(root)
    plan_root = canonical_json(root / "remediation/plan/index.json")
    if not isinstance(plan_root, dict) or set(plan_root) != {*PLAN_CATEGORIES, "schemaVersion"}:
        raise RemediationError("public plan root has an unexpected shape")
    records: dict[str, dict[str, JsonValue]] = {}
    indexes = [str(plan_root[category]) for category in PLAN_CATEGORIES]
    indexes.append("remediation/contracts/index.json")
    for index_path in indexes:
        for path in collect_index(root, index_path):
            value = canonical_json(root / path)
            if (
                not isinstance(value, dict)
                or not isinstance(value.get("id"), str)
                or not isinstance(value.get("schema"), str)
            ):
                raise RemediationError(f"public record lacks identity/schema: {path}")
            if value["id"] in records:
                raise RemediationError(f"duplicate public record ID: {value['id']}")
            schema = schemas.get(value["schema"])
            if schema is None:
                raise RemediationError(f"public record references an unknown schema: {value['id']}")
            errors = sorted(
                validator_type(
                    schema, registry=registry, format_checker=format_checker()
                ).iter_errors(value),
                key=lambda error: error.json_path,
            )
            if errors:
                raise RemediationError(
                    f"public record violates its schema: {value['id']}: {errors[0].message}"
                )
            if value.get("classification") != "PUBLIC_SAFE":
                raise RemediationError(f"non-public record in public projection: {value['id']}")
            records[value["id"]] = value
    return records


def validate_public_projection(root: Path) -> None:
    """Validate public artifacts, schemas, graph, canonicality, and the signed receipt."""
    if not (root / ".git").exists() or not (root / "settings.gradle.kts").is_file():
        raise RemediationError("production root must be the FinGrind repository")
    root_index = canonical_json(root / "remediation/index.json")
    if not isinstance(root_index, dict) or root_index.get("plan") != "remediation/plan/index.json":
        raise RemediationError("remediation root does not bind the public plan")
    if root_index.get("schema") != "remediation/schema/index.json":
        raise RemediationError("remediation root does not bind the public schema")
    claimed_projection, claimed_schema = validate_receipt(root)
    actual_projection = source_digest(
        PROJECTION_DOMAIN,
        _record_paths(root, "remediation/plan") | _record_paths(root, "remediation/contracts"),
    )
    actual_schema = source_digest(SCHEMA_DOMAIN, _record_paths(root, "remediation/schema"))
    if (claimed_projection, claimed_schema) != (actual_projection, actual_schema):
        raise RemediationError("public output does not reproduce the signed Ledger-1 projection")
    validate_successor_checkpoint(root)
    records = _validate_records(root)
    validate_graph(records)
