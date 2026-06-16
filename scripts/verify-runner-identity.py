#!/usr/bin/env python3
"""Verify one workflow runner against canonical bundle target ids."""

from __future__ import annotations

import argparse
import sys

from contract_platform import architecture_id, operating_system_id


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Normalize one runner's live OS and architecture spellings and verify them "
            "against canonical FinGrind bundle target ids."
        )
    )
    parser.add_argument("--expected-os-id", required=True, help="Expected canonical OS id.")
    parser.add_argument(
        "--expected-arch-id",
        required=True,
        help="Expected canonical architecture id.",
    )
    parser.add_argument(
        "--actual-os-name",
        required=True,
        help="Observed OS name such as uname -s or a Windows runtime label.",
    )
    parser.add_argument(
        "--actual-architecture",
        required=True,
        help="Observed architecture such as uname -m or RuntimeInformation.OSArchitecture.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    normalized_os_id = operating_system_id(args.actual_os_name)
    normalized_arch_id = architecture_id(args.actual_architecture)
    if normalized_os_id != args.expected_os_id:
        print(
            "error: expected canonical OS id "
            f"{args.expected_os_id} but normalized {args.actual_os_name!r} to {normalized_os_id}",
            file=sys.stderr,
        )
        return 1
    if normalized_arch_id != args.expected_arch_id:
        print(
            "error: expected canonical architecture id "
            f"{args.expected_arch_id} but normalized {args.actual_architecture!r} to {normalized_arch_id}",
            file=sys.stderr,
        )
        return 1
    print(
        f"runner identity verified as {normalized_os_id}-{normalized_arch_id}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
