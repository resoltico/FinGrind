#!/usr/bin/env bash
# Shared helpers for converging a GitHub release draft onto the expected staged asset state.

readonly publish_release_support_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./release-tag-support.sh
source "${publish_release_support_dir}/release-tag-support.sh"

publish_release_die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

publish_release_compute_sha256() {
    local asset_path=$1
    python3 - "${asset_path}" <<'PY'
from hashlib import sha256
from pathlib import Path
import sys

target = Path(sys.argv[1])
digest = sha256()
with target.open("rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
        digest.update(chunk)
print(digest.hexdigest())
PY
}

publish_release_resolve_repository_slug() {
    if [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
        printf '%s\n' "${GITHUB_REPOSITORY}"
        return
    fi
    gh repo view --json nameWithOwner --jq '.nameWithOwner' 2>/dev/null
}

publish_release_view_json() {
    gh api --paginate "/repos/${PUBLISH_RELEASE_REPO_FULL_NAME}/releases?per_page=100" 2>/dev/null |
        jq -sc --arg tag "${PUBLISH_RELEASE_TAG_NAME}" '
            [
                .[]
                | if type == "array" then .[] else . end
                | select(.tag_name == $tag)
            ] as $matches
            | if ($matches | length) == 0 then
                empty
              elif ($matches | length) == 1 then
                $matches[0]
              else
                error("multiple releases matched tag " + $tag)
              end
        ' || return 1
}

publish_release_exists() {
    local release_json

    release_json="$(publish_release_view_json)" || return 1
    [[ -n "${release_json}" ]]
}

publish_release_api_field() {
    local jq_expression=$1
    local release_json

    release_json="$(publish_release_view_json)" || return 1
    [[ -n "${release_json}" ]] || return 1
    printf '%s' "${release_json}" | jq -r "${jq_expression}"
}

publish_release_asset_digest() {
    local asset_name=$1
    publish_release_api_field ".assets[]? | select(.name == \"${asset_name}\") | .digest // empty"
}

publish_release_wait_for_visibility() {
    local retries="${FINGRIND_RELEASE_DRAFT_VISIBILITY_RETRIES:-10}"
    local delay_seconds="${FINGRIND_RELEASE_DRAFT_VISIBILITY_DELAY_SECONDS:-1}"
    local release_json=''

    while (( retries > 0 )); do
        release_json="$(publish_release_view_json || true)"
        if [[ -n "${release_json}" ]]; then
            return
        fi
        retries=$((retries - 1))
        (( retries > 0 )) || break
        if [[ "${delay_seconds}" != "0" && "${delay_seconds}" != "0.0" ]]; then
            sleep "${delay_seconds}"
        fi
    done

    publish_release_die \
        "release ${PUBLISH_RELEASE_TAG_NAME} was not visible through the GitHub Releases API after creation"
}

publish_release_resolve_api_field_or_die() {
    local jq_expression=$1
    local failure_message=$2
    local resolved_value

    resolved_value="$(publish_release_api_field "${jq_expression}")" || publish_release_die \
        "${failure_message}"
    printf '%s\n' "${resolved_value}"
}

publish_release_resolve_asset_digest_or_die() {
    local asset_name=$1
    local resolved_value

    resolved_value="$(publish_release_asset_digest "${asset_name}")" || publish_release_die \
        "failed to inspect release ${PUBLISH_RELEASE_TAG_NAME} asset ${asset_name}"
    printf '%s\n' "${resolved_value}"
}

# shellcheck source=./publish-github-release-assets-support.sh
source "${publish_release_support_dir}/publish-github-release-assets-support.sh"

publish_release_create_draft_release() {
    gh release create "${PUBLISH_RELEASE_TAG_NAME}" \
        --title "${PUBLISH_RELEASE_TAG_NAME}" \
        --draft \
        --generate-notes \
        --latest=false \
        --verify-tag >/dev/null
    publish_release_wait_for_visibility
}

publish_release_patch_state() {
    local draft_state=$1
    local make_latest=$2
    local release_id
    local payload

    release_id="$(publish_release_resolve_api_field_or_die \
        '.id' \
        "failed to resolve release id for ${PUBLISH_RELEASE_TAG_NAME}")"
    [[ -n "${release_id}" ]] || publish_release_die \
        "failed to resolve release id for ${PUBLISH_RELEASE_TAG_NAME}"
    payload="$(
        python3 - "${PUBLISH_RELEASE_TAG_NAME}" "${draft_state}" "${make_latest}" <<'PY'
import json
import sys

tag_name, draft_state, make_latest = sys.argv[1:4]
print(
    json.dumps(
        {
            "tag_name": tag_name,
            "name": tag_name,
            "draft": draft_state == "true",
            "prerelease": False,
            "make_latest": make_latest,
        }
    )
)
PY
    )"
    printf '%s' "${payload}" | gh api \
        --method PATCH \
        "/repos/${PUBLISH_RELEASE_REPO_FULL_NAME}/releases/${release_id}" \
        --input - >/dev/null
}

publish_release_prepare_draft_release() {
    local release_is_draft

    if ! publish_release_exists; then
        publish_release_create_draft_release
        return
    fi

    release_is_draft="$(publish_release_resolve_api_field_or_die \
        '.draft' \
        "failed to resolve release draft state for ${PUBLISH_RELEASE_TAG_NAME}")"
    [[ "${release_is_draft}" == "true" || "${release_is_draft}" == "false" ]] || publish_release_die \
        "release draft state must resolve to true or false"
    if [[ "${release_is_draft}" == "true" ]]; then
        return
    fi

    if publish_release_assets_match_expected; then
        PUBLISH_RELEASE_PUBLIC_NOOP=true
        return
    fi

    publish_release_die \
        "release ${PUBLISH_RELEASE_TAG_NAME} is already public and immutable; publish a new version tag instead of replacing public assets"
}

publish_release_stage_draft_assets() {
    local asset_path
    local asset_name
    local expected_asset_names=()

    while IFS= read -r asset_name; do
        expected_asset_names+=("${asset_name}")
    done < <(publish_release_expected_asset_names_from_paths "${PUBLISH_RELEASE_ASSET_PATHS[@]}")
    publish_release_remove_unexpected_draft_assets "${expected_asset_names[@]}"

    for asset_path in "${PUBLISH_RELEASE_ASSET_PATHS[@]}"; do
        publish_release_converge_asset "${asset_path}"
    done
    publish_release_require_exact_asset_inventory "${expected_asset_names[@]}"
    publish_release_patch_state true false
}

publish_release_finalize_public_release() {
    local mark_latest=$1
    shift
    local expected_asset_names=("$@")
    local release_is_draft

    [[ "${mark_latest}" == "true" || "${mark_latest}" == "false" ]] || publish_release_die \
        "final release latest policy must be true or false"
    (( ${#expected_asset_names[@]} > 0 )) || publish_release_die \
        "final release requires one nonempty expected asset-name set"
    publish_release_exists || publish_release_die \
        "cannot finalize missing release ${PUBLISH_RELEASE_TAG_NAME}"
    release_is_draft="$(publish_release_resolve_api_field_or_die \
        '.draft' \
        "failed to resolve release draft state for ${PUBLISH_RELEASE_TAG_NAME}")"
    [[ "${release_is_draft}" == "true" || "${release_is_draft}" == "false" ]] || publish_release_die \
        "release draft state must resolve to true or false"
    if [[ "${release_is_draft}" == "false" ]]; then
        publish_release_require_exact_asset_inventory "${expected_asset_names[@]}"
        printf 'GitHub release already public for %s (latest=%s)\n' \
            "${PUBLISH_RELEASE_TAG_NAME}" "${mark_latest}"
        return
    fi
    publish_release_require_exact_asset_inventory "${expected_asset_names[@]}"
    publish_release_patch_state false "${mark_latest}"
    printf 'GitHub release finalized for %s (latest=%s)\n' \
        "${PUBLISH_RELEASE_TAG_NAME}" "${mark_latest}"
}

publish_release_main() {
    local tag_name="${RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
    if [[ -z "${tag_name}" && $# -gt 0 && "$1" == v* ]]; then
        tag_name="$1"
        shift
    fi

    readonly PUBLISH_RELEASE_TAG_NAME="${tag_name}"
    readonly PUBLISH_RELEASE_ASSET_PATHS=("$@")
    PUBLISH_RELEASE_PUBLIC_NOOP=false
    [[ -n "${GH_TOKEN:-}" ]] || publish_release_die "GH_TOKEN is required"
    [[ -n "${PUBLISH_RELEASE_TAG_NAME}" ]] || publish_release_die "release tag is required"
    release_tag_is_stable "${PUBLISH_RELEASE_TAG_NAME}" || publish_release_die \
        "release tag must match stable vX.Y.Z"
    readonly PUBLISH_RELEASE_REPO_FULL_NAME="$(
        publish_release_resolve_repository_slug
    )" || publish_release_die "failed to resolve GitHub repository slug"
    [[ -n "${PUBLISH_RELEASE_REPO_FULL_NAME}" ]] || publish_release_die \
        "failed to resolve GitHub repository slug"

    publish_release_prepare_draft_release
    if [[ "${PUBLISH_RELEASE_PUBLIC_NOOP}" == "true" ]]; then
        printf 'GitHub release already public and asset-complete for %s\n' \
            "${PUBLISH_RELEASE_TAG_NAME}"
        return
    fi
    publish_release_stage_draft_assets
    printf 'GitHub release draft staged for %s\n' "${PUBLISH_RELEASE_TAG_NAME}"
}
