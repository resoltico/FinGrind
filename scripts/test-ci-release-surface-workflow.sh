#!/usr/bin/env bash
# Verify that the public CI workflow enforces the canonical Stage 5 release-surface gate.

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
grep -Fq 'Verify release-surface shell scripts' "${workflow_file}" || die \
    "CI workflow no longer advertises the Stage 5 release-surface gate"
grep -Fq './scripts/check-release-surface-scripts.sh' "${workflow_file}" || die \
    "CI workflow no longer runs the canonical release-surface gate script"

printf 'CI release-surface workflow regression: success\n'
