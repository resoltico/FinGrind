from __future__ import annotations

import os
import subprocess

from .models import ReleaseSmokeConfig
from .support import normalize_newlines, require


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
    completed = subprocess.run(
        [*config.command_prefix, *arguments],
        cwd=config.command_cwd,
        env=command_env(config),
        input=stdin_text,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    return normalize_newlines(completed.stdout), completed.returncode


def run_cli_with_split_streams(
    config: ReleaseSmokeConfig,
    *arguments: str,
    stdin_text: str | None = None,
) -> tuple[str, str]:
    completed = subprocess.run(
        [*config.command_prefix, *arguments],
        cwd=config.command_cwd,
        env=command_env(config),
        input=stdin_text,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    stdout = normalize_newlines(completed.stdout)
    stderr = normalize_newlines(completed.stderr)
    require(
        completed.returncode == 0,
        f"{config.label} command {' '.join(arguments)} failed with exit code {completed.returncode}\n{stdout}{stderr}",
    )
    return stdout, stderr


def command_env(config: ReleaseSmokeConfig) -> dict[str, str]:
    environment = os.environ.copy()
    for key in config.command_env_drop:
        environment.pop(key, None)
    environment.update(config.command_env_set)
    return environment

