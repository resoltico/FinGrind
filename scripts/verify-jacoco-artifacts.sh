#!/usr/bin/env bash
# Verify that the repo-owned JaCoCo GA pin resolves to one exact published artifact set.

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

require_command python3

[[ -f "${version_catalog_path}" ]] || die "missing version catalog at ${version_catalog_path}"
[[ -f "${build_metadata_path}" ]] || die "missing build metadata at ${build_metadata_path}"

readonly jacoco_version="$(
    awk -F'"' '$1 ~ /^jacoco = / { print $2; exit }' "${version_catalog_path}"
)"

[[ -n "${jacoco_version}" ]] || die "missing jacoco version pin in ${version_catalog_path}"
[[ "${jacoco_version}" =~ ^0\.[0-9]+\.[0-9]+$ ]] || die \
    "unexpected JaCoCo GA version '${jacoco_version}'"
if grep -Fq 'fingrindJacocoSnapshot' "${build_metadata_path}"; then
    die "gradle/fingrind-build.properties must not retain legacy JaCoCo snapshot metadata"
fi

FINGRIND_JACOCO_VERSION="${jacoco_version}" \
python3 - <<'PY'
from __future__ import annotations

import os
import urllib.request

jacoco_version = os.environ["FINGRIND_JACOCO_VERSION"]

artifact_base = "https://repo.maven.apache.org/maven2/org/jacoco"
artifact_urls = {
    "agent": (
        f"{artifact_base}/org.jacoco.agent/{jacoco_version}/"
        f"org.jacoco.agent-{jacoco_version}.jar"
    ),
    "ant": (
        f"{artifact_base}/org.jacoco.ant/{jacoco_version}/"
        f"org.jacoco.ant-{jacoco_version}.jar"
    ),
    "core": (
        f"{artifact_base}/org.jacoco.core/{jacoco_version}/"
        f"org.jacoco.core-{jacoco_version}.jar"
    ),
    "report": (
        f"{artifact_base}/org.jacoco.report/{jacoco_version}/"
        f"org.jacoco.report-{jacoco_version}.jar"
    ),
}

for label, artifact_url in artifact_urls.items():
    with urllib.request.urlopen(artifact_url, timeout=30) as response:
        if response.status != 200:
            raise SystemExit(
                f"error: JaCoCo artifact {label!r} was not reachable at {artifact_url!r}"
            )

print(f"JaCoCo artifacts verified: version={jacoco_version}")
PY
