"""Contract checks for the archive-native probe's ASCII path transport."""

from __future__ import annotations

import ast
import base64
from pathlib import Path

from .field_matrix.format_boundary_probe_execution import native_probe_path_argument


def assert_native_probe_path_transport_contract() -> None:
    """Keep Windows path arguments independent of the process code page."""
    original_path = "C:/release smoke/Rīga büro/ledger.sqlite"
    encoded_path = native_probe_path_argument(original_path)
    assert encoded_path.isascii()
    assert base64.b64decode(encoded_path).decode("utf-8") == original_path
    _assert_python_probe_arguments_are_encoded()
    _assert_java_probe_decodes_utf8_paths()


def _assert_python_probe_arguments_are_encoded() -> None:
    source_path = Path(__file__).parent / "field_matrix" / "format_boundary_probe_execution.py"
    tree = ast.parse(source_path.read_text(encoding="utf-8"), filename=str(source_path))
    command = _native_probe_command(tree, source_path)
    encoded_option_names = {
        element.value
        for element in command.elts
        if isinstance(element, ast.Constant) and isinstance(element.value, str)
    }
    assert {"--library-base64", "--book-base64", "--key-base64"} <= encoded_option_names
    encoded_paths = [
        element
        for element in command.elts
        if isinstance(element, ast.Call)
        and isinstance(element.func, ast.Name)
        and element.func.id == "native_probe_path_argument"
    ]
    assert len(encoded_paths) == 3


def _native_probe_command(tree: ast.Module, source_path: Path) -> ast.List:
    for node in ast.walk(tree):
        if not isinstance(node, ast.Assign):
            continue
        if any(
            isinstance(target, ast.Name) and target.id == "command" for target in node.targets
        ) and isinstance(node.value, ast.List):
            return node.value
    raise AssertionError(f"{source_path} must construct one native probe command list")


def _assert_java_probe_decodes_utf8_paths() -> None:
    source_path = Path(__file__).parent / "field_matrix" / "NativeSqliteFormatBoundaryProbe.java"
    source = source_path.read_text(encoding="utf-8")
    assert "Base64.getDecoder().decode(arguments[index])" in source
    assert "StandardCharsets.UTF_8" in source
    assert 'case "--library"' not in source
    assert 'case "--book"' not in source
    assert 'case "--key"' not in source
