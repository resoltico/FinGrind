#!/usr/bin/env bash
# Verify that a tag-targeted publication candidate is safe to publish. Initial publication requires
# the checked-out tag commit to be the current remote default-branch head and to introduce the
# target release version on that default-branch line; reruns against an existing immutable tag
# allow a historical tag commit, but only when that commit remains reachable from the default
# branch and the release-blocking CI checks on that exact commit are already green.

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
readonly tag_name="${1:-${RELEASE_TAG:-${GITHUB_REF_NAME:-}}}"
readonly verifier_mode_input="${FINGRIND_RELEASE_TAG_VERIFIER_MODE:-}"

[[ -f "${release_check_support}" ]] || die "missing release-check support helper at ${release_check_support}"
[[ -f "${verification_support}" ]] || die "missing release-check verification helper at ${verification_support}"
# shellcheck source=/dev/null
source "${release_check_support}"
# shellcheck source=/dev/null
source "${verification_support}"

resolve_verifier_mode() {
    case "${verifier_mode_input}" in
        "")
            if [[ "${GITHUB_EVENT_NAME:-}" == "workflow_dispatch" ]]; then
                printf '%s\n' "rerun"
            else
                printf '%s\n' "initial"
            fi
            ;;
        initial|rerun)
            printf '%s\n' "${verifier_mode_input}"
            ;;
        *)
            die \
                "unsupported FINGRIND_RELEASE_TAG_VERIFIER_MODE '${verifier_mode_input}'; expected initial or rerun"
            ;;
    esac
}

readonly blocking_checks_csv="$(fingrind_required_ci_checks_csv)"
readonly poll_interval_seconds="${FINGRIND_RELEASE_CHECK_POLL_INTERVAL_SECONDS:-10}"
readonly timeout_seconds="${FINGRIND_RELEASE_CHECK_TIMEOUT_SECONDS:-2400}"
readonly verifier_mode="$(resolve_verifier_mode)"

[[ -n "${tag_name}" ]] || die "release tag is required"
[[ "${tag_name}" == v* ]] || die "release tag must start with v"

repo_root="${FINGRIND_RELEASE_WORKTREE:-$(git rev-parse --show-toplevel 2>/dev/null || true)}"
if [[ -z "${repo_root}" ]]; then
    repo_root="${script_repo_root}"
fi
readonly repo_root

cd "${repo_root}"

readonly expected_version="${tag_name#v}"
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

readonly tag_ref_api="/repos/${repo_full_name}/git/ref/tags/${tag_name}"
readonly remote_tag_object_type="$(gh api "${tag_ref_api}" --jq '.object.type')"
readonly remote_tag_object_sha="$(gh api "${tag_ref_api}" --jq '.object.sha')"

case "${remote_tag_object_type}" in
    commit)
        readonly tag_commit_sha="${remote_tag_object_sha}"
        ;;
    tag)
        readonly tag_commit_sha="$(gh api "/repos/${repo_full_name}/git/tags/${remote_tag_object_sha}" --jq '.object.sha')"
        ;;
    *)
        die "unsupported remote tag object type '${remote_tag_object_type}' for ${tag_name}"
        ;;
esac

[[ "${local_commit_sha}" == "${tag_commit_sha}" ]] || die \
    "checked-out commit ${local_commit_sha} does not match remote tag ${tag_name} commit ${tag_commit_sha}"

version_from_gradle_properties_at_revision() {
    local revision="$1"
    local revision_version
    revision_version="$(
        git show "${revision}:gradle.properties" 2>/dev/null | awk -F= '
            $1 == "version" {
                print $2
                exit
            }
        '
    )"
    [[ -n "${revision_version}" ]] || die \
        "missing version in gradle.properties at ${revision}"
    printf '%s\n' "${revision_version}"
}

readonly tag_commit_first_parent_sha="$(git rev-parse --verify "${tag_commit_sha}^1" 2>/dev/null || true)"
[[ -n "${tag_commit_first_parent_sha}" ]] || die \
    "release tag ${tag_name} commit ${tag_commit_sha} has no first parent to prove version introduction"
readonly tag_commit_first_parent_version="$(
    version_from_gradle_properties_at_revision "${tag_commit_sha}^1"
)"
[[ "${tag_commit_first_parent_version}" != "${expected_version}" ]] || die \
    "release tag ${tag_name} commit ${tag_commit_sha} did not introduce version ${expected_version}; first-parent commit ${tag_commit_first_parent_sha} already carried that version"

default_branch_ref="refs/remotes/origin/${default_branch}"
if ! git show-ref --verify --quiet "${default_branch_ref}"; then
    git fetch --no-tags origin \
        "${default_branch}:${default_branch_ref}" >/dev/null 2>&1 || die \
        "failed to fetch origin/${default_branch}"
fi

git show-ref --verify --quiet "${default_branch_ref}" || die \
    "missing ${default_branch_ref} after fetch"

readonly remote_default_sha="$(git rev-parse "${default_branch_ref}")"
case "${verifier_mode}" in
    initial)
        [[ "${tag_commit_sha}" == "${remote_default_sha}" ]] || die \
            "initial release tag ${tag_name} commit ${tag_commit_sha} does not match origin/${default_branch} head ${remote_default_sha}"
        ;;
    rerun)
        git merge-base --is-ancestor "${tag_commit_sha}" "${default_branch_ref}" || die \
            "rerun release tag ${tag_name} commit ${tag_commit_sha} is not reachable from origin/${default_branch}"
        ;;
esac

fingrind_wait_for_release_blocking_checks \
    "${repo_full_name}" \
    "${tag_commit_sha}" \
    "${blocking_checks_csv}" \
    "${poll_interval_seconds}" \
    "${timeout_seconds}" \
    "${verifier_mode} release candidate ${tag_name} on origin/${default_branch}" \
    "${verifier_mode} release candidate ${tag_name}" \
    "${verifier_mode} release candidate ${tag_name}"
