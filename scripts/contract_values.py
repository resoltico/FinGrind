"""Helpers for reading canonical FinGrind contract values from repo-owned resources."""

from __future__ import annotations

import json
import platform
import re
from pathlib import Path


def repository_root(script_path: str | Path) -> Path:
    return Path(script_path).resolve().parent.parent


def load_contract_values(
    repo_root: Path,
    *,
    os_name: str | None = None,
    architecture: str | None = None,
) -> dict[str, object]:
    schema_keys = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/contract-schema-keys.json"
    )
    runtime_surface_schema = required_object(schema_keys, "runtimeSurface")
    public_distribution_schema = required_object(schema_keys, "publicDistribution")
    managed_sqlite_schema = required_object(schema_keys, "managedSqlite")
    bundle_layout_schema = required_object(schema_keys, "bundleLayout")
    operation_id_schema = required_object(schema_keys, "operationIdContract")

    runtime_surface_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-surface-contract.json"
    )
    public_distribution_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/public-distribution-contract.json"
    )
    managed_sqlite_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json"
    )
    bundle_layout_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json"
    )
    operation_ids_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/operation-id-contract.json"
    )

    bundle_layout_targets = load_bundle_layout_targets(bundle_layout_document, bundle_layout_schema)
    public_distribution = load_public_distribution(
        public_distribution_document,
        public_distribution_schema,
        declared_bundle_targets=set(bundle_layout_targets),
    )
    host_bundle_target = load_host_bundle_target(
        bundle_layout_targets,
        os_name=os_name or platform.system(),
        architecture=architecture or platform.machine(),
    )
    return {
        "runtimeSurface": {
            "directJavaRuntimeDistribution": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "directJavaRuntimeDistribution"),
            ),
            "sourceCheckoutRuntimeDistribution": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "sourceCheckoutRuntimeDistribution"),
            ),
            "containerRuntimeDistribution": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "containerRuntimeDistribution"),
            ),
            "bundleRuntimeDistribution": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "bundleRuntimeDistribution"),
            ),
            "publicCliDistribution": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "publicCliDistribution"),
            ),
            "storageDriver": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "storageDriver"),
            ),
            "storageEngine": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "storageEngine"),
            ),
            "bookProtectionMode": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "bookProtectionMode"),
            ),
            "defaultBookCipher": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "defaultBookCipher"),
            ),
            "sqliteLibraryMode": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "sqliteLibraryMode"),
            ),
            "sqliteLibraryEnvironmentVariable": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "sqliteLibraryEnvironmentVariable"),
            ),
            "sqliteBundleHomeSystemProperty": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "sqliteBundleHomeSystemProperty"),
            ),
        },
        "publicDistribution": public_distribution,
        "managedSqlite": {
            "requiredMinimumSqliteVersion": required_value(
                managed_sqlite_document,
                required_string(managed_sqlite_schema, "requiredMinimumSqliteVersion"),
            ),
            "requiredSqlite3mcVersion": required_value(
                managed_sqlite_document,
                required_string(managed_sqlite_schema, "requiredSqlite3mcVersion"),
            ),
        },
        "bundleLayout": {
            "targets": bundle_layout_targets,
            "hostBundleTarget": host_bundle_target,
        },
        "operationIds": load_operation_ids(operation_ids_document, operation_id_schema),
    }


def read_json(path: Path) -> dict[str, object]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"expected top-level JSON object in {path}")
    return data


def required_value(document: dict[str, object], key: str) -> str:
    value = document.get(key)
    normalized = value.strip() if isinstance(value, str) else ""
    if not normalized:
        raise ValueError(f"missing required contract property: {key}")
    return normalized


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


def load_operation_ids(
    document: dict[str, object], schema: dict[str, object]
) -> dict[str, str]:
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
        if not normalized_enum_name or not re.fullmatch(
            r"[A-Z][A-Z0-9_]*", normalized_enum_name
        ):
            raise ValueError(
                "operation-id schema values must be non-blank upper-snake enum names"
            )
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
            raise ValueError(
                "operation-id contract keys must be non-blank upper-snake enum names"
            )
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
            "sqliteLibraryFileName": required_value(
                raw_target, sqlite_library_file_name_key
            ),
        }
        recomposed_classifier = (
            target["operatingSystemId"] + "-" + target["architectureId"]
        )
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


def load_host_bundle_target(
    bundle_layout_targets: dict[str, dict[str, str]],
    *,
    os_name: str,
    architecture: str,
) -> dict[str, str]:
    classifier = operating_system_id(os_name) + "-" + architecture_id(architecture)
    try:
        target = bundle_layout_targets[classifier]
    except KeyError as exc:
        raise ValueError(f"host bundle target is not declared in the bundle layout contract: {classifier}") from exc
    return {"classifier": classifier, **target}


def operating_system_id(os_name: str) -> str:
    normalized = os_name.lower()
    if "mac" in normalized or "darwin" in normalized:
        return "macos"
    if "linux" in normalized:
        return "linux"
    if "windows" in normalized:
        return "windows"
    raise ValueError(
        f"FinGrind bundles currently support macOS, Linux, and Windows only: {os_name}"
    )


def architecture_id(architecture: str) -> str:
    normalized = architecture.lower()
    if normalized in {"arm64", "aarch64"}:
        return "aarch64"
    if normalized in {"amd64", "x86_64", "x64"}:
        return "x86_64"
    return re.sub(r"[^a-z0-9]+", "-", normalized).strip("-")
