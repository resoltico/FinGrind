#!/usr/bin/env bash
# Verify the source-checkout managed SQLite runtime contract against the generated launcher.

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
readonly verifier="${script_dir}/verify-sqlite-runtime-contract.py"

[[ -f "${verifier}" ]] || die "missing SQLite runtime verifier at ${verifier}"

environment_output="$(
    cd "${repo_root}" &&
        ./scripts/source-checkout-cli.sh environment --output json
)"
if ! verifier_output="$(
    printf '%s\n' "${environment_output}" |
        python3 "${verifier}" \
            --expected-runtime-distribution-key sourceCheckoutRuntimeDistribution \
            --expected-runtime-provenance source-checkout-managed \
            --label source-checkout-managed-runtime 2>&1
)"; then
    printf '%s\n' "${environment_output}"
    printf '%s\n' "${verifier_output}" >&2
    exit 1
fi
printf '%s\n' "${verifier_output}"
