#!/usr/bin/env bash
# Verify that the repo-owned JaCoCo snapshot contract resolves to one exact published artifact set.

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
readonly python_contract_verifier="${script_dir}/verify_jacoco_snapshot_contract.py"
readonly gradlew="${repo_root}/gradlew"

require_command python3
[[ -f "${python_contract_verifier}" ]] || die \
    "missing JaCoCo snapshot contract verifier at ${python_contract_verifier}"
[[ -x "${gradlew}" ]] || die "missing executable Gradle wrapper at ${gradlew}"

python3 "${python_contract_verifier}" --repo-root "${repo_root}"
"${gradlew}" prepareJacocoSnapshotArtifacts --console=plain --no-daemon >/dev/null
