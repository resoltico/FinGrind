#!/usr/bin/env bash
# Verify that the merged release commit is exactly the current remote default-branch head and wait
# for the release-blocking CI checks on that commit before any release tag is created.

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
# The post-merge CI fan-out starts secondary release-blocking jobs only after the main Check job
# completes, so a full healthy handoff can legitimately take well past 15 minutes and can exceed
# forty minutes when Windows smoke starts late.
readonly timeout_seconds="${FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS:-3000}"

[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${verification_support}" ]] || die "missing release-check verification helper at ${verification_support}"
# shellcheck source=/dev/null
source "${release_check_support}"
# shellcheck source=/dev/null
source "${verification_support}"

readonly blocking_checks_csv="$(fingrind_required_ci_checks_csv)"

repo_root="${FINGRIND_RELEASE_WORKTREE:-$(git rev-parse --show-toplevel 2>/dev/null || true)}"
if [[ -z "${repo_root}" ]]; then
    repo_root="${script_repo_root}"
fi
readonly repo_root

cd "${repo_root}"

readonly target_commit_sha="${1:-$(git rev-parse HEAD)}"
readonly local_head_sha="$(git rev-parse HEAD)"

[[ "${target_commit_sha}" == "${local_head_sha}" ]] || die \
    "target commit ${target_commit_sha} does not match checked-out HEAD ${local_head_sha}"

readonly repo_full_name="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
readonly default_branch="$(gh repo view --json defaultBranchRef --jq '.defaultBranchRef.name')"

[[ -n "${repo_full_name}" ]] || die "failed to resolve repository name"
[[ -n "${default_branch}" ]] || die "failed to resolve default branch"

git fetch --no-tags origin "${default_branch}" >/dev/null 2>&1 || die \
    "failed to fetch origin/${default_branch}"

readonly remote_default_ref="refs/remotes/origin/${default_branch}"
git show-ref --verify --quiet "${remote_default_ref}" || die \
    "missing ${remote_default_ref} after fetch"

readonly remote_default_sha="$(git rev-parse "${remote_default_ref}")"
[[ "${local_head_sha}" == "${remote_default_sha}" ]] || die \
    "checked-out HEAD ${local_head_sha} does not match origin/${default_branch} ${remote_default_sha}; pull the merged release commit before tagging"

fingrind_wait_for_release_blocking_checks \
    "${repo_full_name}" \
    "${target_commit_sha}" \
    "${blocking_checks_csv}" \
    "${poll_interval_seconds}" \
    "${timeout_seconds}" \
    "release merge handoff on origin/${default_branch}" \
    "release merge handoff" \
    "release merge handoff"
