"""Regression checks for the UTF-8 release-smoke process boundary."""

from __future__ import annotations

import ast
from pathlib import Path


def assert_cli_transport_encoding_contract() -> None:
    """Require every captured Java/bridge response to remain valid UTF-8 text."""
    _assert_subprocess_utf8_contract(Path(__file__).with_name("cli.py"), expected_calls=4)
    _assert_subprocess_utf8_contract(
        Path(__file__).parent / "field_matrix" / "format_boundary_probe_execution.py",
        expected_calls=1,
    )


def _assert_subprocess_utf8_contract(path: Path, expected_calls: int) -> None:
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    calls = [
        node
        for node in ast.walk(tree)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == "run"
        and isinstance(node.func.value, ast.Name)
        and node.func.value.id == "subprocess"
    ]
    assert len(calls) == expected_calls, (
        f"{path} must retain exactly {expected_calls} release-smoke subprocess transport calls"
    )
    for call in calls:
        keywords = {
            keyword.arg: keyword.value for keyword in call.keywords if keyword.arg is not None
        }
        assert _string_constant(keywords.get("encoding")) == "utf-8", (
            f"{path}:{call.lineno} must decode the release-smoke subprocess boundary as UTF-8"
        )
        assert _string_constant(keywords.get("errors")) == "strict", (
            f"{path}:{call.lineno} must reject malformed release-smoke subprocess output"
        )


def _string_constant(node: ast.expr | None) -> str | None:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    return None
