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
KOTLIN_IMPORT_RE = re.compile(r"^\s*import\s+", re.MULTILINE)
KOTLIN_DECLARATION_TOKEN_RE = re.compile(r"`[^`]+`|[A-Za-z_][A-Za-z0-9_]*|[{}():.]")

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
JSON_KEY_RE = re.compile(r'^\s*"(?:\\.|[^"\\])*"\s*:', re.MULTILINE)


def measure_kotlin_file(path: Path) -> FileMetrics:
    text = path.read_text(encoding="utf-8")
    sanitized = sanitize_kotlin(text)
    normalized_lines = normalized_nonempty_lines(sanitized.splitlines())
    functions, nested_types = count_kotlin_declarations(sanitized)
    return FileMetrics(
        physical_lines=len(text.splitlines()),
        logical_lines=len(normalized_lines),
        import_like_lines=len(KOTLIN_IMPORT_RE.findall(text)),
        functions=functions,
        nested_types=nested_types,
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


def measure_json_file(path: Path) -> FileMetrics:
    text = path.read_text(encoding="utf-8")
    normalized_lines = normalized_nonempty_lines(text.splitlines())
    return FileMetrics(
        physical_lines=len(text.splitlines()),
        logical_lines=len(normalized_lines),
        import_like_lines=len(JSON_KEY_RE.findall(text)),
        functions=0,
        nested_types=count_json_nested_structures(text),
        normalized_nonempty_lines=normalized_lines,
    )


def sanitize_kotlin(text: str) -> str:
    without_block_comments = KOTLIN_COMMENT_BLOCK_RE.sub(" ", text)
    without_triple_strings = KOTLIN_TRIPLE_STRING_RE.sub('""', without_block_comments)
    without_strings = KOTLIN_STRING_RE.sub('""', without_triple_strings)
    without_chars = KOTLIN_CHAR_RE.sub("' '", without_strings)
    return KOTLIN_LINE_COMMENT_RE.sub("", without_chars)


def count_kotlin_declarations(text: str) -> tuple[int, int]:
    tokens = KOTLIN_DECLARATION_TOKEN_RE.findall(text)
    brace_depth = 0
    functions = 0
    nested_types = 0
    index = 0
    while index < len(tokens):
        token = tokens[index]
        if token == "{":
            brace_depth += 1
        elif token == "}":
            brace_depth = max(0, brace_depth - 1)
        elif token == "fun":
            next_token = tokens[index + 1] if index + 1 < len(tokens) else None
            if next_token == "interface":
                type_name = tokens[index + 2] if index + 2 < len(tokens) else None
                if is_kotlin_identifier(type_name) and brace_depth > 0:
                    nested_types += 1
                index += 1
            else:
                functions += 1
        elif token in {"class", "interface"}:
            next_token = tokens[index + 1] if index + 1 < len(tokens) else None
            if is_kotlin_identifier(next_token) and brace_depth > 0:
                nested_types += 1
        elif token == "object":
            previous_token = tokens[index - 1] if index > 0 else None
            next_token = tokens[index + 1] if index + 1 < len(tokens) else None
            if (
                previous_token == "companion" or is_kotlin_identifier(next_token)
            ) and brace_depth > 0:
                nested_types += 1
        index += 1
    return functions, nested_types


def is_kotlin_identifier(token: str | None) -> bool:
    if token is None or token in {"{", "}", "(", ")", ":", "."}:
        return False
    return True


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


def count_json_nested_structures(text: str) -> int:
    in_string = False
    escape = False
    container_depth = 0
    nested_structures = 0
    for character in text:
        if in_string:
            if escape:
                escape = False
            elif character == "\\":
                escape = True
            elif character == '"':
                in_string = False
            continue
        if character == '"':
            in_string = True
            continue
        if character in "{[":
            if container_depth > 0:
                nested_structures += 1
            container_depth += 1
            continue
        if character in "}]" and container_depth > 0:
            container_depth -= 1
    return nested_structures


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
