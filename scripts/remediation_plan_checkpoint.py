"""Validation for the signed append-only Ledger-1 P0 closure checkpoint."""

from __future__ import annotations

import datetime as datetime_module
import hashlib
from pathlib import Path

from remediation_plan_support import (
    RemediationError,
    canonical_bytes,
    canonical_json,
    public_key_fingerprint,
    verify_signature,
)

SUCCESSOR_ACTION = "AUTHORIZE_P0_SUCCESSOR_CHECKPOINT_V1"
SUCCESSOR_CHECKPOINT_DOMAIN = "fingrind-remediation-p0-successor-checkpoint:v1"
SUCCESSOR_CHECKPOINT_PATH = "remediation/checkpoints/P0-CLOSURE-V1.json"
SUCCESSOR_RECEIPT_PATH = "remediation/checkpoints/P0-CLOSURE-V1.receipt.json"
SUCCESSOR_RECEIPT_SCHEMA = "urn:fingrind:remediation:p0-successor-publication-receipt:v1"
SUCCESSOR_PROJECTION_ID = "PUBLIC-SAFE-P0-SUCCESSOR-PROJECTION-V1"
SUCCESSOR_RECEIPT_ID = "P0-SUCCESSOR-PUBLICATION-RECEIPT-V1"
SUCCESSOR_SOURCE_ENVELOPE_ID = "APPROVAL-P0-SUCCESSOR-PUBLICATION-V1"
P0_PUBLIC_MERGE = {
    "baseCommitOid": "292e1e6c43b3a5b3baa5ab9a5fc7d7325f9230c1",
    "candidateCommitOid": "236ea8c4725fe40ca3131adbde4f6885d408401d",
    "mergeCommitOid": "15456003f412f439247ec78b19cd6a092b56cde7",
    "mergeTreeOid": "05ff2969743315c991f7ad6070f48ffa50eff52c",
}


def _expected_checkpoint(sealed_receipt: dict[str, object]) -> dict[str, object]:
    return {
        "classification": "PUBLIC_SAFE",
        "id": SUCCESSOR_PROJECTION_ID,
        "kind": "P0_STATUS_SUMMARY",
        "p0": {
            "designStatus": "COMPLETE",
            "implementationStatus": "MERGED",
            "postMergeVerificationStatus": "PASSED",
            "preMergeVerificationStatus": "PASSED",
            "publicMerge": P0_PUBLIC_MERGE,
            "statusHistoryDisposition": "OMITTED_RESTRICTED_RECONCILIATION",
        },
        "schema": "urn:fingrind:ledger1:successor-public-safe-projection:v1",
        "sealedReference": {
            "publicDigestSubjects": [
                sealed_receipt.get("subjectDigest"),
                sealed_receipt.get("schemaDigest"),
            ],
            "version": "10.8.2",
        },
        "version": "1.0.0",
    }


def _validate_receipt_identity(receipt: dict[str, object]) -> None:
    expected_fields = {
        "approvedAction",
        "classification",
        "keyId",
        "publicKeySha256",
        "receiptId",
        "schema",
        "signature",
        "signatureAlgorithm",
        "signatureEncoding",
        "signedAt",
        "sourceEnvelopeId",
        "subjectDigest",
        "targetPath",
        "version",
    }
    if set(receipt) != expected_fields:
        raise RemediationError("P0 successor checkpoint receipt has an unexpected shape")
    if (
        receipt.get("approvedAction") != SUCCESSOR_ACTION
        or receipt.get("classification") != "PUBLIC_SAFE"
        or receipt.get("receiptId") != SUCCESSOR_RECEIPT_ID
        or receipt.get("schema") != SUCCESSOR_RECEIPT_SCHEMA
        or receipt.get("sourceEnvelopeId") != SUCCESSOR_SOURCE_ENVELOPE_ID
        or receipt.get("targetPath") != SUCCESSOR_CHECKPOINT_PATH
        or receipt.get("version") != "1.0.0"
    ):
        raise RemediationError("P0 successor checkpoint receipt identity drifted")


def _validate_receipt_time(receipt: dict[str, object]) -> None:
    signed_at = receipt.get("signedAt")
    if not isinstance(signed_at, str):
        raise RemediationError("P0 successor checkpoint receipt lacks a signing time")
    try:
        parsed_time = datetime_module.datetime.fromisoformat(signed_at)
    except ValueError as error:
        raise RemediationError("P0 successor checkpoint receipt signing time is invalid") from error
    if parsed_time.utcoffset() is None:
        raise RemediationError("P0 successor checkpoint receipt signing time lacks a UTC offset")


def validate_successor_checkpoint(root: Path) -> None:
    """Verify the append-only signed P0 closure summary without altering the sealed plan."""
    checkpoint_root = root / "remediation" / "checkpoints"
    expected_paths = {
        root / SUCCESSOR_CHECKPOINT_PATH,
        root / SUCCESSOR_RECEIPT_PATH,
    }
    actual_paths = {path for path in checkpoint_root.rglob("*") if path.is_file()}
    if actual_paths != expected_paths or any(
        path.is_symlink() for path in checkpoint_root.rglob("*")
    ):
        raise RemediationError("P0 successor checkpoint inventory is not exact")
    checkpoint = canonical_json(root / SUCCESSOR_CHECKPOINT_PATH)
    receipt = canonical_json(root / SUCCESSOR_RECEIPT_PATH)
    sealed_receipt = canonical_json(root / "remediation" / "projection-receipt.json")
    if not isinstance(checkpoint, dict) or not isinstance(receipt, dict):
        raise RemediationError("P0 successor checkpoint pair is not composed of objects")
    if not isinstance(sealed_receipt, dict):
        raise RemediationError("sealed public projection receipt is not an object")
    if checkpoint != _expected_checkpoint(sealed_receipt):
        raise RemediationError("P0 successor checkpoint does not contain the approved final state")
    _validate_receipt_identity(receipt)
    public_key = root / "remediation" / "projection-receipt-public.pem"
    fingerprint = public_key_fingerprint(public_key)
    if (
        receipt.get("publicKeySha256") != fingerprint
        or receipt.get("keyId") != f"KEY-ED25519-{fingerprint[:24].upper()}"
        or receipt.get("signatureAlgorithm") != "Ed25519"
        or receipt.get("signatureEncoding") != "base64url-no-padding"
    ):
        raise RemediationError("P0 successor checkpoint receipt key identity is invalid")
    checkpoint_digest = hashlib.sha256(canonical_bytes(checkpoint)).hexdigest()
    if receipt.get("subjectDigest") != {
        "classification": "PUBLIC_SAFE",
        "digest": checkpoint_digest,
        "digestAlgorithm": "SHA-256",
        "digestName": "p0SuccessorCheckpointDigest",
        "domain": SUCCESSOR_CHECKPOINT_DOMAIN,
    }:
        raise RemediationError("P0 successor checkpoint receipt does not bind the exact checkpoint")
    _validate_receipt_time(receipt)
    signature = receipt.get("signature")
    if not isinstance(signature, str):
        raise RemediationError("P0 successor checkpoint receipt lacks a signature")
    verify_signature(
        public_key,
        canonical_bytes({key: value for key, value in receipt.items() if key != "signature"}),
        signature,
    )
