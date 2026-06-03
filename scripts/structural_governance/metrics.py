from __future__ import annotations

import re
from pathlib import Path
from typing import Iterable

from .models import FileMetrics

KOTLIN_COMMENT_BLOCK_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
KOTLIN_LINE_COMMENT_RE = re.compile(r"//.*?$", re.MULTILINE)
KOTLIN_TRIPLE_STRING_RE = re.compile(r'""".*?"""', re.DOTALL)
KOTLIN_STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')
KOTLIN_CHAR_RE = re.compile(r"'(?:\\.|[^'\\])'")
KOTLIN_FUNCTION_RE = re.compile(r"\bfun\s+[A-Za-z_`][A-Za-z0-9_`<>,\s]*\(")
KOTLIN_TYPE_RE = re.compile(r"\b(?:class|interface|object)\s+[A-Z][A-Za-z0-9_`]*")
KOTLIN_IMPORT_RE = re.compile(r"^\s*import\s+", re.MULTILINE)

SHELL_COMMENT_LINE_RE = re.compile(r"^\s*#")
SHELL_FUNCTION_RE = re.compile(
    r"^\s*(?:function\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*(?:\(\))?\s*\{",
    re.MULTILINE,
)
SHELL_SOURCE_RE = re.compile(r"^\s*(?:source|\.)\s+", re.MULTILINE)

PYTHON_COMMENT_LINE_RE = re.compile(r"^\s*#")
PYTHON_IMPORT_RE = re.compile(r"^\s*(?:from\s+\S+\s+import|import\s+\S+)", re.MULTILINE)
PYTHON_FUNCTION_RE = re.compile(r"^\s*def\s+[A-Za-z_][A-Za-z0-9_]*\s*\(", re.MULTILINE)
PYTHON_TYPE_RE = re.compile(r"^\s*class\s+[A-Z][A-Za-z0-9_]*\s*(?:\(|:)", re.MULTILINE)

SQL_COMMENT_LINE_RE = re.compile(r"^\s*--")
SQL_IMPORT_RE = re.compile(r"^\s*(?:create|alter|drop)\s+", re.MULTILINE)
MARKDOWN_COMMENT_BLOCK_RE = re.compile(r"<!--.*?-->", re.DOTALL)
MARKDOWN_HEADING_RE = re.compile(r"^\s{0,3}#{1,6}\s+", re.MULTILINE)


def measure_kotlin_file(path: Path) -> FileMetrics:
    text = path.read_text(encoding="utf-8")
    sanitized = sanitize_kotlin(text)
    normalized_lines = normalized_nonempty_lines(sanitized.splitlines())
    nested_type_matches = len(KOTLIN_TYPE_RE.findall(sanitized))
    return FileMetrics(
        physical_lines=len(text.splitlines()),
        logical_lines=len(normalized_lines),
        import_like_lines=len(KOTLIN_IMPORT_RE.findall(text)),
        functions=len(KOTLIN_FUNCTION_RE.findall(sanitized)),
        nested_types=max(0, nested_type_matches - 1),
        normalized_nonempty_lines=normalized_lines,
    )


def measure_shell_file(path: Path) -> FileMetrics:
    text = path.read_text(encoding="utf-8")
    normalized_lines = normalized_nonempty_lines(strip_shell_comments(text).splitlines())
    return FileMetrics(
        physical_lines=len(text.splitlines()),
        logical_lines=len(normalized_lines),
        import_like_lines=len(SHELL_SOURCE_RE.findall(text)),
        functions=len(SHELL_FUNCTION_RE.findall(text)),
        nested_types=0,
        normalized_nonempty_lines=normalized_lines,
    )


def measure_python_file(path: Path) -> FileMetrics:
    text = path.read_text(encoding="utf-8")
    normalized_lines = normalized_nonempty_lines(strip_python_comments(text).splitlines())
    return FileMetrics(
        physical_lines=len(text.splitlines()),
        logical_lines=len(normalized_lines),
        import_like_lines=len(PYTHON_IMPORT_RE.findall(text)),
        functions=len(PYTHON_FUNCTION_RE.findall(text)),
        nested_types=len(PYTHON_TYPE_RE.findall(text)),
        normalized_nonempty_lines=normalized_lines,
    )


def measure_sql_file(path: Path) -> FileMetrics:
    text = path.read_text(encoding="utf-8")
    normalized_lines = normalized_nonempty_lines(strip_sql_comments(text).splitlines())
    return FileMetrics(
        physical_lines=len(text.splitlines()),
        logical_lines=len(normalized_lines),
        import_like_lines=len(SQL_IMPORT_RE.findall(text)),
        functions=0,
        nested_types=0,
        normalized_nonempty_lines=normalized_lines,
    )


def measure_markdown_file(path: Path) -> FileMetrics:
    text = path.read_text(encoding="utf-8")
    sanitized = MARKDOWN_COMMENT_BLOCK_RE.sub("", text)
    normalized_lines = normalized_nonempty_lines(sanitized.splitlines())
    return FileMetrics(
        physical_lines=len(text.splitlines()),
        logical_lines=len(normalized_lines),
        import_like_lines=len(MARKDOWN_HEADING_RE.findall(text)),
        functions=0,
        nested_types=0,
        normalized_nonempty_lines=normalized_lines,
    )


def sanitize_kotlin(text: str) -> str:
    without_block_comments = KOTLIN_COMMENT_BLOCK_RE.sub(" ", text)
    without_triple_strings = KOTLIN_TRIPLE_STRING_RE.sub('""', without_block_comments)
    without_strings = KOTLIN_STRING_RE.sub('""', without_triple_strings)
    without_chars = KOTLIN_CHAR_RE.sub("' '", without_strings)
    return KOTLIN_LINE_COMMENT_RE.sub("", without_chars)


def strip_shell_comments(text: str) -> str:
    cleaned: list[str] = []
    for line in text.splitlines():
        if SHELL_COMMENT_LINE_RE.match(line):
            cleaned.append("")
            continue
        comment_index = line.find(" #")
        cleaned.append(line[:comment_index] if comment_index >= 0 else line)
    return "\n".join(cleaned)


def strip_python_comments(text: str) -> str:
    cleaned: list[str] = []
    for line in text.splitlines():
        if PYTHON_COMMENT_LINE_RE.match(line):
            cleaned.append("")
            continue
        comment_index = line.find(" #")
        cleaned.append(line[:comment_index] if comment_index >= 0 else line)
    return "\n".join(cleaned)


def strip_sql_comments(text: str) -> str:
    cleaned: list[str] = []
    for line in text.splitlines():
        if SQL_COMMENT_LINE_RE.match(line):
            cleaned.append("")
            continue
        comment_index = line.find(" --")
        cleaned.append(line[:comment_index] if comment_index >= 0 else line)
    return "\n".join(cleaned)


def normalized_nonempty_lines(lines: Iterable[str]) -> tuple[str, ...]:
    normalized: list[str] = []
    for raw_line in lines:
        line = re.sub(r"\s+", " ", raw_line.strip())
        if not line:
            continue
        line = re.sub(r'"(?:\\.|[^"\\])*"', '""', line)
        line = re.sub(r"\b\d+\b", "0", line)
        normalized.append(line)
    return tuple(normalized)
