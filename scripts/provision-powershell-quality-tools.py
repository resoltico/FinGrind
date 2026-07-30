"""Provision FinGrind's exact Pester and PSScriptAnalyzer quality-tool modules."""

from __future__ import annotations

import json

from powershell_provisioning_cli import run_provisioning_command
from powershell_quality_tools import (
    ProvisioningError,
    QualityToolsMetadata,
    default_metadata_path,
    load_metadata,
    provision_quality_tools,
)


def render_metadata(metadata: QualityToolsMetadata) -> str:
    """Render the immutable module names, releases, and checksums for machine consumers."""

    return json.dumps(
        {
            artifact.module_name: {"sha256": artifact.sha256, "version": artifact.version}
            for artifact in metadata.artifacts
        },
        sort_keys=True,
    )


def main() -> int:
    """Run the quality-tool metadata query or provisioning command."""

    return run_provisioning_command(
        description="Provision FinGrind's checksum-verified, exact PowerShell quality-tool modules.",
        metadata_default=default_metadata_path(),
        metadata_help="Path to the canonical PowerShell quality-tool metadata file.",
        install_root_help="Directory below which verified archives and module trees are atomically published.",
        print_option="--print-metadata",
        print_help="Print canonical module names and exact versions without provisioning.",
        metadata_loader=load_metadata,
        metadata_renderer=render_metadata,
        provisioner=lambda metadata, install_root: json.dumps(
            provision_quality_tools(metadata, install_root).as_json_object(), sort_keys=True
        ),
        provisioning_error_type=ProvisioningError,
    )


if __name__ == "__main__":
    raise SystemExit(main())
