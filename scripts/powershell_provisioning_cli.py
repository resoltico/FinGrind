"""Own the shared narrow command interface for FinGrind PowerShell provisioning tools."""

from __future__ import annotations

import argparse
import sys
from collections.abc import Callable
from pathlib import Path


def run_provisioning_command[Metadata](
    *,
    description: str,
    metadata_default: Path,
    metadata_help: str,
    install_root_help: str,
    print_option: str,
    print_help: str,
    metadata_loader: Callable[[Path], Metadata],
    metadata_renderer: Callable[[Metadata], str],
    provisioner: Callable[[Metadata, Path], object],
    provisioning_error_type: type[RuntimeError],
) -> int:
    """Run the common metadata-query or explicit-install-root provisioning contract."""

    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--metadata", type=Path, default=metadata_default, help=metadata_help)
    parser.add_argument("--install-root", type=Path, help=install_root_help)
    parser.add_argument(print_option, action="store_true", help=print_help)
    args = parser.parse_args()
    print_requested = getattr(args, print_option.removeprefix("--").replace("-", "_"))
    try:
        metadata = metadata_loader(args.metadata)
        if print_requested:
            if args.install_root is not None:
                raise provisioning_error_type(
                    f"{print_option} cannot be combined with --install-root"
                )
            print(metadata_renderer(metadata))
            return 0
        if args.install_root is None:
            raise provisioning_error_type(
                f"--install-root is required unless {print_option} is selected"
            )
        print(provisioner(metadata, args.install_root))
        return 0
    except provisioning_error_type as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
