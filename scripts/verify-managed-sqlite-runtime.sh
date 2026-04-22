#!/usr/bin/env bash
# Verify the source-checkout managed SQLite runtime contract against the live CLI capabilities.

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
readonly verifier="${script_dir}/verify-managed-sqlite-runtime.py"

[[ -f "${verifier}" ]] || die "missing managed SQLite runtime verifier at ${verifier}"

capabilities_output="$(
    cd "${repo_root}" &&
        ./gradlew -q :cli:run --args='capabilities --output json' --no-daemon --console=plain
)"
if ! verifier_output="$(
    printf '%s\n' "${capabilities_output}" | python3 "${verifier}" 2>&1
)"; then
    printf '%s\n' "${capabilities_output}"
    printf '%s\n' "${verifier_output}" >&2
    exit 1
fi
printf '%s\n' "${verifier_output}"
