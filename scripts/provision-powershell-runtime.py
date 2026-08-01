"""Provision FinGrind's exact, checksum-verified PowerShell runtime."""

from __future__ import annotations

from powershell_provisioning_cli import run_provisioning_command
from powershell_runtime import (
    ProvisioningError,
    default_metadata_path,
    load_metadata,
    provision_runtime,
)


def main() -> int:
    """Run the PowerShell runtime's metadata query or provisioning command."""

    return run_provisioning_command(
        description="Provision FinGrind's checksum-verified, exact PowerShell runtime.",
        metadata_default=default_metadata_path(),
        metadata_help="Path to the canonical gradle/fingrind-build.properties file.",
        install_root_help="Directory below which the verified runtime is atomically published.",
        print_option="--print-version",
        print_help="Print the exact canonical PowerShell version without provisioning.",
        metadata_loader=load_metadata,
        metadata_renderer=lambda metadata: metadata.version,
        provisioner=provision_runtime,
        provisioning_error_type=ProvisioningError,
    )


if __name__ == "__main__":
    raise SystemExit(main())
