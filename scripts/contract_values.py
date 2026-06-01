"""Helpers for reading canonical FinGrind contract values from repo-owned resources."""

from __future__ import annotations

import platform
from pathlib import Path

from contract_platform import load_host_bundle_target
from contract_value_support import (
    load_bundle_layout_targets,
    load_operation_ids,
    load_public_distribution,
    load_release_publication,
    load_runtime_environment_document,
    read_json,
    required_bool,
    required_int,
    required_object,
    required_string,
    required_value,
    string_array,
)


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
    protected_book_format_schema = required_object(schema_keys, "protectedBookFormat")
    public_distribution_schema = required_object(schema_keys, "publicDistribution")
    managed_sqlite_schema = required_object(schema_keys, "managedSqlite")
    runtime_environment_schema = required_object(schema_keys, "runtimeEnvironment")
    bundle_layout_schema = required_object(schema_keys, "bundleLayout")
    release_publication_schema = required_object(schema_keys, "releasePublication")
    operation_id_schema = required_object(schema_keys, "operationIdContract")

    runtime_surface_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-surface-contract.json"
    )
    protected_book_format_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/protected-book-format-contract.json"
    )
    public_distribution_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/public-distribution-contract.json"
    )
    managed_sqlite_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json"
    )
    runtime_environment_document = load_runtime_environment_document(repo_root)
    bundle_layout_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json"
    )
    release_publication_document = read_json(
        repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/release-publication-contract.json"
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
    release_publication = load_release_publication(
        release_publication_document,
        release_publication_schema,
        supported_bundle_targets=public_distribution["supportedPublicCliBundleTargets"],
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
            "sqliteBundleHomeSystemProperty": required_value(
                runtime_surface_document,
                required_string(runtime_surface_schema, "sqliteBundleHomeSystemProperty"),
            ),
        },
        "protectedBookFormat": {
            "cipher": required_value(
                protected_book_format_document,
                required_string(protected_book_format_schema, "cipher"),
            ),
            "legacyMode": required_bool(
                protected_book_format_document,
                required_string(protected_book_format_schema, "legacyMode"),
            ),
            "pageSize": required_int(
                protected_book_format_document,
                required_string(protected_book_format_schema, "pageSize"),
            ),
            "reservedBytes": required_int(
                protected_book_format_document,
                required_string(protected_book_format_schema, "reservedBytes"),
            ),
            "legacyPageSize": required_int(
                protected_book_format_document,
                required_string(protected_book_format_schema, "legacyPageSize"),
            ),
            "kdfIter": required_int(
                protected_book_format_document,
                required_string(protected_book_format_schema, "kdfIter"),
            ),
            "plaintextHeaderSize": required_int(
                protected_book_format_document,
                required_string(protected_book_format_schema, "plaintextHeaderSize"),
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
            "requiredSqliteSourceId": required_value(
                managed_sqlite_document,
                required_string(managed_sqlite_schema, "requiredSqliteSourceId"),
            ),
            "requiredCompileOptions": string_array(
                managed_sqlite_document,
                required_string(managed_sqlite_schema, "requiredCompileOptions"),
            ),
            "forbiddenCompileOptions": string_array(
                managed_sqlite_document,
                required_string(managed_sqlite_schema, "forbiddenCompileOptions"),
            ),
            "requiresSecureMemorySupport": required_bool(
                managed_sqlite_document,
                required_string(managed_sqlite_schema, "requiresSecureMemorySupport"),
            ),
        },
        "runtimeEnvironment": {
            "sourceCheckoutJava": required_value(
                runtime_environment_document,
                required_string(runtime_environment_schema, "sourceCheckoutJava"),
            ),
        },
        "bundleLayout": {
            "targets": bundle_layout_targets,
            "hostBundleTarget": host_bundle_target,
        },
        "releasePublication": release_publication,
        "operationIds": load_operation_ids(operation_ids_document, operation_id_schema),
    }
