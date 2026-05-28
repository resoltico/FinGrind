#!/usr/bin/env bash
# Verify FinGrind non-Java structural governance surfaces through the repo-owned Python analyzer.

set -euo pipefail

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
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"
readonly verifier_py="${repo_root}/scripts/verify-structural-governance.py"

print_usage() {
    printf '%s\n' \
        'Usage: ./scripts/verify-structural-governance.sh [--surface build-logic-kotlin] [--surface shell-release]' \
        '' \
        'Verifies FinGrind structural-governance budgets for non-Java control-plane surfaces.' \
        'Without --surface, the verifier checks both build-logic-kotlin and shell-release.'
}

for argument in "$@"; do
    case "${argument}" in
        -h|--help)
            print_usage
            exit 0
            ;;
    esac
done

[[ -f "${python_runtime_support}" ]] || {
    printf 'error: missing Python runtime support helper at %s\n' "${python_runtime_support}" >&2
    exit 1
}
[[ -f "${verifier_py}" ]] || {
    printf 'error: missing structural governance verifier at %s\n' "${verifier_py}" >&2
    exit 1
}

# shellcheck source=/dev/null
source "${python_runtime_support}"
prepare_python_runtime_env

"${FINGRIND_PYTHON_EXECUTABLE}" "${verifier_py}" --repo-root "${repo_root}" "$@"
