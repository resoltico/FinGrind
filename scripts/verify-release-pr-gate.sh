#!/usr/bin/env bash
# Verify that a release PR head commit has the canonical release-blocking Gate check before merge.

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
readonly script_repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly release_check_support="${script_repo_root}/scripts/release-check-support.sh"
readonly verification_support="${script_repo_root}/scripts/release-check-verification-support.sh"
readonly poll_interval_seconds="${FINGRIND_RELEASE_CHECK_POLL_INTERVAL_SECONDS:-10}"

[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${verification_support}" ]] || die "missing release-check verification helper at ${verification_support}"

# shellcheck source=/dev/null
source "${release_check_support}"
# shellcheck source=/dev/null
source "${verification_support}"

readonly timeout_seconds="$(fingrind_release_check_timeout_seconds)"

readonly pr_number="${1:-}"
[[ -n "${pr_number}" ]] || die "usage: ./scripts/verify-release-pr-gate.sh <pull-request-number>"
[[ "${pr_number}" =~ ^[0-9]+$ ]] || die "pull request number must be numeric, got '${pr_number}'"

readonly repo_full_name="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
[[ -n "${repo_full_name}" ]] || die "failed to resolve repository name"

readonly pr_payload="$(
    gh pr view \
        "${pr_number}" \
        --repo "${repo_full_name}" \
        --json number,state,headRefName,headRefOid,url
)"
readonly pr_state="$(printf '%s' "${pr_payload}" | jq -r '.state')"
readonly pr_head_ref_name="$(printf '%s' "${pr_payload}" | jq -r '.headRefName')"
readonly pr_head_ref_oid="$(printf '%s' "${pr_payload}" | jq -r '.headRefOid')"
readonly pr_url="$(printf '%s' "${pr_payload}" | jq -r '.url')"

[[ "${pr_state}" == "OPEN" ]] || die \
    "pull request #${pr_number} is ${pr_state}, expected OPEN before release merge verification"
[[ -n "${pr_head_ref_name}" && "${pr_head_ref_name}" != "null" ]] || die \
    "failed to resolve headRefName for pull request #${pr_number}"
[[ -n "${pr_head_ref_oid}" && "${pr_head_ref_oid}" != "null" ]] || die \
    "failed to resolve headRefOid for pull request #${pr_number}"
[[ -n "${pr_url}" && "${pr_url}" != "null" ]] || die \
    "failed to resolve URL for pull request #${pr_number}"

readonly blocking_checks_csv="$(fingrind_required_ci_checks_csv)"
fingrind_wait_for_release_blocking_checks \
    "${repo_full_name}" \
    "${pr_head_ref_oid}" \
    "${blocking_checks_csv}" \
    "${poll_interval_seconds}" \
    "${timeout_seconds}" \
    "release PR #${pr_number} (${pr_url})" \
    "release PR #${pr_number} (${pr_head_ref_name})" \
    "release PR #${pr_number} (${pr_url})"
