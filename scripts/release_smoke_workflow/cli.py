from __future__ import annotations

import json
import os
import subprocess
import tempfile
from pathlib import Path

from .models import ReleaseSmokeConfig
from .support import normalize_newlines, require


def emit_command_progress(config: ReleaseSmokeConfig, arguments: tuple[str, ...]) -> None:
    """Expose one non-sensitive heartbeat for each independently executed CLI command.

    The release workflow captures every command's public output for assertion, so its
    parent process would otherwise remain silent throughout a healthy long matrix.
    Reporting only the operation identifier preserves the assertions' stream boundary
    and avoids putting workspace paths or credential-bearing arguments in the log.
    """
    print(f"{config.label}: running {arguments[0]}", flush=True)


def run_cli(
    config: ReleaseSmokeConfig,
    *arguments: str,
    stdin_text: str | None = None,
) -> str:
    output, exit_code = run_cli_allow_failure(config, *arguments, stdin_text=stdin_text)
    require(
        exit_code == 0,
        f"{config.label} command {' '.join(arguments)} failed with exit code {exit_code}\n{output}",
    )
    return output


def run_cli_allow_failure(
    config: ReleaseSmokeConfig,
    *arguments: str,
    stdin_text: str | None = None,
) -> tuple[str, int]:
    emit_command_progress(config, arguments)
    if config.command_bridge_prefix:
        return run_cli_allow_failure_via_bridge(config, *arguments, stdin_text=stdin_text)
    completed = subprocess.run(
        [*config.command_prefix, *arguments],
        cwd=config.command_cwd,
        env=command_env(config),
        input=stdin_text,
        text=True,
        encoding="utf-8",
        errors="strict",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    return normalize_newlines(completed.stdout), completed.returncode


def run_cli_allow_failure_via_bridge(
    config: ReleaseSmokeConfig,
    *arguments: str,
    stdin_text: str | None = None,
) -> tuple[str, int]:
    request_path = write_bridge_request(arguments, stdin_text)
    try:
        completed = subprocess.run(
            [*config.command_bridge_prefix, str(request_path)],
            cwd=config.command_cwd,
            env=command_env(config),
            text=True,
            encoding="utf-8",
            errors="strict",
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
    finally:
        request_path.unlink(missing_ok=True)
    return normalize_newlines(completed.stdout), completed.returncode


def run_cli_with_split_streams(
    config: ReleaseSmokeConfig,
    *arguments: str,
    stdin_text: str | None = None,
) -> tuple[str, str]:
    stdout, stderr, exit_code = run_cli_allow_failure_with_split_streams(
        config,
        *arguments,
        stdin_text=stdin_text,
    )
    require(
        exit_code == 0,
        f"{config.label} command {' '.join(arguments)} failed with exit code {exit_code}\n{stdout}{stderr}",
    )
    return stdout, stderr


def run_cli_allow_failure_with_split_streams(
    config: ReleaseSmokeConfig,
    *arguments: str,
    stdin_text: str | None = None,
) -> tuple[str, str, int]:
    """Run one command while preserving its public stdout/stderr boundary.

    Deterministic failures select their renderer from the requested output mode.
    Callers that exercise an expected failure therefore need both streams, not the
    combined diagnostic convenience used by generic JSON-failure checks.
    """
    emit_command_progress(config, arguments)
    if config.command_bridge_prefix:
        return run_cli_allow_failure_with_split_streams_via_bridge(
            config,
            *arguments,
            stdin_text=stdin_text,
        )
    completed = subprocess.run(
        [*config.command_prefix, *arguments],
        cwd=config.command_cwd,
        env=command_env(config),
        input=stdin_text,
        text=True,
        encoding="utf-8",
        errors="strict",
        capture_output=True,
        check=False,
    )
    stdout = normalize_newlines(completed.stdout)
    stderr = normalize_newlines(completed.stderr)
    return stdout, stderr, completed.returncode


def run_cli_allow_failure_with_split_streams_via_bridge(
    config: ReleaseSmokeConfig,
    *arguments: str,
    stdin_text: str | None = None,
) -> tuple[str, str, int]:
    request_path = write_bridge_request(arguments, stdin_text)
    try:
        completed = subprocess.run(
            [*config.command_bridge_prefix, str(request_path)],
            cwd=config.command_cwd,
            env=command_env(config),
            text=True,
            encoding="utf-8",
            errors="strict",
            capture_output=True,
            check=False,
        )
    finally:
        request_path.unlink(missing_ok=True)
    stdout = normalize_newlines(completed.stdout)
    stderr = normalize_newlines(completed.stderr)
    return stdout, stderr, completed.returncode


def command_env(config: ReleaseSmokeConfig) -> dict[str, str]:
    environment = os.environ.copy()
    for key in config.command_env_drop:
        environment.pop(key, None)
    environment.update(config.command_env_set)
    return environment


def write_bridge_request(arguments: tuple[str, ...], stdin_text: str | None) -> Path:
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        newline="\n",
        prefix="fingrind-release-smoke-",
        suffix=".json",
        delete=False,
    ) as handle:
        json.dump(
            {"arguments": list(arguments), "stdinText": stdin_text},
            handle,
            ensure_ascii=True,
        )
        handle.write("\n")
        return Path(handle.name)
