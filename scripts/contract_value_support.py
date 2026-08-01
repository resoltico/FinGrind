"""Shared helpers for loading and validating repo-owned contract JSON surfaces."""

from __future__ import annotations

import json
import re
from pathlib import Path


def read_json(path: Path) -> dict[str, object]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise TypeError(f"expected top-level JSON object in {path}")
    return data


def load_runtime_environment_document(repo_root: Path) -> dict[str, object]:
    generated_contract = (
        repo_root
        / "contract/build/generated-resources/protocol/dev/erst/fingrind/contract/protocol/runtime-environment-contract.json"
    )
    if generated_contract.is_file():
        return read_json(generated_contract)

    java_version = load_required_build_property(
        repo_root / "gradle/fingrind-build.properties", "fingrindJavaVersion"
    )
    if not re.fullmatch(r"[1-9][0-9]*", java_version):
        raise ValueError(
            "gradle/fingrind-build.properties must declare fingrindJavaVersion as one positive integer"
        )
    return {"sourceCheckoutJava": java_version + "+"}


def load_required_build_property(path: Path, key: str) -> str:
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if "=" not in line:
            continue
        candidate_key, candidate_value = line.split("=", 1)
        if candidate_key.strip() != key:
            continue
        normalized_value = candidate_value.strip()
        if normalized_value:
            return normalized_value
        break
    raise ValueError(f"missing required build property {key} in {path}")


def required_value(document: dict[str, object], key: str) -> str:
    value = document.get(key)
    normalized = value.strip() if isinstance(value, str) else ""
    if not normalized:
        raise ValueError(f"missing required contract property: {key}")
    return normalized


def required_bool(document: dict[str, object], key: str) -> bool:
    value = document.get(key)
    if not isinstance(value, bool):
        raise TypeError(f"missing required boolean contract property: {key}")
    return value


def required_int(document: dict[str, object], key: str) -> int:
    value = document.get(key)
    if isinstance(value, bool) or not isinstance(value, int):
        raise TypeError(f"missing required integer contract property: {key}")
    return value


def required_object(document: dict[str, object], key: str) -> dict[str, object]:
    value = document.get(key)
    if not isinstance(value, dict):
        raise TypeError(f"missing required contract object: {key}")
    return value


def required_string(document: dict[str, object], key: str) -> str:
    value = document.get(key)
    normalized = value.strip() if isinstance(value, str) else ""
    if not normalized:
        raise ValueError(f"missing required contract property: {key}")
    return normalized


def require_only_properties(
    document: dict[str, object], allowed_keys: tuple[str, ...], object_label: str
) -> None:
    unexpected_keys = sorted(set(document).difference(allowed_keys))
    if unexpected_keys:
        raise ValueError(
            f"{object_label} must not declare unrecognized properties: "
            + ", ".join(unexpected_keys)
        )


def string_array(document: dict[str, object], key: str) -> list[str]:
    value = document.get(key, [])
    if value is None:
        return []
    if not isinstance(value, list):
        raise TypeError(f"contract property {key} must be one JSON array of strings")
    normalized: list[str] = []
    seen: set[str] = set()
    for element in value:
        if not isinstance(element, str):
            raise TypeError(f"contract property {key} must be one JSON array of strings")
        item = element.strip()
        if not item:
            raise ValueError(f"contract property {key} must be one JSON array of strings")
        if item in seen:
            raise ValueError(f"contract property {key} must not contain duplicates: {item}")
        seen.add(item)
        normalized.append(item)
    return normalized


def load_operation_ids(document: dict[str, object]) -> dict[str, str]:
    operation_ids: dict[str, str] = {}
    for enum_name, wire_name in document.items():
        normalized_enum_name = enum_name.strip() if isinstance(enum_name, str) else ""
        if not normalized_enum_name or not re.fullmatch(r"[A-Z][A-Z0-9_]*", normalized_enum_name):
            raise ValueError("operation-id contract keys must be non-blank upper-snake enum names")
        normalized_semantic_key = operation_id_semantic_key(normalized_enum_name)
        if normalized_semantic_key in operation_ids:
            raise ValueError(
                f"operation-id contract semantic key must be unique: {normalized_semantic_key}"
            )
        normalized_wire_name = wire_name.strip() if isinstance(wire_name, str) else ""
        if not normalized_wire_name:
            raise ValueError(
                f"operation-id contract value must be one non-blank string: {normalized_enum_name}"
            )
        operation_ids[normalized_semantic_key] = normalized_wire_name
    return operation_ids


def operation_id_semantic_key(enum_name: str) -> str:
    parts = enum_name.split("_")
    if not parts or any(not part for part in parts):
        raise ValueError(
            f"operation-id contract key must be one non-blank upper-snake enum name: {enum_name}"
        )
    lower_head = parts[0].lower()
    tail = "".join(part.lower().capitalize() for part in parts[1:])
    semantic_key = lower_head + tail
    if not re.fullmatch(r"[a-z][a-zA-Z0-9]*", semantic_key):
        raise ValueError(
            "operation-id contract semantic key must resolve to one non-blank lower-camel name: "
            f"{enum_name}"
        )
    return semantic_key
