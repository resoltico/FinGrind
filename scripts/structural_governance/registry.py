from __future__ import annotations

import json
from datetime import date
from functools import lru_cache
from pathlib import Path

from .models import ReviewedSurface, ReviewedSurfaceApproval

_REGISTRY_PATH = Path(__file__).with_name("reviewed_surface_registry.json")


@lru_cache(maxsize=1)
def load_text_reviewed_surfaces() -> dict[str, ReviewedSurface]:
    document = _load_registry_document()
    surfaces = [
        _text_reviewed_surface(node) for node in _required_array(document, "textReviewedSurfaces")
    ]
    return {surface.relative_path: surface for surface in _require_unique_text_surfaces(surfaces)}


def _load_registry_document() -> dict[str, object]:
    document = json.loads(_REGISTRY_PATH.read_text(encoding="utf-8"))
    if not isinstance(document, dict):
        raise ValueError(
            f"{_REGISTRY_PATH}: reviewed-surface registry must be one top-level JSON object."
        )
    _required_array(document, "javaReviewedSurfaces")
    _required_array(document, "textReviewedSurfaces")
    _require_unique_java_surfaces(document)
    return document


def _required_array(document: dict[str, object], key: str) -> list[dict[str, object]]:
    value = document.get(key)
    if not isinstance(value, list):
        raise ValueError(f"{_REGISTRY_PATH}: {key} must be one JSON array.")
    normalized: list[dict[str, object]] = []
    for index, element in enumerate(value):
        if not isinstance(element, dict):
            raise ValueError(f"{_REGISTRY_PATH}: {key}[{index}] must be one JSON object.")
        normalized.append(element)
    return normalized


def _require_unique_java_surfaces(document: dict[str, object]) -> None:
    seen: set[tuple[str, str]] = set()
    for index, node in enumerate(_required_array(document, "javaReviewedSurfaces")):
        key = (_required_text(node, "projectPath"), _required_text(node, "relativePath"))
        if key in seen:
            raise ValueError(
                f"{_REGISTRY_PATH}: duplicate java reviewed surface at javaReviewedSurfaces[{index}] for {key[0]}/{key[1]}."
            )
        seen.add(key)


def _require_unique_text_surfaces(
    surfaces: list[ReviewedSurface],
) -> list[ReviewedSurface]:
    seen: set[str] = set()
    for surface in surfaces:
        if surface.relative_path in seen:
            raise ValueError(
                f"{_REGISTRY_PATH}: duplicate text reviewed surface for {surface.relative_path}."
            )
        seen.add(surface.relative_path)
    return surfaces


def _text_reviewed_surface(node: dict[str, object]) -> ReviewedSurface:
    approval = _required_object(node, "approval")
    return ReviewedSurface(
        relative_path=_required_text(node, "relativePath"),
        owner=_required_text(node, "owner"),
        reason=_required_text(node, "reason"),
        split_trigger=_required_text(node, "splitTrigger"),
        reviewed_role_name=_required_text(node, "reviewedRoleName"),
        budget_variance_reason=_optional_text(node, "budgetVarianceReason"),
        approval=ReviewedSurfaceApproval(
            approved_physical_lines=_required_int(approval, "physicalLines"),
            approved_logical_lines=_required_int(approval, "logicalLines"),
            approved_import_like_lines=_required_int(approval, "importLikeLines"),
            approved_functions=_required_int(approval, "functions"),
            approved_nested_types=_required_int(approval, "nestedTypes"),
            expires_on=date.fromisoformat(_required_text(approval, "expiresOn")),
        ),
    )


def _required_object(document: dict[str, object], key: str) -> dict[str, object]:
    value = document.get(key)
    if not isinstance(value, dict):
        raise ValueError(f"{_REGISTRY_PATH}: {key} must be one JSON object.")
    return value


def _required_text(document: dict[str, object], key: str) -> str:
    value = document.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{_REGISTRY_PATH}: {key} must be one non-blank JSON string.")
    return value.strip()


def _optional_text(document: dict[str, object], key: str) -> str | None:
    value = document.get(key)
    if value is None:
        return None
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{_REGISTRY_PATH}: {key} must be absent or one non-blank JSON string.")
    return value.strip()


def _required_int(document: dict[str, object], key: str) -> int:
    value = document.get(key)
    if not isinstance(value, int):
        raise ValueError(f"{_REGISTRY_PATH}: {key} must be one JSON integer.")
    return value
