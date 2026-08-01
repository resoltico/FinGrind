#!/usr/bin/env bash
# Reconcile FinGrind's release-tag control plane only when existing policy is canonical.

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
readonly release_check_gh_api_support="${repo_root}/scripts/release-check-gh-api-support.sh"
readonly release_tag_ruleset_support="${repo_root}/scripts/release-tag-ruleset-support.sh"
readonly release_tag_ruleset_contract="${repo_root}/scripts/release_tag_ruleset_contract.py"
readonly repository_settings_verifier="${repo_root}/scripts/verify-release-repo-settings.sh"

[[ -f "${release_check_gh_api_support}" ]] || die \
    "missing release-check GitHub API support helper at ${release_check_gh_api_support}"
[[ -f "${release_tag_ruleset_support}" ]] || die \
    "missing release-tag ruleset support helper at ${release_tag_ruleset_support}"
[[ -f "${release_tag_ruleset_contract}" ]] || die \
    "missing release-tag ruleset contract at ${release_tag_ruleset_contract}"
[[ -x "${repository_settings_verifier}" ]] || die \
    "missing executable repository-settings verifier at ${repository_settings_verifier}"

# shellcheck source=/dev/null
source "${release_check_gh_api_support}"
# shellcheck source=/dev/null
source "${release_tag_ruleset_support}"

repo_view_json="$(gh repo view --json nameWithOwner 2>/dev/null)" || die \
    "failed to read the GitHub repository name from gh repo view"
readonly repo_view_json

repo_full_name="$(
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
readonly repo_full_name

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

tag_ruleset_details_json="$(
    fingrind_release_tag_ruleset_details_json "${repo_full_name}"
)" || die "could not read the tag-ruleset inventory"
readonly tag_ruleset_details_json

configuration_plan_json="$(
    printf '%s\n' "${tag_ruleset_details_json}" | \
        python3 "${release_tag_ruleset_contract}" \
            --release-owner-id "${release_owner_id}" \
            --configuration-plan \
            2>&1
)" || die "release tag-ruleset configuration is not safely reconcilable: ${configuration_plan_json}"
readonly configuration_plan_json

configuration_actions="$(
    FINGRIND_RELEASE_TAG_RULESET_CONFIGURATION_PLAN="${configuration_plan_json}" \
        python3 - <<'PY'
import json
import os

payload = json.loads(os.environ["FINGRIND_RELEASE_TAG_RULESET_CONFIGURATION_PLAN"])
actions = payload.get("create")
if not isinstance(actions, list) or any(action not in {"creation", "immutability"} for action in actions):
    raise SystemExit(1)
if len(actions) != len(set(actions)):
    raise SystemExit(1)
for action in actions:
    print(action)
PY
)" || die "release tag-ruleset configuration plan is malformed"
readonly configuration_actions

if [[ -n "${configuration_actions}" ]]; then
    while IFS= read -r configuration_action; do
        case "${configuration_action}" in
            creation)
                request_json="$(
                    python3 "${release_tag_ruleset_contract}" \
                        --release-owner-id "${release_owner_id}" \
                        --print-creation-request
                )"
                ;;
            immutability)
                request_json="$(
                    python3 "${release_tag_ruleset_contract}" \
                        --release-owner-id "${release_owner_id}" \
                        --print-immutability-request
                )"
                ;;
            *)
                die "release tag-ruleset configuration plan named an unsupported action"
                ;;
        esac

        configuration_result="$(
            printf '%s\n' "${request_json}" | \
                fingrind_release_github_api_json \
                    "create ${configuration_action} release tag ruleset for ${repo_full_name}" \
                    --method POST \
                    --input - \
                    "repos/${repo_full_name}/rulesets"
        )"
        configuration_error="$(fingrind_release_payload_error_message "${configuration_result}")"
        if [[ "${configuration_error}" != "null" ]]; then
            die "${configuration_error}"
        fi
        printf 'Created %s release tag ruleset.\n' "${configuration_action}"
    done <<< "${configuration_actions}"
fi

"${repository_settings_verifier}"
