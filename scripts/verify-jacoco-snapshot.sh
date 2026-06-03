#!/usr/bin/env bash
# Verify that the repo-owned JaCoCo snapshot contract resolves to one exact published artifact set.

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

require_command curl
require_command python3

[[ -f "${version_catalog_path}" ]] || die "missing version catalog at ${version_catalog_path}"
[[ -f "${build_metadata_path}" ]] || die "missing build metadata at ${build_metadata_path}"

readonly jacoco_version="$(
    awk -F'"' '$1 ~ /^jacoco = / { print $2; exit }' "${version_catalog_path}"
)"
readonly snapshot_base_version="$(
    awk -F= '$1 == "fingrindJacocoSnapshotBaseVersion" { print $2; exit }' "${build_metadata_path}"
)"
readonly expected_build_label="$(
    awk -F= '$1 == "fingrindJacocoSnapshotBuildLabel" { print $2; exit }' "${build_metadata_path}"
)"
readonly expected_resolved_version="$(
    awk -F= '$1 == "fingrindJacocoSnapshotResolvedVersion" { print $2; exit }' "${build_metadata_path}"
)"

[[ -z "${jacoco_version}" ]] || die \
    "gradle/libs.versions.toml must not own a floating JaCoCo version entry"
[[ -n "${snapshot_base_version}" ]] || die \
    "missing fingrindJacocoSnapshotBaseVersion in ${build_metadata_path}"
[[ -n "${expected_build_label}" ]] || die \
    "missing fingrindJacocoSnapshotBuildLabel in ${build_metadata_path}"
[[ -n "${expected_resolved_version}" ]] || die \
    "missing fingrindJacocoSnapshotResolvedVersion in ${build_metadata_path}"
[[ "${snapshot_base_version}" =~ ^0\.[0-9]+\.[0-9]+-SNAPSHOT$ ]] || die \
    "unexpected JaCoCo snapshot base version '${snapshot_base_version}'"
[[ "${expected_build_label}" =~ ^0\.[0-9]+\.[0-9]+\.[0-9]{12}$ ]] || die \
    "unexpected JaCoCo snapshot build label '${expected_build_label}'"
[[ "${expected_resolved_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+-[0-9]{8}\.[0-9]{6}-[0-9]+$ ]] || die \
    "unexpected JaCoCo snapshot resolved version '${expected_resolved_version}'"

FINGRIND_JACOCO_SNAPSHOT_BASE_VERSION="${snapshot_base_version}" \
FINGRIND_JACOCO_EXPECTED_BUILD_LABEL="${expected_build_label}" \
FINGRIND_JACOCO_EXPECTED_RESOLVED_VERSION="${expected_resolved_version}" \
python3 - <<'PY'
from __future__ import annotations

import os
import urllib.request

snapshot_base_version = os.environ["FINGRIND_JACOCO_SNAPSHOT_BASE_VERSION"]
expected_build_label = os.environ["FINGRIND_JACOCO_EXPECTED_BUILD_LABEL"]
expected_resolved_version = os.environ["FINGRIND_JACOCO_EXPECTED_RESOLVED_VERSION"]

expected_line = ".".join(expected_build_label.split(".")[:3])
expected_resolved_prefix = expected_resolved_version.split("-", 1)[0]
if expected_line != expected_resolved_prefix:
    raise SystemExit(
        "error: JaCoCo build label and resolved artifact diverged: "
        f"build label {expected_build_label!r}, resolved version {expected_resolved_version!r}"
    )

artifact_base = "https://central.sonatype.com/repository/maven-snapshots/org/jacoco"
artifact_urls = {
    "agent": (
        f"{artifact_base}/org.jacoco.agent/{snapshot_base_version}/"
        f"org.jacoco.agent-{expected_resolved_version}.jar"
    ),
    "ant": (
        f"{artifact_base}/org.jacoco.ant/{snapshot_base_version}/"
        f"org.jacoco.ant-{expected_resolved_version}.jar"
    ),
    "core": (
        f"{artifact_base}/org.jacoco.core/{snapshot_base_version}/"
        f"org.jacoco.core-{expected_resolved_version}.jar"
    ),
    "report": (
        f"{artifact_base}/org.jacoco.report/{snapshot_base_version}/"
        f"org.jacoco.report-{expected_resolved_version}.jar"
    ),
}

for label, artifact_url in artifact_urls.items():
    with urllib.request.urlopen(artifact_url, timeout=30) as response:
        if response.status != 200:
            raise SystemExit(
                f"error: JaCoCo artifact {label!r} was not reachable at {artifact_url!r}"
            )

print(
    "JaCoCo snapshot verified: "
    f"base={snapshot_base_version} build={expected_build_label} resolved={expected_resolved_version}"
)
PY
