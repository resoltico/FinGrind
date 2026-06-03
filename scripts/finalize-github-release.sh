#!/usr/bin/env bash
# Finalize one staged GitHub release draft into the public published release state.

set -euo pipefail

readonly script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./publish-github-release-support.sh
source "${script_dir}/publish-github-release-support.sh"

finalize_github_release_main() {
    local tag_name="${RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
    if [[ -z "${tag_name}" && $# -gt 0 && "$1" == v* ]]; then
        tag_name="$1"
        shift
    fi

    readonly PUBLISH_RELEASE_TAG_NAME="${tag_name}"
    readonly PUBLISH_RELEASE_ASSET_PATHS=()
    readonly PUBLISH_RELEASE_REPO_FULL_NAME="$(publish_release_resolve_repository_slug)"
    local mark_latest="${FINGRIND_RELEASE_MARK_LATEST:-false}"

    [[ -n "${GH_TOKEN:-}" ]] || publish_release_die "GH_TOKEN is required"
    [[ -n "${PUBLISH_RELEASE_TAG_NAME}" ]] || publish_release_die "release tag is required"
    [[ -n "${PUBLISH_RELEASE_REPO_FULL_NAME}" ]] || publish_release_die \
        "failed to resolve GitHub repository slug"

    publish_release_finalize_public_release "${mark_latest}"
}

finalize_github_release_main "$@"
