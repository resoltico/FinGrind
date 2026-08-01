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
    local release_version="${tag_name#v}"
    local payload_root="${FINGRIND_RELEASE_PAYLOAD_ROOT:-$(cd -P -- "${script_dir}/.." && pwd)}"
    local release_plan_json
    local expected_asset_names=()
    local asset_name

    [[ -n "${GH_TOKEN:-}" ]] || publish_release_die "GH_TOKEN is required"
    [[ -n "${PUBLISH_RELEASE_TAG_NAME}" ]] || publish_release_die "release tag is required"
    release_tag_is_stable "${PUBLISH_RELEASE_TAG_NAME}" || publish_release_die \
        "release tag must match stable vX.Y.Z"
    [[ -d "${payload_root}" ]] || publish_release_die \
        "release payload root does not exist: ${payload_root}"
    [[ -n "${PUBLISH_RELEASE_REPO_FULL_NAME}" ]] || publish_release_die \
        "failed to resolve GitHub repository slug"

    release_plan_json="$(
        python3 "${script_dir}/read-release-publication-plan.py" \
            --version "${release_version}" \
            --repository-root "${payload_root}"
    )" || publish_release_die \
        "could not resolve the canonical release asset set for ${PUBLISH_RELEASE_TAG_NAME}"
    while IFS= read -r asset_name; do
        [[ -n "${asset_name}" ]] || publish_release_die \
            "canonical release asset set for ${PUBLISH_RELEASE_TAG_NAME} contained one empty name"
        expected_asset_names+=("${asset_name}")
    done < <(printf '%s' "${release_plan_json}" | jq -r '.releaseAssetNames[]')
    (( ${#expected_asset_names[@]} > 0 )) || publish_release_die \
        "canonical release asset set for ${PUBLISH_RELEASE_TAG_NAME} was empty"

    publish_release_finalize_public_release "${mark_latest}" "${expected_asset_names[@]}"
}

finalize_github_release_main "$@"
