"""Private staged-file transport for PowerShell-backed release-smoke commands."""

from __future__ import annotations

import json
import tempfile
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class BridgeRequest:
    """One Python-owned set of private files for a bridged CLI invocation."""

    request_path: Path
    arguments_path: Path
    stdin_path: Path | None

    def cleanup(self) -> None:
        for path in (self.request_path, self.arguments_path, self.stdin_path):
            if path is not None:
                path.unlink(missing_ok=True)


def write_bridge_request(arguments: tuple[str, ...], stdin_text: str | None) -> BridgeRequest:
    """Stage command data once so PowerShell only forwards private-file references.

    Windows PowerShell boundaries must not decode and reserialize application arguments: that
    conversion can change a valid Unicode path before the bundled JVM reads it. The JVM consumes
    the Python-written ASCII JSON argument vector directly; PowerShell only relays its path and,
    when needed, a separate UTF-8 stdin file.
    """
    staged_paths: list[Path] = []
    try:
        arguments_path = _write_bridge_json(list(arguments), "fingrind-release-smoke-arguments-")
        staged_paths.append(arguments_path)
        stdin_path = (
            _write_bridge_text(stdin_text, "fingrind-release-smoke-stdin-")
            if stdin_text is not None
            else None
        )
        if stdin_path is not None:
            staged_paths.append(stdin_path)
        request_path = _write_bridge_json(
            {
                "argumentsFile": str(arguments_path),
                "stdinFile": str(stdin_path) if stdin_path is not None else None,
            },
            "fingrind-release-smoke-bridge-",
        )
        staged_paths.append(request_path)
        return BridgeRequest(request_path, arguments_path, stdin_path)
    except (OSError, TypeError, ValueError):
        for staged_path in staged_paths:
            staged_path.unlink(missing_ok=True)
        raise


def _write_bridge_json(payload: object, prefix: str) -> Path:
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        newline="\n",
        prefix=prefix,
        suffix=".json",
        delete=False,
    ) as handle:
        json.dump(payload, handle, ensure_ascii=True)
        handle.write("\n")
        return Path(handle.name)


def _write_bridge_text(content: str, prefix: str) -> Path:
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        newline="\n",
        prefix=prefix,
        suffix=".txt",
        delete=False,
    ) as handle:
        handle.write(content)
        return Path(handle.name)
