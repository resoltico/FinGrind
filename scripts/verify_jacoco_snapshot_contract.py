#!/usr/bin/env python3
"""Verify the pinned JaCoCo snapshot metadata resolves to one exact published artifact set."""

from __future__ import annotations

import argparse
import re
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

JACOCO_SNAPSHOT_FETCH_USER_AGENT = "FinGrind-JaCoCo-Snapshot-Verifier/1.0"
DOWNLOAD_CONNECT_TIMEOUT_SECONDS = 15
DOWNLOAD_MAX_ATTEMPTS = 6
DOWNLOAD_RETRY_DELAY_SECONDS = 2
ARTIFACT_BASE = "https://central.sonatype.com/repository/maven-snapshots/org/jacoco"
MODULE_IDS = (
    "org.jacoco.agent",
    "org.jacoco.ant",
    "org.jacoco.core",
    "org.jacoco.report",
)

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


def verify_module_metadata(contract: JacocoSnapshotContract, module_id: str) -> None:
    metadata_url = (
        f"{ARTIFACT_BASE}/{module_id}/{contract.snapshot_base_version}/maven-metadata.xml"
    )
    metadata_payload = fetch_metadata(metadata_url)
    metadata_root = parse_metadata_xml(metadata_payload, metadata_url)

    versioning = child(metadata_root, "versioning", metadata_url)
    snapshot = child(versioning, "snapshot", metadata_url)
    timestamp = child_text(snapshot, "timestamp", metadata_url)
    build_number = child_text(snapshot, "buildNumber", metadata_url)
    expected_timestamp, expected_build_number = resolved_snapshot_coordinates(
        contract.expected_resolved_version,
    )
    if timestamp != expected_timestamp or build_number != expected_build_number:
        raise VerificationError(
            f"JaCoCo metadata for {module_id!r} diverged from the pinned resolved version: "
            f"expected timestamp/build {expected_timestamp}/{expected_build_number}, "
            f"found {timestamp}/{build_number}",
        )

    last_updated = child_text(versioning, "lastUpdated", metadata_url)
    expected_build_minute = contract.expected_build_label.rsplit(".", 1)[1]
    if not last_updated.startswith(expected_build_minute):
        raise VerificationError(
            f"JaCoCo metadata for {module_id!r} no longer matches the pinned build label minute: "
            f"expected prefix {expected_build_minute!r}, found {last_updated!r}",
        )

    snapshot_versions = child(versioning, "snapshotVersions", metadata_url)
    jar_versions = [
        snapshot_version
        for snapshot_version in snapshot_versions.findall("snapshotVersion")
        if child_text(snapshot_version, "extension", metadata_url) == "jar"
        and snapshot_version.find("classifier") is None
    ]
    if not jar_versions:
        raise VerificationError(
            f"JaCoCo metadata for {module_id!r} no longer declares an unclassified jar snapshot",
        )
    jar_value = child_text(jar_versions[0], "value", metadata_url)
    if jar_value != contract.expected_resolved_version:
        raise VerificationError(
            f"JaCoCo metadata for {module_id!r} no longer resolves to the pinned jar version: "
            f"expected {contract.expected_resolved_version!r}, found {jar_value!r}",
        )


def resolved_snapshot_coordinates(resolved_version: str) -> tuple[str, str]:
    match = RESOLVED_VERSION_PATTERN.fullmatch(resolved_version)
    assert match is not None
    return match.group(2), match.group(3)


def fetch_metadata(metadata_url: str) -> bytes:
    last_error: Exception | None = None
    for attempt in range(1, DOWNLOAD_MAX_ATTEMPTS + 1):
        request = urllib.request.Request(
            metadata_url,
            headers={
                "Accept": "application/xml",
                "User-Agent": JACOCO_SNAPSHOT_FETCH_USER_AGENT,
            },
        )
        try:
            with urllib.request.urlopen(
                request,
                timeout=DOWNLOAD_CONNECT_TIMEOUT_SECONDS,
            ) as response:
                return response.read()
        except (TimeoutError, urllib.error.HTTPError, urllib.error.URLError) as error:
            last_error = error
            if attempt == DOWNLOAD_MAX_ATTEMPTS:
                break
            try:
                time.sleep(DOWNLOAD_RETRY_DELAY_SECONDS * attempt)
            except KeyboardInterrupt as interrupted:
                raise VerificationError(
                    f"interrupted while retrying JaCoCo snapshot metadata fetch from {metadata_url}",
                ) from interrupted
    raise VerificationError(
        f"JaCoCo snapshot metadata was not reachable at {metadata_url}: {last_error}",
    )


def parse_metadata_xml(
    metadata_payload: bytes,
    metadata_url: str,
) -> ET.Element:
    try:
        return ET.fromstring(metadata_payload)
    except ET.ParseError as error:
        raise VerificationError(
            f"JaCoCo snapshot metadata at {metadata_url} was not valid XML",
        ) from error


def child(
    parent: ET.Element,
    child_name: str,
    metadata_url: str,
) -> ET.Element:
    child_element = parent.find(child_name)
    if child_element is None:
        raise VerificationError(
            f"JaCoCo snapshot metadata at {metadata_url} is missing <{child_name}>",
        )
    return child_element


def child_text(
    parent: ET.Element,
    child_name: str,
    metadata_url: str,
) -> str:
    child_element = child(parent, child_name, metadata_url)
    child_value = (child_element.text or "").strip()
    if not child_value:
        raise VerificationError(
            f"JaCoCo snapshot metadata at {metadata_url} has an empty <{child_name}>",
        )
    return child_value


def main() -> int:
    args = parse_args()
    contract = read_contract(args.repo_root)
    for module_id in MODULE_IDS:
        verify_module_metadata(contract, module_id)
    print(
        "JaCoCo snapshot verified: "
        f"base={contract.snapshot_base_version} "
        f"build={contract.expected_build_label} "
        f"resolved={contract.expected_resolved_version}",
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except VerificationError as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1) from error
