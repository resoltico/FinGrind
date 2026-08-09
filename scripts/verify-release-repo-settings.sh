#!/usr/bin/env bash
# Verify the GitHub repository settings that the FinGrind public-release path depends on.

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
readonly release_check_support="${repo_root}/scripts/release-check-support.sh"
readonly release_check_gh_api_support="${repo_root}/scripts/release-check-gh-api-support.sh"
readonly release_tag_ruleset_support="${repo_root}/scripts/release-tag-ruleset-support.sh"
readonly release_tag_ruleset_contract="${repo_root}/scripts/release_tag_ruleset_contract.py"
readonly security_policy_verifier="${repo_root}/scripts/verify-security-policy-surface.sh"
readonly expected_default_branch="${1:-${FINGRIND_RELEASE_DEFAULT_BRANCH:-main}}"

[[ -f "${release_check_support}" ]] || die \
    "missing release-check support helper at ${release_check_support}"
[[ -f "${release_check_gh_api_support}" ]] || die \
    "missing release-check GitHub API support helper at ${release_check_gh_api_support}"
[[ -f "${release_tag_ruleset_support}" ]] || die \
    "missing release-tag ruleset support helper at ${release_tag_ruleset_support}"
[[ -f "${release_tag_ruleset_contract}" ]] || die \
    "missing release-tag ruleset contract at ${release_tag_ruleset_contract}"
[[ -x "${security_policy_verifier}" ]] || die \
    "missing executable security-policy verifier at ${security_policy_verifier}"
[[ -n "${expected_default_branch}" ]] || die "expected default branch must not be blank"

# shellcheck source=/dev/null
source "${release_check_support}"
# shellcheck source=/dev/null
source "${release_check_gh_api_support}"
# shellcheck source=/dev/null
source "${release_tag_ruleset_support}"

readonly required_check_name="$(fingrind_required_ci_check_name)"

repo_view_json="$(
    gh repo view --json nameWithOwner,defaultBranchRef,deleteBranchOnMerge 2>/dev/null
)" || die "failed to read GitHub repository settings from gh repo view"

readonly repo_view_json
readonly repo_full_name="$(
    FINGRIND_RELEASE_REPO_VIEW_JSON="${repo_view_json}" \
        python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["FINGRIND_RELEASE_REPO_VIEW_JSON"])
repo_name = payload.get("nameWithOwner")
if not isinstance(repo_name, str) or not repo_name:
    raise SystemExit(1)
print(repo_name)
PY
)" || die "gh repo view did not return nameWithOwner"

security_policy_output="$(
    "${security_policy_verifier}" "${repo_full_name}" 2>&1
)" || die "release security-policy verification failed: ${security_policy_output}"
readonly security_policy_output

repository_metadata_json="$(
    fingrind_release_github_api_json \
        "repository metadata for ${repo_full_name}" \
        "repos/${repo_full_name}"
)"
readonly repository_metadata_json

repository_metadata_error="$(fingrind_release_payload_error_message "${repository_metadata_json}")"
if [[ "${repository_metadata_error}" != "null" ]]; then
    die "${repository_metadata_error}"
fi

release_owner_id="$(
    FINGRIND_RELEASE_REPOSITORY_METADATA_JSON="${repository_metadata_json}" \
        python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["FINGRIND_RELEASE_REPOSITORY_METADATA_JSON"])
owner = payload.get("owner")
if not isinstance(owner, dict) or owner.get("type") != "User":
    raise SystemExit(1)
owner_id = owner.get("id")
if isinstance(owner_id, bool) or not isinstance(owner_id, int) or owner_id <= 0:
    raise SystemExit(1)
print(owner_id)
PY
)" || die "repository metadata must expose one positive numeric User owner ID"
readonly release_owner_id

branch_protection_json="$(
    fingrind_release_github_api_json \
        "branch protection for ${repo_full_name}:${expected_default_branch}" \
        "repos/${repo_full_name}/branches/${expected_default_branch}/protection"
)"
readonly branch_protection_json

branch_protection_error="$(fingrind_release_payload_error_message "${branch_protection_json}")"
if [[ "${branch_protection_error}" != "null" ]]; then
    die "${branch_protection_error}"
fi

self_hosted_runners_json="$(
    fingrind_release_github_api_json \
        "self-hosted runner inventory for ${repo_full_name}" \
        "repos/${repo_full_name}/actions/runners"
)"
readonly self_hosted_runners_json

self_hosted_runners_error="$(fingrind_release_payload_error_message "${self_hosted_runners_json}")"
if [[ "${self_hosted_runners_error}" != "null" ]]; then
    die "${self_hosted_runners_error}"
fi

workflow_permissions_json="$(
    fingrind_release_github_api_json \
        "Actions workflow permissions for ${repo_full_name}" \
        "repos/${repo_full_name}/actions/permissions/workflow"
)"
readonly workflow_permissions_json

workflow_permissions_error="$(fingrind_release_payload_error_message "${workflow_permissions_json}")"
if [[ "${workflow_permissions_error}" != "null" ]]; then
    die "${workflow_permissions_error}"
fi

tag_ruleset_details_json="$(
    fingrind_release_tag_ruleset_details_json "${repo_full_name}"
)" || die "could not read the tag-ruleset inventory"
readonly tag_ruleset_details_json

tag_ruleset_validation_output="$(
    printf '%s\n' "${tag_ruleset_details_json}" | \
        python3 "${release_tag_ruleset_contract}" --release-owner-id "${release_owner_id}" 2>&1
)" || die "release tag ruleset verification failed: ${tag_ruleset_validation_output}"
readonly tag_ruleset_validation_output

validation_output="$(
    FINGRIND_RELEASE_REPO_VIEW_JSON="${repo_view_json}" \
    FINGRIND_RELEASE_BRANCH_PROTECTION_JSON="${branch_protection_json}" \
    FINGRIND_RELEASE_SELF_HOSTED_RUNNERS_JSON="${self_hosted_runners_json}" \
    FINGRIND_RELEASE_WORKFLOW_PERMISSIONS_JSON="${workflow_permissions_json}" \
        FINGRIND_RELEASE_EXPECTED_DEFAULT_BRANCH="${expected_default_branch}" \
        FINGRIND_RELEASE_REQUIRED_CHECK_NAME="${required_check_name}" \
        python3 - <<'PY'
import json
import os
import sys

repo_view = json.loads(os.environ["FINGRIND_RELEASE_REPO_VIEW_JSON"])
branch_protection = json.loads(os.environ["FINGRIND_RELEASE_BRANCH_PROTECTION_JSON"])
self_hosted_runners = json.loads(os.environ["FINGRIND_RELEASE_SELF_HOSTED_RUNNERS_JSON"])
workflow_permissions = json.loads(os.environ["FINGRIND_RELEASE_WORKFLOW_PERMISSIONS_JSON"])
expected_default_branch = os.environ["FINGRIND_RELEASE_EXPECTED_DEFAULT_BRANCH"]
required_check_name = os.environ["FINGRIND_RELEASE_REQUIRED_CHECK_NAME"]

errors = []

repo_name = repo_view.get("nameWithOwner")
default_branch_ref = repo_view.get("defaultBranchRef")
default_branch = default_branch_ref.get("name") if isinstance(default_branch_ref, dict) else None
delete_branch_on_merge = repo_view.get("deleteBranchOnMerge")
if default_branch != expected_default_branch:
    errors.append(
        f"default branch must be '{expected_default_branch}', found {default_branch!r}"
    )
if delete_branch_on_merge is not True:
    errors.append("delete_branch_on_merge must be enabled")

required_status_checks = branch_protection.get("required_status_checks")
if not isinstance(required_status_checks, dict):
    errors.append("branch protection must publish required_status_checks")
    contexts = None
    checks = None
else:
    if required_status_checks.get("strict") is not True:
        errors.append("required_status_checks.strict must be true")
    contexts = required_status_checks.get("contexts")
    if contexts != [required_check_name]:
        errors.append(
            f"required status-check contexts must equal {[required_check_name]!r}, found {contexts!r}"
        )
    checks = required_status_checks.get("checks")
    if isinstance(checks, list):
        check_contexts = [entry.get("context") for entry in checks if isinstance(entry, dict)]
        if check_contexts != [required_check_name]:
            errors.append(
                f"required status-check checks must equal {[required_check_name]!r}, found {check_contexts!r}"
            )

review_requirements = branch_protection.get("required_pull_request_reviews")
if not isinstance(review_requirements, dict):
    errors.append("branch protection must publish required_pull_request_reviews")
else:
    if review_requirements.get("require_code_owner_reviews") is not False:
        errors.append("code-owner review must not be required in the solo-maintainer policy")
    if review_requirements.get("required_approving_review_count") != 0:
        errors.append("required approving review count must equal 0")
    if review_requirements.get("require_last_push_approval") is not False:
        errors.append("require_last_push_approval must remain false")
    if review_requirements.get("dismiss_stale_reviews") is not False:
        errors.append("dismiss_stale_reviews must remain false")

enforce_admins = branch_protection.get("enforce_admins")
admin_enforced = enforce_admins.get("enabled") if isinstance(enforce_admins, dict) else None
if admin_enforced is not True:
    errors.append(
        "administrator enforcement must remain enabled on main branch protection"
    )

runner_count = self_hosted_runners.get("total_count")
runners = self_hosted_runners.get("runners")
if not isinstance(runner_count, int) or runner_count < 0 or not isinstance(runners, list):
    errors.append("self-hosted runner inventory must publish a nonnegative total_count and runners list")
elif runner_count != len(runners):
    errors.append("self-hosted runner inventory total_count must equal the returned runner count")
elif runner_count != 0:
    errors.append("public release repository must not expose self-hosted runners to workflow jobs")

if workflow_permissions.get("default_workflow_permissions") != "read":
    errors.append("Actions default workflow permissions must be read")
if workflow_permissions.get("can_approve_pull_request_reviews") is not False:
    errors.append("Actions workflows must not approve pull-request reviews")

if errors:
    for error in errors:
        print(error, file=sys.stderr)
    raise SystemExit(1)

print(
    "Verified release repository settings for "
    f"{repo_name}: default branch {default_branch}, delete_branch_on_merge=true, "
    f"required check {required_check_name}, pull-request path required without independent approval, "
    "administrator enforcement enabled, "
    "self-hosted runners unavailable, Actions default permissions read"
)
PY
)" || die "release repository settings verification failed"

printf '%s\n' "${validation_output}"
printf '%s\n' "${tag_ruleset_validation_output}"
printf '%s\n' "${security_policy_output}"
