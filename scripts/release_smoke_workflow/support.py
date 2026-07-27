from __future__ import annotations

import json
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


def payload_field(payload: dict[str, Any], *path: str | int) -> Any:
    current: Any = payload
    for part in path:
        if isinstance(part, int):
            if not isinstance(current, list) or part < 0 or part >= len(current):
                raise ReleaseSmokeFailure(
                    "missing required JSON field: " + ".".join(str(segment) for segment in path)
                )
            current = current[part]
            continue
        if not isinstance(current, dict) or part not in current:
            raise ReleaseSmokeFailure(
                "missing required JSON field: " + ".".join(str(segment) for segment in path)
            )
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


def require_labeled_text_value(text: str, label: str, expected: str, message: str) -> None:
    lines = text.splitlines()
    values: list[str] = []
    for index, line in enumerate(lines):
        prefix, separator, value = line.partition(":")
        if separator and prefix.rstrip() == label and value.startswith(" "):
            continuation_prefix = " " * (len(prefix) + 3)
            value_lines = [value[1:]]
            for continuation in lines[index + 1 :]:
                if not continuation.startswith(continuation_prefix):
                    break
                value_lines.append(continuation.removeprefix(continuation_prefix))
            values.append(" ".join(value_lines))
    require(values == [expected], message)


def require_rejected_json_diagnostic(
    output: str,
    code: str,
    diagnostic_message: str,
    hint: str,
    label: str,
) -> None:
    envelope = parse_json_output(output, f"{label} output was not valid JSON")
    require(envelope.get("status") == "rejected", f"{label} did not report rejected status")
    require(envelope.get("code") == code, f"{label} did not report {code}")
    require(
        envelope.get("message") == diagnostic_message,
        f"{label} did not report its exact message",
    )
    require(envelope.get("hint") == hint, f"{label} did not report its exact remediation")


def posix_pattern_to_python(pattern: str) -> str:
    return pattern.replace("[[:space:]]", r"\s")


def normalize_newlines(text: str) -> str:
    return text.replace("\r", "")
