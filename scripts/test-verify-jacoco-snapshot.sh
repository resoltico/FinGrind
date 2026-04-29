#!/usr/bin/env bash
# Guard the pinned JaCoCo snapshot verifier against drift in the canonical build metadata.

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

readonly script_dir="$(resolve_script_dir)"
readonly verifier="${script_dir}/verify-jacoco-snapshot.sh"
readonly version_catalog_path="${script_dir}/../gradle/libs.versions.toml"
readonly build_metadata_path="${script_dir}/../gradle/fingrind-build.properties"

[[ -f "${verifier}" ]] || die "missing JaCoCo snapshot verifier at ${verifier}"
[[ -f "${version_catalog_path}" ]] || die "missing version catalog at ${version_catalog_path}"
[[ -f "${build_metadata_path}" ]] || die "missing build metadata at ${build_metadata_path}"

grep -Fq 'jacoco = "0.8.15-SNAPSHOT"' "${version_catalog_path}" || die \
    "version catalog no longer pins the JaCoCo snapshot alias"
grep -Fq 'fingrindJacocoSnapshotBuild=0.8.15.202604281210' "${build_metadata_path}" || die \
    "build metadata no longer carries the pinned JaCoCo snapshot build label"
grep -Fq 'fingrindJacocoSnapshotResolvedVersion=0.8.15-20260428.121054-96' "${build_metadata_path}" || die \
    "build metadata no longer carries the pinned JaCoCo resolved snapshot version"
"${verifier}" >/dev/null

printf 'JaCoCo snapshot verifier regression: success\n'
