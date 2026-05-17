#!/usr/bin/env bash
# Verify that the public CI workflow invokes the canonical root verification gate.

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
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly workflow_file="${repo_root}/.github/workflows/ci.yml"

[[ -f "${workflow_file}" ]] || die "missing CI workflow at ${workflow_file}"
grep -Fq 'Run the canonical root verification gate' "${workflow_file}" || die \
    "CI workflow no longer advertises the canonical root verification gate"
grep -Fq './check.sh --no-daemon --console=plain' "${workflow_file}" || die \
    "CI workflow no longer runs the canonical root verification gate"
grep -Fq 'Run root quality gates on Windows' "${workflow_file}" || die \
    "CI workflow no longer keeps the Windows root gate as an explicit fail-fast step"
grep -Fq 'Run included build-logic tests on Windows' "${workflow_file}" || die \
    "CI workflow no longer keeps Windows build-logic verification as a separate step"
if grep -Fq 'Run root quality gates and included build-logic tests on Windows' "${workflow_file}"; then
    die "CI workflow still combines Windows root verification and build-logic verification in one non-fail-fast step"
fi

printf 'CI release-surface workflow regression: success\n'
