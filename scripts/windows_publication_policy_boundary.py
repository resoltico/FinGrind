"""Strict value and lexical-path boundary for Windows publication policy.

This module accepts only values that can safely cross from the native adapter into the pure
publication control plane. It deliberately knows no filesystem state: the PowerShell adapter owns
physical admission, while this boundary owns deterministic JSON, schema, and path normalization.
"""

from __future__ import annotations

import json
import ntpath
import posixpath
import re

_WINDOWS_FILE_NAME_FORBIDDEN_CHARACTERS = frozenset('<>:"/\\|?*')


class PublicationPolicyError(ValueError):
    """Raised when release-publication metadata violates the policy contract."""


def load_json_object(document: str, label: str) -> dict[str, object]:
    """Parse one JSON object while refusing duplicate object keys."""

    try:
        parsed = json.loads(document, object_pairs_hook=_json_object_without_duplicates)
    except json.JSONDecodeError as error:
        raise PublicationPolicyError(f"{label} must be valid JSON") from error
    if not isinstance(parsed, dict):
        raise PublicationPolicyError(f"{label} must be one JSON object")
    return parsed


def require_only_properties(
    document: dict[str, object], allowed_properties: tuple[str, ...], label: str
) -> None:
    """Require an exact closed object schema for one policy message."""

    unexpected_properties = sorted(set(document).difference(allowed_properties))
    if unexpected_properties:
        raise PublicationPolicyError(
            f"{label} must not declare unrecognized properties: " + ", ".join(unexpected_properties)
        )
    missing_properties = sorted(set(allowed_properties).difference(document))
    if missing_properties:
        raise PublicationPolicyError(
            f"{label} must declare required properties: " + ", ".join(missing_properties)
        )


def required_object(
    document: dict[str, object], property_name: str, label: str
) -> dict[str, object]:
    """Return one required JSON object property."""

    value = document.get(property_name)
    if not isinstance(value, dict):
        raise PublicationPolicyError(f"{label} must declare {property_name} as one object")
    return value


def required_text(document: dict[str, object], property_name: str, label: str) -> str:
    """Return one required non-blank JSON string property."""

    value = required_string(document, property_name, label)
    if not value.strip():
        raise PublicationPolicyError(f"{label} must declare non-blank {property_name}")
    return value


def required_string(document: dict[str, object], property_name: str, label: str) -> str:
    """Return one required JSON string property, including an intentional empty value."""

    value = document.get(property_name)
    if not isinstance(value, str):
        raise PublicationPolicyError(f"{label} must declare {property_name} as one string")
    return value


def release_path_component(value: str, label: str) -> str:
    """Validate one exact release metadata value that becomes a Windows file-name component."""

    if (
        not isinstance(value, str)
        or not value
        or value != value.strip()
        or any(
            character in _WINDOWS_FILE_NAME_FORBIDDEN_CHARACTERS or ord(character) < 32
            for character in value
        )
    ):
        raise PublicationPolicyError(f"{label} must be one path-safe file-name component")
    return value


def normalized_absolute_path(value: str, label: str) -> str:
    """Normalize one fully qualified POSIX or Windows lexical path without opening it."""

    if not isinstance(value, str) or not value.strip() or value != value.strip():
        raise PublicationPolicyError(f"{label} must be an absolute path")
    if "\r" in value or "\n" in value or not _is_fully_qualified_path(value):
        raise PublicationPolicyError(f"{label} must be an absolute path")
    return _path_module(value).normpath(value)


def join_path(root: str, *parts: str) -> str:
    """Join lexical paths with the separator family selected by the trusted root."""

    path_module = _path_module(root)
    return path_module.normpath(path_module.join(root, *parts))


def paths_equal(left: str, right: str) -> bool:
    """Compare canonical lexical Windows or POSIX paths case-insensitively."""

    return (
        normalized_absolute_path(left, "path").casefold()
        == normalized_absolute_path(right, "path").casefold()
    )


def _json_object_without_duplicates(pairs: list[tuple[str, object]]) -> dict[str, object]:
    document: dict[str, object] = {}
    for key, value in pairs:
        if key in document:
            raise PublicationPolicyError(f"JSON object must not declare duplicate property: {key}")
        document[key] = value
    return document


def _path_module(value: str):
    if "\\" in value or re.match(r"^[A-Za-z]:", value):
        return ntpath
    return posixpath


def _is_fully_qualified_path(value: str) -> bool:
    if _path_module(value) is posixpath:
        return posixpath.isabs(value)
    drive, tail = ntpath.splitdrive(value)
    return bool(drive) and tail.startswith(("\\", "/"))
