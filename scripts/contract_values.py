"""Load canonical FinGrind contract values through focused contract-family owners."""

from __future__ import annotations

import pathlib
import platform

import contract_document_support
import contract_payload_support


def repository_root(script_path: str | pathlib.Path) -> pathlib.Path:
    return pathlib.Path(script_path).resolve().parent.parent


def load_contract_values(
    repo_root: pathlib.Path,
    *,
    os_name: str | None = None,
    architecture: str | None = None,
) -> dict[str, object]:
    schema_sections = contract_document_support.load_contract_schema_sections(repo_root)
    contract_documents = contract_document_support.load_contract_documents(repo_root)
    return contract_payload_support.load_contract_values_payload(
        schema_sections,
        contract_documents,
        os_name=os_name or platform.system(),
        architecture=architecture or platform.machine(),
    )
