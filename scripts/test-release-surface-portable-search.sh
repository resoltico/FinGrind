#!/usr/bin/env bash
# Ensure release-surface regressions keep their search dependency portable across hosted runners.

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
readonly optional_search_tool='r''g'
readonly release_surface_search_scripts=(
    "${repo_root}/scripts/ci-release-surface-workflow-assertions-support.sh"
    "${repo_root}/scripts/test-check-windows-contract.sh"
    "${repo_root}/scripts/test-ci-release-surface-workflow.sh"
    "${repo_root}/scripts/test-no-product-bigdecimal.sh"
    "${repo_root}/scripts/test-release-smoke-workflow.sh"
    "${repo_root}/scripts/test-verify-windows-publication-surface.sh"
    "${repo_root}/scripts/test-windows-portable-archive-path-policy.sh"
)

for script_path in "${release_surface_search_scripts[@]}"; do
    [[ -f "${script_path}" ]] || die "missing release-surface search owner at ${script_path}"
done

matches="$(
    grep -n -E "^[[:space:]]*${optional_search_tool}([[:space:]]|$)" \
        "${release_surface_search_scripts[@]}" || true
)"
[[ -z "${matches}" ]] || die \
    "release-surface scripts must not depend on the optional ${optional_search_tool} command: ${matches}"

printf 'release-surface portable-search regression: success\n'
