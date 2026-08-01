#!/usr/bin/env bash
# Verify that a release candidate is safe before and after tag creation. Pre-tag admission proves
# the typed candidate still names the current green default-branch head and that no local or remote
# reference would be reused. Initial publication then proves the pushed reference names that same
# commit. A tag-triggered workflow can begin only after repository-wide publication serialization,
# so it admits a historical tag by durable default-branch ancestry and exact-commit CI rather than
# incorrectly trying to reconstruct the operator's contemporaneous default-branch-head proof.
# Workflow-dispatch reruns use the same durable admission fact.

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
readonly release_tag_support="${script_repo_root}/scripts/release-tag-support.sh"
readonly tag_name="${1:-${RELEASE_TAG:-${GITHUB_REF_NAME:-}}}"
readonly verifier_mode_input="${FINGRIND_RELEASE_TAG_VERIFIER_MODE:-}"

[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${verification_support}" ]] || die "missing release-check verification helper at ${verification_support}"
[[ -f "${release_tag_support}" ]] || die "missing release-tag support helper at ${release_tag_support}"
# shellcheck source=/dev/null
source "${release_check_support}"
# shellcheck source=/dev/null
source "${verification_support}"
# shellcheck source=/dev/null
source "${release_tag_support}"

resolve_verifier_mode() {
    case "${verifier_mode_input}" in
        "")
            if [[ "${GITHUB_EVENT_NAME:-}" == "workflow_dispatch" ]]; then
                printf '%s\n' "rerun"
            else
                printf '%s\n' "initial"
            fi
            ;;
        pre-tag|initial|tag-publication|rerun)
            printf '%s\n' "${verifier_mode_input}"
            ;;
        *)
            die \
                "unsupported FINGRIND_RELEASE_TAG_VERIFIER_MODE '${verifier_mode_input}'; expected pre-tag, initial, tag-publication, or rerun"
            ;;
    esac
}

readonly blocking_checks_csv="$(fingrind_required_ci_checks_csv)"
readonly poll_interval_seconds="${FINGRIND_RELEASE_CHECK_POLL_INTERVAL_SECONDS:-10}"
readonly timeout_seconds="${FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS:-2400}"
readonly verifier_mode="$(resolve_verifier_mode)"

[[ -n "${tag_name}" ]] || die "release tag is required"
release_tag_is_stable "${tag_name}" || die "release tag must match stable vX.Y.Z"

repo_root="${FINGRIND_RELEASE_WORKTREE:-$(git rev-parse --show-toplevel 2>/dev/null || true)}"
if [[ -z "${repo_root}" ]]; then
    repo_root="${script_repo_root}"
fi
readonly repo_root

cd "${repo_root}"

readonly expected_version="$(release_tag_version "${tag_name}")"
readonly gradle_version="$(
    awk -F= '
        $1 == "version" {
            print $2
            exit
        }
    ' "${repo_root}/gradle.properties"
)"

[[ -n "${gradle_version}" ]] || die "missing version in gradle.properties"
[[ "${expected_version}" == "${gradle_version}" ]] || die \
    "tag version ${expected_version} does not match gradle.properties version ${gradle_version}"

readonly repo_full_name="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
readonly default_branch="$(gh repo view --json defaultBranchRef --jq '.defaultBranchRef.name')"
readonly local_commit_sha="$(git rev-parse HEAD)"

[[ -n "${repo_full_name}" ]] || die "failed to resolve repository name"
[[ -n "${default_branch}" ]] || die "failed to resolve default branch"

resolve_remote_tag_commit() {
    local tag_ref_api="/repos/${repo_full_name}/git/ref/tags/${tag_name}"
    local remote_tag_object_type
    local remote_tag_object_sha

    remote_tag_object_type="$(gh api "${tag_ref_api}" --jq '.object.type')"
    remote_tag_object_sha="$(gh api "${tag_ref_api}" --jq '.object.sha')"

    case "${remote_tag_object_type}" in
        commit)
            printf '%s\n' "${remote_tag_object_sha}"
            ;;
        tag)
            gh api "/repos/${repo_full_name}/git/tags/${remote_tag_object_sha}" --jq '.object.sha'
            ;;
        *)
            die "unsupported remote tag object type '${remote_tag_object_type}' for ${tag_name}"
            ;;
    esac
}

resolve_remote_default_branch_commit() {
    local remote_default_branch_output
    local remote_default_branch_sha
    local remote_default_branch_ref

    remote_default_branch_output="$(
        git ls-remote --exit-code --refs origin "refs/heads/${default_branch}"
    )" || die "failed to resolve the current origin/${default_branch} head"

    [[ "${remote_default_branch_output}" != *$'\n'* ]] || die \
        "origin returned multiple refs while resolving refs/heads/${default_branch}"

    IFS=$'\t' read -r remote_default_branch_sha remote_default_branch_ref <<< \
        "${remote_default_branch_output}"
    [[ "${remote_default_branch_ref}" == "refs/heads/${default_branch}" ]] || die \
        "origin returned an unexpected ref while resolving refs/heads/${default_branch}"
    [[ "${remote_default_branch_sha}" =~ ^[0-9a-f]{40,64}$ ]] || die \
        "origin returned an invalid commit identifier for refs/heads/${default_branch}"

    printf '%s\n' "${remote_default_branch_sha}"
}

fetch_remote_default_branch_commit() {
    git fetch --no-tags --no-write-fetch-head origin "${remote_default_sha}" >/dev/null 2>&1 || die \
        "failed to fetch the current origin/${default_branch} head"
    git cat-file -e "${remote_default_sha}^{commit}" 2>/dev/null || die \
        "origin/${default_branch} head ${remote_default_sha} is not a commit"
}

readonly remote_default_sha="$(resolve_remote_default_branch_commit)"
tag_commit_sha=""

case "${verifier_mode}" in
    pre-tag)
        if git show-ref --verify --quiet "refs/tags/${tag_name}"; then
            die "pre-tag release candidate ${tag_name} already exists locally"
        fi
        if git ls-remote --exit-code --refs origin "refs/tags/${tag_name}" >/dev/null 2>&1; then
            die "pre-tag release candidate ${tag_name} already exists on origin"
        else
            remote_tag_lookup_status=$?
            [[ ${remote_tag_lookup_status} -eq 2 ]] || die \
                "failed to determine whether pre-tag release candidate ${tag_name} already exists on origin"
        fi
        tag_commit_sha="${local_commit_sha}"
        [[ "${tag_commit_sha}" == "${remote_default_sha}" ]] || die \
            "pre-tag release candidate ${tag_name} checked-out HEAD ${tag_commit_sha} does not match origin/${default_branch} head ${remote_default_sha}"
        ;;
    initial)
        tag_commit_sha="$(resolve_remote_tag_commit)"
        [[ "${local_commit_sha}" == "${tag_commit_sha}" ]] || die \
            "checked-out commit ${local_commit_sha} does not match remote tag ${tag_name} commit ${tag_commit_sha}"
        [[ "${tag_commit_sha}" == "${remote_default_sha}" ]] || die \
            "initial release tag ${tag_name} commit ${tag_commit_sha} does not match origin/${default_branch} head ${remote_default_sha}"
        ;;
    tag-publication|rerun)
        tag_commit_sha="$(resolve_remote_tag_commit)"
        [[ "${local_commit_sha}" == "${tag_commit_sha}" ]] || die \
            "checked-out commit ${local_commit_sha} does not match remote tag ${tag_name} commit ${tag_commit_sha}"
        fetch_remote_default_branch_commit
        git merge-base --is-ancestor "${tag_commit_sha}" "${remote_default_sha}" || die \
            "${verifier_mode} release tag ${tag_name} commit ${tag_commit_sha} is not reachable from the current origin/${default_branch} head ${remote_default_sha}"
        ;;
esac
readonly tag_commit_sha

fingrind_wait_for_release_blocking_checks \
    "${repo_full_name}" \
    "${tag_commit_sha}" \
    "${blocking_checks_csv}" \
    "${poll_interval_seconds}" \
    "${timeout_seconds}" \
    "${verifier_mode} release candidate ${tag_name} on origin/${default_branch}" \
    "${verifier_mode} release candidate ${tag_name}" \
    "${verifier_mode} release candidate ${tag_name}"
