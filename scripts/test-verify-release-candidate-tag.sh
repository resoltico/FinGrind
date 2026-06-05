#!/usr/bin/env bash
# Guard the publication-candidate verifier so tag-driven release workflows cannot skip the
# committed release-blocking CI contract.

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
readonly verifier="${repo_root}/scripts/verify-release-candidate-tag.sh"
readonly release_check_support="${repo_root}/scripts/release-check-support.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"
readonly release_workflow="${repo_root}/.github/workflows/release.yml"

[[ -x "${verifier}" ]] || die "missing executable release-candidate verifier at ${verifier}"
[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_protocol}" ]] || die "missing release protocol at ${release_protocol}"
[[ -f "${release_workflow}" ]] || die "missing release workflow at ${release_workflow}"

# shellcheck source=/dev/null
source "${release_check_support}"
readonly expected_check_name="$(fingrind_required_ci_check_name)"

grep -Fq 'scripts/test-verify-release-candidate-tag.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the release-candidate verifier regression"
grep -Fq './scripts/verify-release-candidate-tag.sh' "${release_protocol}" || die \
    "release protocol no longer requires the release-candidate verifier"
grep -Fq 'Verify release candidate tag handoff' "${release_workflow}" || die \
    "release workflow no longer declares the release-candidate handoff gate"
grep -Fq 'verify-release-candidate-tag.sh' "${release_workflow}" || die \
    "release workflow no longer invokes the release-candidate verifier"
grep -Fq 'build-staging-container:' "${release_workflow}" || die \
    "release workflow no longer carries the container publication job after the release handoff"
if [[ -e "${repo_root}/.github/workflows/container.yml" ]]; then
    die "retired standalone container workflow resurfaced after publication unification"
fi
grep -Fq 'release-check-support.sh' "${verifier}" || die \
    "release-candidate verifier no longer sources the canonical release-check owner"
grep -Fq "${expected_check_name}" "${release_protocol}" || die \
    "release protocol no longer documents the canonical Gate release check"
if grep -Fq 'gh workflow run container.yml' "${release_protocol}"; then
    die "release protocol still documents rerunning a retired standalone container workflow"
fi
if grep -Fq 'Contributor devcontainer' "${verifier}"; then
    die "release-candidate verifier reintroduced the obsolete contributor-devcontainer release check"
fi

printf 'verify-release-candidate-tag regression: success\n'
