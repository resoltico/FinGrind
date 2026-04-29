#!/usr/bin/env bash
# Verify that the pinned JaCoCo snapshot alias still resolves to the exact build FinGrind expects.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

resolve_script_dir() {
    local source_path="${BASH_SOURCE[0]}"
    while [[ -h "${source_path}" ]]; do
        local source_dir
        source_dir="$(cd -P -- "$(dirname -- "${source_path}")" && pwd)"
        source_path="$(readlink "${source_path}")"
        if [[ "${source_path}" != /* ]]; then
            source_path="${source_dir}/${source_path}"
        fi
    done
    cd -P -- "$(dirname -- "${source_path}")" && pwd
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command '$1' is not available"
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly version_catalog_path="${repo_root}/gradle/libs.versions.toml"
readonly build_metadata_path="${repo_root}/gradle/fingrind-build.properties"
readonly snapshot_metadata_url="https://central.sonatype.com/repository/maven-snapshots/org/jacoco/org.jacoco.agent/0.8.15-SNAPSHOT/maven-metadata.xml"

require_command python3

[[ -f "${version_catalog_path}" ]] || die "missing version catalog at ${version_catalog_path}"
[[ -f "${build_metadata_path}" ]] || die "missing build metadata at ${build_metadata_path}"

readonly jacoco_version="$(
    awk -F'"' '$1 ~ /^jacoco = / { print $2; exit }' "${version_catalog_path}"
)"
readonly expected_build="$(
    awk -F= '$1 == "fingrindJacocoSnapshotBuild" { print $2; exit }' "${build_metadata_path}"
)"
readonly expected_resolved_version="$(
    awk -F= '$1 == "fingrindJacocoSnapshotResolvedVersion" { print $2; exit }' "${build_metadata_path}"
)"

[[ "${jacoco_version}" == "0.8.15-SNAPSHOT" ]] || die \
    "expected gradle/libs.versions.toml to pin JaCoCo 0.8.15-SNAPSHOT but found '${jacoco_version:-missing}'"
[[ -n "${expected_build}" ]] || die \
    "missing fingrindJacocoSnapshotBuild in ${build_metadata_path}"
[[ -n "${expected_resolved_version}" ]] || die \
    "missing fingrindJacocoSnapshotResolvedVersion in ${build_metadata_path}"
[[ "${expected_build}" =~ ^0\.8\.15\.[0-9]{12}$ ]] || die \
    "unexpected JaCoCo snapshot build label '${expected_build}'"
[[ "${expected_resolved_version}" =~ ^0\.8\.15-[0-9]{8}\.[0-9]{6}-[0-9]+$ ]] || die \
    "unexpected JaCoCo snapshot resolved version '${expected_resolved_version}'"

snapshot_metadata="$(
    curl -fsSL "${snapshot_metadata_url}"
)"

FINGRIND_JACOCO_SNAPSHOT_METADATA="${snapshot_metadata}" \
FINGRIND_JACOCO_EXPECTED_BUILD="${expected_build}" \
FINGRIND_JACOCO_EXPECTED_RESOLVED_VERSION="${expected_resolved_version}" \
python3 - <<'PY'
from __future__ import annotations

import os
import xml.etree.ElementTree as ET

metadata_xml = os.environ["FINGRIND_JACOCO_SNAPSHOT_METADATA"]
expected_build = os.environ["FINGRIND_JACOCO_EXPECTED_BUILD"]
expected_resolved_version = os.environ["FINGRIND_JACOCO_EXPECTED_RESOLVED_VERSION"]

root = ET.fromstring(metadata_xml)
version = root.findtext("./version")
last_updated = root.findtext("./versioning/lastUpdated")
snapshot_value = None
for snapshot_version in root.findall("./versioning/snapshotVersions/snapshotVersion"):
    extension = snapshot_version.findtext("extension")
    classifier = snapshot_version.findtext("classifier")
    if extension == "jar" and classifier is None:
        snapshot_value = snapshot_version.findtext("value")
        break

if version != "0.8.15-SNAPSHOT":
    raise SystemExit(
        f"error: JaCoCo snapshot metadata no longer exposes version 0.8.15-SNAPSHOT: {version!r}"
    )
if snapshot_value != expected_resolved_version:
    raise SystemExit(
        "error: JaCoCo snapshot alias drifted: "
        f"expected resolved version {expected_resolved_version!r} but found {snapshot_value!r}"
    )
expected_build_suffix = expected_build.removeprefix("0.8.15.")
if not last_updated or not last_updated.startswith(expected_build_suffix):
    raise SystemExit(
        "error: JaCoCo snapshot metadata lastUpdated no longer matches the pinned build label: "
        f"expected prefix {expected_build_suffix!r} but found {last_updated!r}"
    )
print(
    "JaCoCo snapshot verified: "
    f"alias=0.8.15-SNAPSHOT resolved={snapshot_value} build={expected_build}"
)
PY
