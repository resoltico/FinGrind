#!/usr/bin/env bash
# Aggregate the release-blocking GitHub workflow verification helpers.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf 'error: %s\n' \
        "release-check-verification-support.sh is a library and must be sourced by another script." >&2
    exit 1
fi

readonly release_check_verification_support_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly release_check_gh_api_support="${release_check_verification_support_dir}/release-check-gh-api-support.sh"
readonly release_check_workflow_support="${release_check_verification_support_dir}/release-check-workflow-support.sh"

[[ -f "${release_check_gh_api_support}" ]] || {
    printf 'error: %s\n' "missing release-check GitHub API support at ${release_check_gh_api_support}" >&2
    exit 1
}
[[ -f "${release_check_workflow_support}" ]] || {
    printf 'error: %s\n' "missing release-check workflow support at ${release_check_workflow_support}" >&2
    exit 1
}

# shellcheck source=/dev/null
source "${release_check_gh_api_support}"
# shellcheck source=/dev/null
source "${release_check_workflow_support}"
