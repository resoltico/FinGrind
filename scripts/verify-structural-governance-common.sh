#!/usr/bin/env bash
# Shared path and verifier helpers for structural-governance shell regressions.

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

readonly structural_governance_common_script_dir="$(resolve_script_dir)"
readonly structural_governance_common_repo_root="$(cd -P -- "${structural_governance_common_script_dir}/.." && pwd)"
readonly structural_governance_verifier_py="${structural_governance_common_repo_root}/scripts/verify-structural-governance.py"

[[ -f "${structural_governance_verifier_py}" ]] || die \
    "missing structural governance verifier at ${structural_governance_verifier_py}"

run_expect_success() {
    local fixture_root=$1
    shift
    python3 "${structural_governance_verifier_py}" --repo-root "${fixture_root}" "$@" >/dev/null
}

run_expect_failure() {
    local expected_fragment=$1
    local fixture_root=$2
    shift 2
    local output
    local status
    set +e
    output="$(python3 "${structural_governance_verifier_py}" --repo-root "${fixture_root}" "$@" 2>&1)"
    status=$?
    set -e
    [[ ${status} -ne 0 ]] || die "verifier unexpectedly succeeded for ${expected_fragment}"
    [[ "${output}" == *"${expected_fragment}"* ]] || die \
        "verifier output did not contain expected fragment: ${expected_fragment}"
}

run_in_temp_fixture() {
    local callback=$1
    local temp_dir
    temp_dir="$(mktemp -d)"
    "${callback}" "${temp_dir}"
    rm -rf "${temp_dir}"
}
