from __future__ import annotations

import json
import ntpath
import posixpath
import re
from pathlib import Path
from typing import Any

from .models import ReleaseSmokeFailure


def project_version(repo_root: Path) -> str:
    for line in (repo_root / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("version="):
            version = line.split("=", 1)[1].strip()
            if version:
                return version
            break
    raise ReleaseSmokeFailure(
        f"could not determine project version from {repo_root / 'gradle.properties'}"
    )


def parse_json_output(output: str, message: str) -> dict[str, Any]:
    try:
        payload = json.loads(output)
    except json.JSONDecodeError as exc:
        raise ReleaseSmokeFailure(message) from exc
    if not isinstance(payload, dict):
        raise ReleaseSmokeFailure(message)
    return payload


def payload_field(payload: dict[str, Any], *path: str) -> Any:
    current: Any = payload
    for part in path:
        if not isinstance(current, dict) or part not in current:
            raise ReleaseSmokeFailure(f"missing required JSON field: {'.'.join(path)}")
        current = current[part]
    return current


def required_mapping(container: dict[str, Any], key: str | None) -> dict[str, Any]:
    value: Any
    if key is None:
        value = container
    else:
        value = container.get(key)
    if not isinstance(value, dict):
        missing_name = key if key is not None else "mapping"
        raise ReleaseSmokeFailure(f"missing required JSON object: {missing_name}")
    return value


def required_list(container: dict[str, Any], key: str) -> list[Any]:
    value = container.get(key)
    if not isinstance(value, list):
        raise ReleaseSmokeFailure(f"missing required JSON array: {key}")
    return value


def require_string(container: dict[str, Any], key: str) -> str:
    value = container.get(key)
    if not isinstance(value, str) or not value:
        raise ReleaseSmokeFailure(f"missing required string field: {key}")
    return value


def require_bool(container: dict[str, Any], key: str) -> bool:
    value = container.get(key)
    if not isinstance(value, bool):
        raise ReleaseSmokeFailure(f"missing required boolean field: {key}")
    return value


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ReleaseSmokeFailure(message)


def require_match(text: str, pattern: str, message: str) -> None:
    if re.search(posix_pattern_to_python(pattern), text, re.MULTILINE) is None:
        raise ReleaseSmokeFailure(message)


def require_no_match(text: str, pattern: str, message: str) -> None:
    if re.search(posix_pattern_to_python(pattern), text, re.MULTILINE) is not None:
        raise ReleaseSmokeFailure(message)


def posix_pattern_to_python(pattern: str) -> str:
    return pattern.replace("[[:space:]]", r"\s")


def normalize_newlines(text: str) -> str:
    return text.replace("\r", "")


def extract_pdf_exported_path(pdf_stderr: str) -> str:
    match = re.search(
        r"^Message\s+:\s+Wrote the requested PDF report artifact to (.+)$",
        pdf_stderr,
        re.MULTILINE,
    )
    if match is None:
        raise ReleaseSmokeFailure(
            "missing pdf-exported diagnostics message with the written artifact path"
        )
    return match.group(1).strip()


def normalize_reported_path(path_text: str) -> str:
    normalized = path_text.strip()
    if not normalized:
        raise ReleaseSmokeFailure("expected one non-blank artifact path")
    if is_windows_like_path(normalized):
        return ntpath.normcase(ntpath.normpath(normalized.replace("/", "\\")))
    return posixpath.normpath(normalized)


def is_windows_like_path(path_text: str) -> bool:
    return (
        "\\" in path_text
        or re.match(r"^[A-Za-z]:[\\/]", path_text) is not None
        or re.match(r"^[\\/]{2}[^\\/]+[\\/]+[^\\/]+", path_text) is not None
    )


def normalized_path_components(path_text: str) -> tuple[str, ...]:
    normalized = normalize_reported_path(path_text)
    if is_windows_like_path(path_text):
        return tuple(
            component for component in normalized.replace("\\", "/").split("/") if component
        )
    return tuple(component for component in normalized.split("/") if component)
