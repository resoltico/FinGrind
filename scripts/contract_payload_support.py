"""Assemble canonical FinGrind contract payloads from focused contract families."""

from __future__ import annotations

import contract_platform
import contract_release_support
from contract_bundle_support import (
    load_bundle_layout_targets,
    load_public_distribution,
    merge_bundle_publication_targets,
)
from contract_document_support import ContractDocuments, ContractSchemaSections
from contract_value_support import (
    load_operation_ids,
    required_bool,
    required_int,
    required_string,
    required_value,
    string_array,
)


def load_contract_values_payload(
    schema_sections: ContractSchemaSections,
    contract_documents: ContractDocuments,
    *,
    os_name: str,
    architecture: str,
) -> dict[str, object]:
    bundle_layout_targets = merge_bundle_publication_targets(
        load_bundle_layout_targets(
            contract_documents.bundle_layout,
            schema_sections.bundle_layout,
        ),
        contract_documents.bundle_publication,
        schema_sections.bundle_publication,
    )
    public_distribution = load_public_distribution(bundle_layout_targets)
    release_publication = contract_release_support.load_release_publication(
        contract_documents.release_publication,
        schema_sections.release_publication,
        bundle_layout_targets=bundle_layout_targets,
    )
    host_bundle_target = contract_platform.load_host_bundle_target(
        bundle_layout_targets,
        os_name=os_name,
        architecture=architecture,
    )
    return {
        "runtimeSurface": _load_runtime_surface(
            contract_documents.runtime_surface,
            schema_sections.runtime_surface,
        ),
        "protectedBookFormat": _load_protected_book_format(
            contract_documents.protected_book_format,
            schema_sections.protected_book_format,
        ),
        "publicDistribution": public_distribution,
        "managedSqlite": _load_managed_sqlite(
            contract_documents.managed_sqlite,
            schema_sections.managed_sqlite,
        ),
        "runtimeEnvironment": {
            "sourceCheckoutJava": required_value(
                contract_documents.runtime_environment,
                required_string(schema_sections.runtime_environment, "sourceCheckoutJava"),
            ),
        },
        "bundleLayout": {
            "targets": bundle_layout_targets,
            "hostBundleTarget": host_bundle_target,
        },
        "releasePublication": release_publication,
        "operationIds": load_operation_ids(contract_documents.operation_ids),
    }


def _load_runtime_surface(
    document: dict[str, object],
    schema: dict[str, object],
) -> dict[str, str]:
    return {
        semantic_name: required_value(document, required_string(schema, semantic_name))
        for semantic_name in (
            "directJavaRuntimeDistribution",
            "sourceCheckoutRuntimeDistribution",
            "containerRuntimeDistribution",
            "bundleRuntimeDistribution",
            "publicCliDistribution",
            "storageDriver",
            "storageEngine",
            "bookProtectionMode",
            "defaultBookCipher",
            "sqliteLibraryMode",
            "sqliteBundleHomeSystemProperty",
        )
    }


def _load_protected_book_format(
    document: dict[str, object],
    schema: dict[str, object],
) -> dict[str, object]:
    return {
        "applicationId": required_int(
            document,
            required_string(schema, "applicationId"),
        ),
        "formatVersion": required_int(
            document,
            required_string(schema, "formatVersion"),
        ),
        "cipher": required_value(document, required_string(schema, "cipher")),
        "legacyMode": required_bool(document, required_string(schema, "legacyMode")),
        "pageSize": required_int(document, required_string(schema, "pageSize")),
        "reservedBytes": required_int(document, required_string(schema, "reservedBytes")),
        "legacyPageSize": required_int(document, required_string(schema, "legacyPageSize")),
        "kdfIter": required_int(document, required_string(schema, "kdfIter")),
        "plaintextHeaderSize": required_int(
            document,
            required_string(schema, "plaintextHeaderSize"),
        ),
    }


def _load_managed_sqlite(
    document: dict[str, object],
    schema: dict[str, object],
) -> dict[str, object]:
    return {
        "requiredMinimumSqliteVersion": required_value(
            document,
            required_string(schema, "requiredMinimumSqliteVersion"),
        ),
        "requiredSqlite3mcVersion": required_value(
            document,
            required_string(schema, "requiredSqlite3mcVersion"),
        ),
        "requiredSqliteSourceId": required_value(
            document,
            required_string(schema, "requiredSqliteSourceId"),
        ),
        "requiredCompileOptions": string_array(
            document,
            required_string(schema, "requiredCompileOptions"),
        ),
        "forbiddenCompileOptions": string_array(
            document,
            required_string(schema, "forbiddenCompileOptions"),
        ),
        "requiresSecureMemorySupport": required_bool(
            document,
            required_string(schema, "requiresSecureMemorySupport"),
        ),
    }
