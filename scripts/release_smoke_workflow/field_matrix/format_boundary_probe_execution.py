"""Archive-runtime native SQLite probe execution for format-boundary scenarios."""

from __future__ import annotations

import base64
import os
import subprocess
from collections.abc import Mapping
from pathlib import Path

from ..cli import command_env, run_cli
from ..models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from ..support import parse_json_output, require, require_string, required_mapping

_NATIVE_PROBE_CLASS_NAME = "NativeSqliteFormatBoundaryProbe"


def loaded_sqlite_library_path(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, object],
) -> str:
    """Read the archive's exact loaded SQLite path from its environment report."""
    environment = parse_json_output(
        run_cli(
            config,
            required_operation_id(operation_ids, "environment", config),
            "--output",
            "json",
        ),
        f"{config.label} environment output was not valid JSON for format-boundary testing",
    )
    payload = required_mapping(environment, "payload")
    sqlite = required_mapping(payload, "sqlite")
    runtime = required_mapping(sqlite, "runtime")
    library_path = require_string(runtime, "loadedLibraryPath")
    require(
        Path(library_path).is_absolute(),
        f"{config.label} environment output did not identify one absolute archive SQLite library",
    )
    return library_path


def required_operation_id(
    operation_ids: Mapping[str, object],
    key: str,
    config: ReleaseSmokeConfig,
) -> str:
    """Read a required operation identifier from the live discovery contract."""
    value = operation_ids.get(key)
    require(
        isinstance(value, str) and bool(value),
        f"{config.label} release-smoke contract did not publish operation ID {key}",
    )
    if not isinstance(value, str) or not value:
        raise AssertionError("required operation ID must be a non-empty string")
    return value


def write_user_version(
    config: ReleaseSmokeConfig,
    library_path: str,
    book_path: SmokePath,
    key_path: SmokePath,
    boundary_format: int,
) -> None:
    """Set and immediately read the copied book's authenticated format marker."""
    observed_version = run_native_sqlite_probe(
        config,
        library_path,
        book_path,
        key_path,
        "--set-user-version",
        str(boundary_format),
    )
    require(
        observed_version == boundary_format,
        f"{config.label} archive SQLite did not persist the {boundary_format} protected-book "
        "format marker before public inspection",
    )


def require_persisted_user_version(
    config: ReleaseSmokeConfig,
    library_path: str,
    book_path: SmokePath,
    key_path: SmokePath,
    expected_version: int,
    phase: str,
) -> None:
    """Prove an exercise phase left the copied SQLite user-version marker unchanged."""
    observed_version = run_native_sqlite_probe(
        config,
        library_path,
        book_path,
        key_path,
        "--read-user-version",
    )
    require(
        observed_version == expected_version,
        f"{config.label} protected-book format boundary proof rewrote the format marker "
        f"during {phase}",
    )


def run_native_sqlite_probe(
    config: ReleaseSmokeConfig,
    library_path: str,
    book_path: SmokePath,
    key_path: SmokePath,
    action: str,
    action_value: str | None = None,
) -> int:
    """Run the packaged Java FFM probe against the exact reported native library."""
    command = [
        *native_probe_java_prefix(config, library_path),
        "--enable-native-access=ALL-UNNAMED",
        "-cp",
        native_probe_classpath_argument(config),
        _NATIVE_PROBE_CLASS_NAME,
        "--library-base64",
        native_probe_path_argument(library_path),
        "--book-base64",
        native_probe_path_argument(book_path.argument),
        "--key-base64",
        native_probe_path_argument(key_path.argument),
        action,
    ]
    if action_value is not None:
        command.append(action_value)
    completed = subprocess.run(
        command,
        cwd=config.command_cwd,
        env=command_env(config),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="strict",
        check=False,
    )
    output = completed.stdout.replace("\r\n", "\n").strip()
    require(
        completed.returncode == 0,
        f"{config.label} archive-native SQLite format-boundary probe failed\n{output}",
    )
    try:
        return int(output)
    except ValueError as exc:
        raise ReleaseSmokeFailure(
            f"{config.label} archive-native SQLite format-boundary probe did not emit one integer"
        ) from exc


def native_probe_path_argument(path: str) -> str:
    """Encode one path as ASCII so Windows process transport is lossless."""
    return base64.b64encode(path.encode("utf-8")).decode("ascii")


def native_probe_java_prefix(config: ReleaseSmokeConfig, library_path: str) -> list[str]:
    """Resolve the archive runtime, or use the configured execution-prefix boundary."""
    if config.native_sqlite_java_prefix:
        return list(config.native_sqlite_java_prefix)
    native_library = Path(library_path)
    native_directory = native_library.parent
    bundle_home = native_directory.parent.parent
    require(
        native_directory.name == "native"
        and native_directory.parent.name == "lib"
        and bundle_home.is_dir(),
        f"{config.label} could not derive one archive Java runtime from its reported SQLite path",
    )
    java_name = "java.exe" if os.name == "nt" else "java"
    java_executable = bundle_home / "runtime" / "bin" / java_name
    require(
        java_executable.is_file(),
        f"{config.label} archive runtime did not contain its Java executable for format-boundary testing",
    )
    return [str(java_executable)]


def native_probe_classpath_argument(config: ReleaseSmokeConfig) -> str:
    """Require the packaged probe classpath before archive-native execution."""
    probe_jar = config.native_sqlite_probe_classpath
    require(
        bool(probe_jar),
        f"{config.label} did not configure the packaged native SQLite format-boundary probe",
    )
    if not config.native_sqlite_java_prefix:
        probe_path = Path(probe_jar)
        require(
            probe_path.is_file() and not probe_path.is_symlink(),
            f"{config.label} packaged native SQLite format-boundary probe was not one regular file",
        )
    return probe_jar
