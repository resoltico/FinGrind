from __future__ import annotations

import ntpath
import posixpath
import re

from .models import ReleaseSmokeFailure


def extract_pdf_artifact_path(pdf_stdout: str) -> str:
    match = re.search(
        r"^Path\s+:\s+(.+)$",
        pdf_stdout,
        re.MULTILINE,
    )
    if match is None:
        raise ReleaseSmokeFailure("missing artifact confirmation path for the written PDF report")
    return match.group(1).strip()


def extract_pdf_retained_stage(pdf_stdout: str) -> str:
    match = re.search(
        r"^Retained stage\s+:\s+(.+)$",
        pdf_stdout,
        re.MULTILINE,
    )
    if match is None:
        raise ReleaseSmokeFailure("missing retained-stage confirmation for the written PDF report")
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
