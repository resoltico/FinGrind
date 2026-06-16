#!/usr/bin/env bash
# Regress the canonical runner-identity verifier against the contract-owned platform vocabulary.

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
readonly verifier="${repo_root}/scripts/verify-runner-identity.py"

[[ -f "${verifier}" ]] || die "missing runner-identity verifier at ${verifier}"

python3 "${verifier}" \
    --expected-os-id macos \
    --expected-arch-id aarch64 \
    --actual-os-name Darwin \
    --actual-architecture arm64 >/dev/null

python3 "${verifier}" \
    --expected-os-id linux \
    --expected-arch-id x86_64 \
    --actual-os-name Linux \
    --actual-architecture x64 >/dev/null

python3 "${verifier}" \
    --expected-os-id windows \
    --expected-arch-id x86_64 \
    --actual-os-name Windows \
    --actual-architecture X64 >/dev/null

set +e
failure_output="$(
    python3 "${verifier}" \
        --expected-os-id linux \
        --expected-arch-id aarch64 \
        --actual-os-name Windows \
        --actual-architecture X64 2>&1
)"
failure_status=$?
set -e

[[ ${failure_status} -ne 0 ]] || die "runner-identity verifier accepted one contradictory runner surface"
printf '%s' "${failure_output}" | grep -Fq "expected canonical OS id linux" || die \
    "runner-identity verifier no longer reports canonical OS-id mismatches"

printf 'runner identity verifier regression: success\n'
