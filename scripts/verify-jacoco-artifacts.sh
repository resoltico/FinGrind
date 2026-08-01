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
readonly python_runtime_support="${script_dir}/python-runtime-support.sh"

[[ -f "${python_runtime_support}" ]] || die \
    "missing Python runtime support helper at ${python_runtime_support}"
# shellcheck source=/dev/null
source "${python_runtime_support}"
prepare_python_runtime_env

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

exec "${FINGRIND_PYTHON_EXECUTABLE}" "${script_dir}/jacoco_artifact_verification.py" \
    --version "${jacoco_version}"
