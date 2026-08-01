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
readonly release_check_support="${repo_root}/scripts/release-check-support.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"

[[ -x "${verifier}" ]] || die "missing executable merge-handoff verifier at ${verifier}"
[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_protocol}" ]] || die "missing release protocol at ${release_protocol}"

# shellcheck source=/dev/null
source "${release_check_support}"
readonly expected_check_name="$(fingrind_required_ci_check_name)"

grep -Fq 'scripts/test-verify-release-merge-handoff.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the merge-handoff verifier regression"
grep -Fq './scripts/verify-release-merge-handoff.sh' "${release_protocol}" || die \
    "release protocol no longer requires the merge-handoff verifier"
grep -Fq 'release-check-support.sh' "${verifier}" || die \
    "merge-handoff verifier no longer sources the canonical release-check owner"
grep -Fq '"+refs/heads/${default_branch}:${remote_default_ref}"' "${verifier}" || die \
    "merge-handoff verifier no longer refreshes the remote default-branch ref before admitting a release head"
grep -Fq "${expected_check_name}" "${release_protocol}" || die \
    "release protocol no longer documents the canonical Gate merge-handoff check"
if grep -Fq 'FINGRIND_RELEASE_BLOCKING_CHECKS' "${verifier}"; then
    die "merge-handoff verifier reintroduced the removed release-blocking-check override"
fi
grep -Fq 'FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS' "${release_protocol}" || die \
    "release protocol no longer documents the merge-handoff verifier timeout override"

readonly timeout_default="$(
    sed -n 's/^readonly timeout_seconds="${FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS:-\([0-9][0-9]*\)}"$/\1/p' \
        "${verifier}"
)"
[[ -n "${timeout_default}" ]] || die "failed to read merge-handoff verifier default timeout"
(( timeout_default >= 2400 )) || die \
    "merge-handoff verifier default timeout regressed below 2400 seconds (${timeout_default})"

printf 'verify-release-merge-handoff regression: success\n'
