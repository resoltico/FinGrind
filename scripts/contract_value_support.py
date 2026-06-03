"""Shared helpers for loading and validating repo-owned contract JSON surfaces."""

from __future__ import annotations

import json
import re
from pathlib import Path


def read_json(path: Path) -> dict[str, object]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"expected top-level JSON object in {path}")
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
        raise ValueError(f"missing required boolean contract property: {key}")
    return value


def required_int(document: dict[str, object], key: str) -> int:
    value = document.get(key)
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(f"missing required integer contract property: {key}")
    return value


def required_object(document: dict[str, object], key: str) -> dict[str, object]:
    value = document.get(key)
    if not isinstance(value, dict):
        raise ValueError(f"missing required contract object: {key}")
    return value


def required_string(document: dict[str, object], key: str) -> str:
    value = document.get(key)
    normalized = value.strip() if isinstance(value, str) else ""
    if not normalized:
        raise ValueError(f"missing required contract property: {key}")
    return normalized


def string_array(document: dict[str, object], key: str) -> list[str]:
    value = document.get(key, [])
    if value is None:
        return []
    if not isinstance(value, list):
        raise ValueError(f"contract property {key} must be one JSON array of strings")
    normalized: list[str] = []
    seen: set[str] = set()
    for element in value:
        if not isinstance(element, str):
            raise ValueError(f"contract property {key} must be one JSON array of strings")
        item = element.strip()
        if not item:
            raise ValueError(f"contract property {key} must be one JSON array of strings")
        if item in seen:
            raise ValueError(f"contract property {key} must not contain duplicates: {item}")
        seen.add(item)
        normalized.append(item)
    return normalized


def load_operation_ids(document: dict[str, object], schema: dict[str, object]) -> dict[str, str]:
    operation_ids: dict[str, str] = {}
    declared_enum_names: set[str] = set()
    for semantic_key, raw_enum_name in schema.items():
        normalized_semantic_key = semantic_key.strip() if isinstance(semantic_key, str) else ""
        if not normalized_semantic_key or not re.fullmatch(
            r"[a-z][a-zA-Z0-9]*", normalized_semantic_key
        ):
            raise ValueError(
                "operation-id schema keys must be non-blank lower-camel semantic names"
            )
        normalized_enum_name = raw_enum_name.strip() if isinstance(raw_enum_name, str) else ""
        if not normalized_enum_name or not re.fullmatch(r"[A-Z][A-Z0-9_]*", normalized_enum_name):
            raise ValueError("operation-id schema values must be non-blank upper-snake enum names")
        if normalized_semantic_key in operation_ids:
            raise ValueError(
                f"operation-id contract semantic key must be unique: {normalized_semantic_key}"
            )
        if normalized_enum_name in declared_enum_names:
            raise ValueError(
                f"operation-id schema enum mapping must be unique: {normalized_enum_name}"
            )
        declared_enum_names.add(normalized_enum_name)
        operation_ids[normalized_semantic_key] = required_value(document, normalized_enum_name)

    for enum_name, wire_name in document.items():
        normalized_enum_name = enum_name.strip() if isinstance(enum_name, str) else ""
        if not normalized_enum_name or not re.fullmatch(r"[A-Z][A-Z0-9_]*", normalized_enum_name):
            raise ValueError("operation-id contract keys must be non-blank upper-snake enum names")
        if normalized_enum_name not in declared_enum_names:
            raise ValueError(
                f"operation-id contract declared an enum without one canonical semantic key: {normalized_enum_name}"
            )
        normalized_wire_name = wire_name.strip() if isinstance(wire_name, str) else ""
        if not normalized_wire_name:
            raise ValueError(
                f"operation-id contract value must be one non-blank string: {normalized_enum_name}"
            )
    return operation_ids


def load_bundle_layout_targets(
    document: dict[str, object], schema: dict[str, object]
) -> dict[str, dict[str, str]]:
    bundle_targets_key = required_string(schema, "bundleTargets")
    bundle_targets_object = required_object(document, bundle_targets_key)
    operating_system_id_key = required_string(schema, "operatingSystemId")
    architecture_id_key = required_string(schema, "architectureId")
    archive_format_key = required_string(schema, "archiveFormat")
    launcher_path_key = required_string(schema, "launcherPath")
    launcher_command_key = required_string(schema, "launcherCommand")
    sqlite_library_file_name_key = required_string(schema, "sqliteLibraryFileName")
    targets: dict[str, dict[str, str]] = {}
    for classifier, raw_target in bundle_targets_object.items():
        normalized_classifier = classifier.strip()
        if not normalized_classifier:
            raise ValueError("bundle layout target names must be non-blank")
        if not isinstance(raw_target, dict):
            raise ValueError(f"bundle layout target {normalized_classifier} must be one object")
        target = {
            "operatingSystemId": required_value(raw_target, operating_system_id_key),
            "architectureId": required_value(raw_target, architecture_id_key),
            "archiveFormat": required_value(raw_target, archive_format_key),
            "launcherPath": required_value(raw_target, launcher_path_key),
            "launcherCommand": required_value(raw_target, launcher_command_key),
            "sqliteLibraryFileName": required_value(raw_target, sqlite_library_file_name_key),
        }
        recomposed_classifier = target["operatingSystemId"] + "-" + target["architectureId"]
        if normalized_classifier != recomposed_classifier:
            raise ValueError(
                f"bundle layout target {normalized_classifier} must agree with {recomposed_classifier}"
            )
        targets[normalized_classifier] = target
    if not targets:
        raise ValueError("bundle layout contract must declare at least one bundle target")
    return targets


def load_public_distribution(
    document: dict[str, object],
    schema: dict[str, object],
    *,
    declared_bundle_targets: set[str],
) -> dict[str, list[str]]:
    supported_key = required_string(schema, "supportedPublicCliBundleTargets")
    unsupported_key = required_string(schema, "unsupportedPublicCliBundleTargets")
    supported_targets = string_array(document, supported_key)
    unsupported_targets = string_array(document, unsupported_key)
    overlap = set(supported_targets).intersection(unsupported_targets)
    if overlap:
        duplicated_target = next(iter(sorted(overlap)))
        raise ValueError(
            f"{supported_key} and {unsupported_key} must be disjoint: {duplicated_target}"
        )
    declared_targets = set(supported_targets).union(unsupported_targets)
    for value in declared_targets:
        if value not in declared_bundle_targets:
            raise ValueError(
                f"public distribution contract references undeclared bundle target: {value}"
            )
    if declared_targets != declared_bundle_targets:
        missing_targets = sorted(declared_bundle_targets - declared_targets)
        raise ValueError(
            "public distribution contract must classify every declared bundle target: "
            + ", ".join(missing_targets)
        )
    return {
        "supportedPublicCliBundleTargets": supported_targets,
        "unsupportedPublicCliBundleTargets": unsupported_targets,
    }
