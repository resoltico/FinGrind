"""Representation-specific evidence for live discovery commands."""

from __future__ import annotations

from collections.abc import Callable, Mapping

from ..cli import run_cli
from ..models import ReleaseSmokeConfig
from ..support import project_version, require
from .discovery_output_support import (
    command_catalog_contains,
    require_identity,
    require_text_facts,
    required_mapping,
    success_payload,
)

DiscoveryModeAssertion = Callable[[str, str], None]


def discovery_mode_assertion(
    config: ReleaseSmokeConfig,
    operation_id: str,
    protocol_version: str,
    environment_sqlite_version: str,
) -> DiscoveryModeAssertion:
    """Build the representation assertion bound to live discovery facts."""

    def assertion(output_mode: str, output: str) -> None:
        if output_mode == "json":
            assert_discovery_json(
                config,
                operation_id,
                output,
                protocol_version,
                environment_sqlite_version,
            )
            return
        require(
            output_mode == "text",
            f"{config.label} field-matrix {operation_id} advertised unsupported discovery mode "
            f"{output_mode}",
        )
        assert_discovery_text(
            config,
            operation_id,
            output,
            protocol_version,
            environment_sqlite_version,
        )

    return assertion


def environment_sqlite_version(config: ReleaseSmokeConfig) -> str:
    """Read the one archive-local SQLite version used by discovery assertions."""
    payload = success_payload(
        config,
        "environment",
        run_cli(config, "environment", "--output", "json"),
    )
    sqlite = required_mapping(payload, "sqlite", f"{config.label} field-matrix environment lookup")
    runtime = required_mapping(
        sqlite,
        "runtime",
        f"{config.label} field-matrix environment lookup sqlite",
    )
    version = runtime.get("loadedSqliteVersion")
    require(
        isinstance(version, str) and bool(version.strip()),
        f"{config.label} field-matrix environment lookup did not expose loaded SQLite version",
    )
    if not isinstance(version, str):
        raise TypeError("require must reject a missing SQLite version")
    return version


def assert_discovery_json(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output: str,
    protocol_version: str,
    environment_sqlite_version: str,
) -> None:
    """Require the durable JSON fact unique to one discovery operation."""
    payload = success_payload(config, operation_id, output)
    if operation_id == "help":
        require_identity(payload, config, operation_id, protocol_version)
        commands = payload.get("commands")
        require(
            isinstance(commands, list) and bool(commands),
            f"{config.label} field-matrix help[json] did not retain command guidance",
        )
        return
    if operation_id == "version":
        require_identity(payload, config, operation_id, protocol_version)
        require(
            isinstance(payload.get("description"), str) and bool(payload["description"].strip()),
            f"{config.label} field-matrix version[json] did not retain the application description",
        )
        return
    if operation_id == "capabilities":
        require_identity(payload, config, operation_id, protocol_version)
        commands = payload.get("commands")
        require(
            isinstance(commands, Mapping),
            f"{config.label} field-matrix capabilities[json] did not retain command families",
        )
        if not isinstance(commands, Mapping):
            raise AssertionError("require must reject missing capability command families")
        require(
            command_catalog_contains(commands, "open-book"),
            f"{config.label} field-matrix capabilities[json] did not retain open-book capability",
        )
        return
    if operation_id == "environment":
        sqlite = required_mapping(
            payload,
            "sqlite",
            f"{config.label} field-matrix environment[json]",
        )
        runtime = required_mapping(
            sqlite,
            "runtime",
            f"{config.label} field-matrix environment[json].sqlite",
        )
        require(
            runtime.get("loadedSqliteVersion") == environment_sqlite_version,
            f"{config.label} field-matrix environment[json] did not retain the live SQLite version",
        )
        return
    raise AssertionError(f"unrouted discovery operation: {operation_id}")


def assert_discovery_text(
    config: ReleaseSmokeConfig,
    operation_id: str,
    output: str,
    protocol_version: str,
    environment_sqlite_version: str,
) -> None:
    """Require the durable text fact unique to one discovery operation."""
    if operation_id == "help":
        require_text_facts(
            config,
            operation_id,
            output,
            "Quick Start",
            "Generate a key file",
            "Open a protected book",
        )
        return
    if operation_id == "version":
        require_text_facts(
            config,
            operation_id,
            output,
            "FinGrind",
            project_version(config.repo_root),
            protocol_version,
        )
        return
    if operation_id == "capabilities":
        require_text_facts(
            config,
            operation_id,
            output,
            "FinGrind",
            project_version(config.repo_root),
            protocol_version,
            "trial-balance",
        )
        return
    if operation_id == "environment":
        require_text_facts(
            config,
            operation_id,
            output,
            "FinGrind Environment",
            environment_sqlite_version,
            "SQLite3MC",
        )
        return
    raise AssertionError(f"unrouted discovery operation: {operation_id}")
