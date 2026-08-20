from __future__ import annotations

import json
from datetime import date
from functools import lru_cache
from pathlib import Path

from .models import ReviewedSurface, ReviewedSurfaceApproval

_REGISTRY_ROOT = Path(__file__).with_name("reviewed_surface_registry")
_JAVA_REGISTRY_DIRECTORY = _REGISTRY_ROOT / "java"
_TEXT_REGISTRY_DIRECTORY = _REGISTRY_ROOT / "text"


@lru_cache(maxsize=1)
def load_text_reviewed_surfaces() -> dict[str, ReviewedSurface]:
    _, text_documents = _load_registry_catalog()
    surfaces = [_text_reviewed_surface(path, node) for path, node in text_documents]
    return {surface.relative_path: surface for surface in _require_unique_text_surfaces(surfaces)}


def _load_registry_catalog() -> tuple[
    list[tuple[Path, dict[str, object]]], list[tuple[Path, dict[str, object]]]
]:
    java_documents = _load_fragment_documents(_JAVA_REGISTRY_DIRECTORY, "java")
    text_documents = _load_fragment_documents(_TEXT_REGISTRY_DIRECTORY, "text")
    _require_unique_java_surfaces(java_documents)
    return java_documents, text_documents


def _load_fragment_documents(
    directory: Path, category_name: str
) -> list[tuple[Path, dict[str, object]]]:
    if not directory.is_dir():
        raise ValueError(
            f"{directory}: reviewed-surface {category_name} registry directory is missing."
        )
    paths = sorted(path for path in directory.rglob("*.json") if path.is_file())
    documents: list[tuple[Path, dict[str, object]]] = []
    for path in paths:
        document = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(document, dict):
            raise TypeError(
                f"{path}: reviewed-surface registry fragment must be one top-level JSON object."
            )
        documents.append((path, document))
    return documents


def _require_unique_java_surfaces(documents: list[tuple[Path, dict[str, object]]]) -> None:
    seen: set[tuple[str, str]] = set()
    for path, node in documents:
        key = (
            _required_text(node, "projectPath", path),
            _required_text(node, "relativePath", path),
        )
        if key in seen:
            raise ValueError(f"{path}: duplicate java reviewed surface for {key[0]}/{key[1]}.")
        seen.add(key)


def _require_unique_text_surfaces(
    surfaces: list[ReviewedSurface],
) -> list[ReviewedSurface]:
    seen: set[str] = set()
    for surface in surfaces:
        if surface.relative_path in seen:
            raise ValueError(
                f"{_TEXT_REGISTRY_DIRECTORY}: duplicate text reviewed surface for {surface.relative_path}."
            )
        seen.add(surface.relative_path)
    return surfaces


def _text_reviewed_surface(path: Path, node: dict[str, object]) -> ReviewedSurface:
    approval = _required_object(node, "approval", path)
    return ReviewedSurface(
        relative_path=_required_text(node, "relativePath", path),
        owner=_required_text(node, "owner", path),
        reason=_required_text(node, "reason", path),
        split_trigger=_required_text(node, "splitTrigger", path),
        reviewed_role_name=_required_text(node, "reviewedRoleName", path),
        budget_variance_reason=_optional_text(node, "budgetVarianceReason", path),
        approval=ReviewedSurfaceApproval(
            approved_physical_lines=_required_int(approval, "physicalLines", path),
            approved_logical_lines=_required_int(approval, "logicalLines", path),
            approved_import_like_lines=_required_int(approval, "importLikeLines", path),
            approved_functions=_required_int(approval, "functions", path),
            approved_nested_types=_required_int(approval, "nestedTypes", path),
            expires_on=date.fromisoformat(_required_text(approval, "expiresOn", path)),
        ),
    )


def _required_object(document: dict[str, object], key: str, path: Path) -> dict[str, object]:
    value = document.get(key)
    if not isinstance(value, dict):
        raise TypeError(f"{path}: {key} must be one JSON object.")
    return value


def _required_text(document: dict[str, object], key: str, path: Path) -> str:
    value = document.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{path}: {key} must be one non-blank JSON string.")
    return value.strip()


def _optional_text(document: dict[str, object], key: str, path: Path) -> str | None:
    value = document.get(key)
    if value is None:
        return None
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{path}: {key} must be absent or one non-blank JSON string.")
    return value.strip()


def _required_int(document: dict[str, object], key: str, path: Path) -> int:
    value = document.get(key)
    if not isinstance(value, int):
        raise TypeError(f"{path}: {key} must be one JSON integer.")
    return value
