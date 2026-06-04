#!/usr/bin/env python3
"""Verify the pinned JaCoCo snapshot metadata resolves to one exact published artifact set."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path

SNAPSHOT_BASE_PATTERN = re.compile(r"^0\.\d+\.\d+-SNAPSHOT$")
BUILD_LABEL_PATTERN = re.compile(r"^0\.\d+\.\d+\.(\d{12})$")
RESOLVED_VERSION_PATTERN = re.compile(r"^(\d+\.\d+\.\d+)-(\d{8}\.\d{6})-(\d+)$")


class VerificationError(RuntimeError):
    """Raised when the JaCoCo snapshot contract no longer holds."""


@dataclass(frozen=True)
class JacocoSnapshotContract:
    snapshot_base_version: str
    expected_build_label: str
    expected_resolved_version: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, required=True)
    return parser.parse_args()


def read_contract(repo_root: Path) -> JacocoSnapshotContract:
    version_catalog_path = repo_root / "gradle" / "libs.versions.toml"
    build_metadata_path = repo_root / "gradle" / "fingrind-build.properties"
    if not version_catalog_path.is_file():
        raise VerificationError(f"missing version catalog at {version_catalog_path}")
    if not build_metadata_path.is_file():
        raise VerificationError(f"missing build metadata at {build_metadata_path}")

    if re.search(
        r'^\s*jacoco\s*=\s*"',
        version_catalog_path.read_text(encoding="utf-8"),
        re.MULTILINE,
    ):
        raise VerificationError(
            "gradle/libs.versions.toml must not own a floating JaCoCo version entry"
        )

    properties = {}
    for raw_line in build_metadata_path.read_text(encoding="utf-8").splitlines():
        stripped_line = raw_line.strip()
        if not stripped_line or stripped_line.startswith("#"):
            continue
        key, separator, value = stripped_line.partition("=")
        if separator != "=":
            continue
        properties[key] = value

    contract = JacocoSnapshotContract(
        snapshot_base_version=require_property(
            properties,
            "fingrindJacocoSnapshotBaseVersion",
            build_metadata_path,
        ),
        expected_build_label=require_property(
            properties,
            "fingrindJacocoSnapshotBuildLabel",
            build_metadata_path,
        ),
        expected_resolved_version=require_property(
            properties,
            "fingrindJacocoSnapshotResolvedVersion",
            build_metadata_path,
        ),
    )
    validate_contract_shape(contract)
    return contract


def require_property(
    properties: dict[str, str],
    key: str,
    build_metadata_path: Path,
) -> str:
    value = properties.get(key, "")
    if not value:
        raise VerificationError(f"missing {key} in {build_metadata_path}")
    return value


def validate_contract_shape(contract: JacocoSnapshotContract) -> None:
    if not SNAPSHOT_BASE_PATTERN.fullmatch(contract.snapshot_base_version):
        raise VerificationError(
            f"unexpected JaCoCo snapshot base version {contract.snapshot_base_version!r}",
        )
    if BUILD_LABEL_PATTERN.fullmatch(contract.expected_build_label) is None:
        raise VerificationError(
            f"unexpected JaCoCo snapshot build label {contract.expected_build_label!r}",
        )
    if RESOLVED_VERSION_PATTERN.fullmatch(contract.expected_resolved_version) is None:
        raise VerificationError(
            f"unexpected JaCoCo snapshot resolved version {contract.expected_resolved_version!r}",
        )

    expected_line = ".".join(contract.expected_build_label.split(".")[:3])
    expected_resolved_prefix = contract.expected_resolved_version.split("-", 1)[0]
    if expected_line != expected_resolved_prefix:
        raise VerificationError(
            "JaCoCo build label and resolved artifact diverged: "
            f"build label {contract.expected_build_label!r}, "
            f"resolved version {contract.expected_resolved_version!r}",
        )


def main() -> int:
    args = parse_args()
    contract = read_contract(args.repo_root)
    print(
        "JaCoCo snapshot verified: "
        f"base={contract.snapshot_base_version} "
        f"build={contract.expected_build_label} "
        f"resolved={contract.expected_resolved_version} "
        "contract=local-shape",
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except VerificationError as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1) from error
