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
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"
readonly release_workflow="${repo_root}/.github/workflows/release.yml"
readonly container_workflow="${repo_root}/.github/workflows/container.yml"

[[ -x "${verifier}" ]] || die "missing executable release-candidate verifier at ${verifier}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${release_protocol}" ]] || die "missing release protocol at ${release_protocol}"
[[ -f "${release_workflow}" ]] || die "missing release workflow at ${release_workflow}"
[[ -f "${container_workflow}" ]] || die "missing container workflow at ${container_workflow}"

grep -Fq 'scripts/test-verify-release-candidate-tag.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the release-candidate verifier regression"
grep -Fq './scripts/verify-release-candidate-tag.sh' "${release_protocol}" || die \
    "release protocol no longer requires the release-candidate verifier"
grep -Fq './scripts/verify-release-candidate-tag.sh' "${release_workflow}" || die \
    "release workflow no longer validates publication candidates before building assets"
grep -Fq './scripts/verify-release-candidate-tag.sh' "${container_workflow}" || die \
    "container workflow no longer validates publication candidates before publishing images"
grep -Fq 'Contributor devcontainer' "${verifier}" || die \
    "release-candidate verifier no longer treats the contributor devcontainer job as release-blocking"

printf 'verify-release-candidate-tag regression: success\n'
