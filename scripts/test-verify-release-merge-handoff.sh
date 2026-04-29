#!/usr/bin/env bash
# Guard the merge-handoff verifier so release-blocking CI checks cannot drift back into prose-only
# release guidance.

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
readonly verifier="${repo_root}/scripts/verify-release-merge-handoff.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"

[[ -x "${verifier}" ]] || die "missing executable merge-handoff verifier at ${verifier}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_protocol}" ]] || die "missing release protocol at ${release_protocol}"

grep -Fq 'scripts/test-verify-release-merge-handoff.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the merge-handoff verifier regression"
grep -Fq './scripts/verify-release-merge-handoff.sh' "${release_protocol}" || die \
    "release protocol no longer requires the merge-handoff verifier"
grep -Fq 'Contributor devcontainer' "${verifier}" || die \
    "merge-handoff verifier no longer treats the contributor devcontainer job as release-blocking"

printf 'verify-release-merge-handoff regression: success\n'
