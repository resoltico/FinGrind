"""Load schema sections and contract documents from repo-owned resources."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from contract_value_support import (
    load_runtime_environment_document,
    read_json,
    required_object,
)


@dataclass(frozen=True)
class ContractSchemaSections:
    runtime_surface: dict[str, object]
    protected_book_format: dict[str, object]
    managed_sqlite: dict[str, object]
    runtime_environment: dict[str, object]
    bundle_layout: dict[str, object]
    bundle_publication: dict[str, object]
    release_publication: dict[str, object]
    operation_id: dict[str, object]


@dataclass(frozen=True)
class ContractDocuments:
    runtime_surface: dict[str, object]
    protected_book_format: dict[str, object]
    managed_sqlite: dict[str, object]
    runtime_environment: dict[str, object]
    bundle_layout: dict[str, object]
    bundle_publication: dict[str, object]
    release_publication: dict[str, object]
    operation_ids: dict[str, object]


def load_contract_schema_sections(repo_root: Path) -> ContractSchemaSections:
    schema_keys = read_json(_protocol_resource(repo_root, "contract-schema-keys.json"))
    return ContractSchemaSections(
        runtime_surface=required_object(schema_keys, "runtimeSurface"),
        protected_book_format=required_object(schema_keys, "protectedBookFormat"),
        managed_sqlite=required_object(schema_keys, "managedSqlite"),
        runtime_environment=required_object(schema_keys, "runtimeEnvironment"),
        bundle_layout=required_object(schema_keys, "bundleLayout"),
        bundle_publication=required_object(schema_keys, "bundlePublication"),
        release_publication=required_object(schema_keys, "releasePublication"),
        operation_id=required_object(schema_keys, "operationIdContract"),
    )


def load_contract_documents(repo_root: Path) -> ContractDocuments:
    return ContractDocuments(
        runtime_surface=read_json(_protocol_resource(repo_root, "runtime-surface-contract.json")),
        protected_book_format=read_json(
            _protocol_resource(repo_root, "protected-book-format-contract.json")
        ),
        managed_sqlite=read_json(_protocol_resource(repo_root, "managed-sqlite-contract.json")),
        runtime_environment=load_runtime_environment_document(repo_root),
        bundle_layout=read_json(_protocol_resource(repo_root, "bundle-layout-contract.json")),
        bundle_publication=read_json(
            _protocol_resource(repo_root, "bundle-publication-contract.json")
        ),
        release_publication=read_json(
            _protocol_resource(repo_root, "release-publication-contract.json")
        ),
        operation_ids=read_json(_protocol_resource(repo_root, "operation-id-contract.json")),
    )


def _protocol_resource(repo_root: Path, file_name: str) -> Path:
    return repo_root / "contract/src/main/resources/dev/erst/fingrind/contract/protocol" / file_name
