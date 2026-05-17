#!/usr/bin/env python3
"""Render canonical managed-SQLite compiler and linker flags for the active host."""

from __future__ import annotations

import json
import platform
import sys
from pathlib import Path


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    contract_path = (
        Path(sys.argv[1]).resolve()
        if len(sys.argv) > 1
        else repo_root
        / "contract/src/main/resources/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json"
    )
    document = json.loads(contract_path.read_text(encoding="utf-8"))
    compile_options = document.get("requiredCompileOptions")
    if not isinstance(compile_options, list) or not compile_options:
        raise SystemExit(
            "managed SQLite contract must declare one non-empty requiredCompileOptions array"
        )
    native_hardening = document.get("nativeHardening")
    if not isinstance(native_hardening, dict):
        raise SystemExit("managed SQLite contract must declare one nativeHardening object")
    requires_secure_memory_support = document.get("requiresSecureMemorySupport")
    if not isinstance(requires_secure_memory_support, bool):
        raise SystemExit(
            "managed SQLite contract must declare requiresSecureMemorySupport as a boolean"
        )

    flags: list[str] = []
    for option in compile_options:
        normalized = option.strip() if isinstance(option, str) else ""
        if not normalized:
            raise SystemExit("managed SQLite compile options must be non-blank strings")
        macro = normalized if normalized.startswith("SQLITE_") else "SQLITE_" + normalized
        if "=" not in macro:
            macro += "=1"
        flags.append("-D" + macro)
    if requires_secure_memory_support:
        flags.append("-DSQLITE3MC_SECURE_MEMORY=1")
    unix_compiler_flags = native_hardening.get("unixCompilerFlags")
    if not isinstance(unix_compiler_flags, list):
        raise SystemExit("nativeHardening.unixCompilerFlags must be one JSON array")
    flags.extend(_normalized_flags(unix_compiler_flags, "nativeHardening.unixCompilerFlags"))

    platform_system = platform.system().lower()
    if "linux" in platform_system:
        linux_linker_flags = native_hardening.get("linuxLinkerFlags")
        if not isinstance(linux_linker_flags, list):
            raise SystemExit("nativeHardening.linuxLinkerFlags must be one JSON array")
        flags.extend(_normalized_flags(linux_linker_flags, "nativeHardening.linuxLinkerFlags"))
    elif "darwin" in platform_system:
        macos_linker_flags = native_hardening.get("macosLinkerFlags")
        if not isinstance(macos_linker_flags, list):
            raise SystemExit("nativeHardening.macosLinkerFlags must be one JSON array")
        flags.extend(_normalized_flags(macos_linker_flags, "nativeHardening.macosLinkerFlags"))

    print(" ".join(flags))
    return 0


def _normalized_flags(values: list[object], label: str) -> list[str]:
    flags: list[str] = []
    for value in values:
        normalized = value.strip() if isinstance(value, str) else ""
        if not normalized:
            raise SystemExit(f"{label} must contain only non-blank strings")
        flags.append(normalized)
    return flags


if __name__ == "__main__":
    raise SystemExit(main())
