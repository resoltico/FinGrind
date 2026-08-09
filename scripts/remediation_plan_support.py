"""Canonical JSON, manifest, digest, and signature primitives for remediation plans."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import unicodedata
from pathlib import Path

type JsonValue = bool | int | str | list[JsonValue] | dict[str, JsonValue] | None

SCALAR_SET_FIELDS = frozenset(
    {
        "blocks",
        "children",
        "dependsOn",
        "evidenceRefs",
        "fieldsAdded",
        "fieldsChanged",
        "fieldsRemoved",
        "leaves",
        "paths",
        "removed",
        "added",
        "requiredForMilestones",
    }
)


class RemediationError(RuntimeError):
    """Raised when a remediation artifact violates its public contract."""


def strict_json(path: Path) -> JsonValue:
    """Read one JSON value while rejecting duplicate object keys."""

    def unique_pairs(pairs: list[tuple[str, JsonValue]]) -> dict[str, JsonValue]:
        value: dict[str, JsonValue] = {}
        for key, item in pairs:
            if key in value:
                raise RemediationError(f"duplicate JSON key: {path}")
            value[key] = item
        return value

    try:
        return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_pairs)
    except (OSError, json.JSONDecodeError) as error:
        raise RemediationError(f"invalid JSON: {path}") from error


def _normalized(value: JsonValue, field: str | None = None) -> JsonValue:
    if isinstance(value, dict):
        return {key: _normalized(value[key], key) for key in sorted(value, key=str.encode)}
    if isinstance(value, list):
        items = [_normalized(item) for item in value]
        return sorted(items, key=str) if field in SCALAR_SET_FIELDS else items
    if isinstance(value, float):
        raise RemediationError("floating-point JSON values are forbidden")
    if isinstance(value, str):
        if unicodedata.normalize("NFC", value) != value:
            raise RemediationError("non-NFC JSON string")
        if any(0xD800 <= ord(character) <= 0xDFFF for character in value):
            raise RemediationError("JSON string contains a lone surrogate")
    return value


def _escaped(value: JsonValue) -> str:
    text = json.dumps(value, ensure_ascii=False, separators=(",", ": "), sort_keys=False)
    for short, escaped in (
        (r"\b", r"\u0008"),
        (r"\t", r"\u0009"),
        (r"\n", r"\u000A"),
        (r"\f", r"\u000C"),
        (r"\r", r"\u000D"),
    ):
        text = text.replace(short, escaped)
    return re.sub(r"\\u([0-9a-fA-F]{4})", lambda match: "\\u" + match.group(1).upper(), text)


def _scalar(value: JsonValue) -> bool:
    return value is None or isinstance(value, bool | int | str)


def _render(value: JsonValue, depth: int, root: bool = False) -> list[str]:
    indent = " " * (depth * 2)
    child_indent = " " * ((depth + 1) * 2)
    if _scalar(value):
        return [_escaped(value)]
    if isinstance(value, list):
        if not value:
            return ["[]"]
        if all(_scalar(item) for item in value):
            return ["[" + ", ".join(_escaped(item) for item in value) + "]"]
        lines = ["["]
        for index, item in enumerate(value):
            rendered = _render(item, depth + 1)
            rendered[0] = child_indent + rendered[0]
            if index + 1 < len(value):
                rendered[-1] += ","
            lines.extend(rendered)
        lines.append(indent + "]")
        return lines
    if not value:
        return ["{}"]
    if not root and all(_scalar(item) for item in value.values()):
        return [
            "{"
            + ", ".join(f"{_escaped(key)}: {_escaped(item)}" for key, item in value.items())
            + "}"
        ]
    lines = ["{"]
    for index, (key, item) in enumerate(value.items()):
        rendered = _render(item, depth + 1)
        rendered[0] = child_indent + _escaped(key) + ": " + rendered[0]
        if index + 1 < len(value):
            rendered[-1] += ","
        lines.extend(rendered)
    lines.append(indent + "}")
    return lines


def canonical_bytes(value: JsonValue) -> bytes:
    """Encode a JSON value using the Ledger-1 canonical byte representation."""
    return ("\n".join(_render(_normalized(value), 0, root=True)) + "\n").encode("utf-8")


def canonical_json(path: Path) -> JsonValue:
    """Read a canonical JSON file, rejecting semantically equal but noncanonical bytes."""
    value = strict_json(path)
    if path.read_bytes() != canonical_bytes(value):
        raise RemediationError(f"noncanonical JSON bytes: {path}")
    return value


def write_json(path: Path, value: JsonValue) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(canonical_bytes(value))
    path.chmod(0o644)


def relative_path(value: str) -> str:
    """Require a project-relative portable path without traversal."""
    candidate = Path(value)
    if not value or candidate.is_absolute() or ".." in candidate.parts:
        raise RemediationError(f"unsafe relative path: {value!r}")
    return candidate.as_posix()


def collect_index(root: Path, index_path: str) -> list[str]:
    """Collect manifest leaves while proving each index is acyclic and internally counted."""
    visited: set[str] = set()

    def walk(current: str) -> tuple[list[str], int]:
        path = relative_path(current)
        if path in visited:
            raise RemediationError(f"manifest index cycle: {path}")
        visited.add(path)
        value = canonical_json(root / path)
        if not isinstance(value, dict) or value.get("kind") != "INDEX":
            raise RemediationError(f"invalid manifest index: {path}")
        children = value.get("children")
        leaves = value.get("leaves")
        count = value.get("recordCount")
        if (
            not isinstance(children, list)
            or not isinstance(leaves, list)
            or not isinstance(count, int)
        ):
            raise RemediationError(f"malformed manifest index: {path}")
        child_paths = [relative_path(item) for item in children if isinstance(item, str)]
        leaf_paths = [relative_path(item) for item in leaves if isinstance(item, str)]
        if len(child_paths) != len(children) or len(leaf_paths) != len(leaves):
            raise RemediationError(f"non-string manifest pointer: {path}")
        nested: list[str] = []
        total = len(leaf_paths)
        for child in child_paths:
            child_leaves, child_count = walk(child)
            nested.extend(child_leaves)
            total += child_count
        all_leaves = [*leaf_paths, *nested]
        if len(all_leaves) != len(set(all_leaves)) or count != total:
            raise RemediationError(f"manifest index count mismatch: {path}")
        return all_leaves, total

    leaves, _ = walk(index_path)
    return leaves


def manifest_index(leaves: list[str]) -> dict[str, JsonValue]:
    """Build the unsharded manifest shape used by the bounded P0 public projection."""
    return {
        "children": [],
        "kind": "INDEX",
        "leaves": sorted(leaves),
        "prefix": "",
        "recordCount": len(leaves),
        "schema": "urn:fingrind:ledger1:manifest-index",
    }


def source_digest(domain: str, records: dict[str, bytes]) -> str:
    """Calculate the Ledger-1 digest for an exact mapping of source paths to bytes."""
    payload = domain.encode("utf-8") + b"\0" + len(records).to_bytes(4, "big")
    encoded: list[tuple[bytes, bytes]] = []
    for path, content in records.items():
        path_bytes = path.encode("utf-8")
        record = len(path_bytes).to_bytes(4, "big") + path_bytes + hashlib.sha256(content).digest()
        encoded.append((path_bytes, record))
    return hashlib.sha256(payload + b"".join(record for _, record in sorted(encoded))).hexdigest()


def remap(value: JsonValue, old_prefix: str, new_prefix: str) -> JsonValue:
    """Rebase only explicit ledger path strings, preserving future-output path declarations."""
    if isinstance(value, dict):
        return {key: remap(item, old_prefix, new_prefix) for key, item in value.items()}
    if isinstance(value, list):
        return [remap(item, old_prefix, new_prefix) for item in value]
    if isinstance(value, str) and value.startswith(old_prefix):
        return new_prefix + value.removeprefix(old_prefix)
    return value


def openssl_executable() -> str:
    """Resolve an Ed25519-capable OpenSSL command for public receipt verification."""
    configured = os.environ.get("FINGRIND_OPENSSL_EXECUTABLE")
    if configured:
        candidate = Path(configured)
        if not candidate.is_file() or not os.access(candidate, os.X_OK):
            raise RemediationError("configured OpenSSL executable is unavailable")
        return str(candidate)
    homebrew = Path("/opt/homebrew/bin/openssl")
    if homebrew.is_file() and os.access(homebrew, os.X_OK):
        return str(homebrew)
    candidate = shutil.which("openssl")
    if candidate:
        return candidate
    raise RemediationError("OpenSSL is required for public receipt verification")


def public_key_fingerprint(public_key: Path) -> str:
    """Return the SHA-256 SPKI fingerprint for an OpenSSL-readable public key."""
    result = subprocess.run(
        [
            openssl_executable(),
            "pkey",
            "-pubin",
            "-in",
            str(public_key),
            "-pubout",
            "-outform",
            "DER",
        ],
        check=False,
        capture_output=True,
    )
    if result.returncode:
        raise RemediationError(f"cannot read public key: {public_key}")
    return hashlib.sha256(result.stdout).hexdigest()


def verify_signature(public_key: Path, payload: bytes, encoded_signature: str) -> None:
    """Verify a base64url Ed25519 signature without exposing it to a shell."""
    padding = "=" * ((4 - len(encoded_signature) % 4) % 4)
    try:
        signature = base64.urlsafe_b64decode(encoded_signature + padding)
    except ValueError as error:
        raise RemediationError("receipt signature is not base64url") from error
    with tempfile.TemporaryDirectory() as temporary:
        temporary_root = Path(temporary)
        payload_path = temporary_root / "payload.bin"
        signature_path = temporary_root / "signature.bin"
        payload_path.write_bytes(payload)
        signature_path.write_bytes(signature)
        result = subprocess.run(
            [
                openssl_executable(),
                "pkeyutl",
                "-verify",
                "-rawin",
                "-pubin",
                "-inkey",
                str(public_key),
                "-in",
                str(payload_path),
                "-sigfile",
                str(signature_path),
            ],
            check=False,
            capture_output=True,
        )
    if result.returncode:
        raise RemediationError("receipt signature does not verify")
