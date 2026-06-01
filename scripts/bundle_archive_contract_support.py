from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def require_match(text: str, pattern: str, message: str) -> None:
    if re.search(pattern, text, re.MULTILINE) is None:
        raise SystemExit(message)


def require_no_match(text: str, pattern: str, message: str) -> None:
    if re.search(pattern, text, re.MULTILINE) is not None:
        raise SystemExit(message)


def normalize_newlines(text: str) -> str:
    return text.replace("\r", "")


def joined_path(root: Path, relative_path: str) -> Path:
    segments = [segment for segment in relative_path.replace("\\", "/").split("/") if segment]
    return root.joinpath(*segments)


def bundled_java_command(bundle_root: Path) -> Path:
    candidates = [
        bundle_root / "runtime" / "bin" / "java",
        bundle_root / "runtime" / "bin" / "java.exe",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise SystemExit(f"missing bundled Java runtime under {bundle_root / 'runtime' / 'bin'}")


def normalized_command_output(command: list[str]) -> str:
    completed = subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
    )
    return normalize_newlines(completed.stdout + completed.stderr)


def verify_java_version(java_command: Path, expected_source_checkout_java: str) -> None:
    expected_feature_version = expected_source_checkout_java.rstrip("+")
    require(
        bool(expected_feature_version),
        "source-checkout Java contract must not be blank when verifying the bundled runtime",
    )

    version_output = normalized_command_output([str(java_command), "--version"])
    first_line = next((line for line in version_output.splitlines() if line.strip()), "")
    version_tokens = [token for token in first_line.split() if token]
    require(
        len(version_tokens) >= 2
        and (
            version_tokens[1] == expected_feature_version
            or version_tokens[1].startswith(expected_feature_version + ".")
        ),
        f"bundled Java runtime did not report Java {expected_feature_version}",
    )


def require_executable(path: Path, message: str) -> None:
    require(
        os.access(path, os.X_OK),
        message,
    )
