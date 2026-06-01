#!/usr/bin/env bash
# Shared helpers for converging a GitHub release onto the expected public publication state.

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
    gh api "/repos/${PUBLISH_RELEASE_REPO_FULL_NAME}/releases/tags/${PUBLISH_RELEASE_TAG_NAME}" \
        2>/dev/null || return 1
}

publish_release_exists() {
    publish_release_view_json >/dev/null 2>&1
}

publish_release_api_field() {
    local jq_expression=$1
    gh api "/repos/${PUBLISH_RELEASE_REPO_FULL_NAME}/releases/tags/${PUBLISH_RELEASE_TAG_NAME}" \
        --jq "${jq_expression}"
}

publish_release_asset_digest() {
    local asset_name=$1
    publish_release_api_field ".assets[]? | select(.name == \"${asset_name}\") | .digest // empty"
}

publish_release_create_draft_release() {
    gh release create "${PUBLISH_RELEASE_TAG_NAME}" \
        --title "${PUBLISH_RELEASE_TAG_NAME}" \
        --draft \
        --generate-notes \
        --latest=false \
        --verify-tag >/dev/null
}

publish_release_patch_state() {
    local draft_state=$1
    local make_latest=$2
    local release_id
    local payload

    release_id="$(publish_release_api_field '.id')"
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

publish_release_ensure_mutable_release() {
    if publish_release_exists; then
        return
    fi
    publish_release_create_draft_release
}

publish_release_delete_asset_if_present() {
    local asset_name=$1
    local observed_digest

    observed_digest="$(publish_release_asset_digest "${asset_name}")"
    [[ -n "${observed_digest}" ]] || return 0
    gh release delete-asset "${PUBLISH_RELEASE_TAG_NAME}" "${asset_name}" --yes >/dev/null 2>&1 || {
        observed_digest="$(publish_release_asset_digest "${asset_name}")"
        [[ -z "${observed_digest}" ]] || publish_release_die \
            "failed to replace draft release asset ${asset_name} on ${PUBLISH_RELEASE_TAG_NAME}"
    }
}

publish_release_upload_asset() {
    local asset_path=$1
    local asset_name
    local observed_digest
    local expected_digest

    asset_name="$(basename -- "${asset_path}")"
    gh release upload "${PUBLISH_RELEASE_TAG_NAME}" "${asset_path}" >/dev/null 2>&1 || {
        observed_digest="$(publish_release_asset_digest "${asset_name}")"
        expected_digest="sha256:$(publish_release_compute_sha256 "${asset_path}")"
        [[ "${observed_digest}" == "${expected_digest}" ]] || publish_release_die \
            "failed to upload ${asset_name} to release ${PUBLISH_RELEASE_TAG_NAME}"
    }
}

publish_release_converge_asset() {
    local asset_path=$1
    local asset_name
    local expected_digest
    local observed_digest

    asset_name="$(basename -- "${asset_path}")"
    [[ -f "${asset_path}" ]] || publish_release_die "missing release asset at ${asset_path}"
    expected_digest="sha256:$(publish_release_compute_sha256 "${asset_path}")"
    observed_digest="$(publish_release_asset_digest "${asset_name}")"

    if [[ "${observed_digest}" == "${expected_digest}" ]]; then
        return
    fi

    publish_release_delete_asset_if_present "${asset_name}"
    publish_release_upload_asset "${asset_path}"

    observed_digest="$(publish_release_asset_digest "${asset_name}")"
    [[ "${observed_digest}" == "${expected_digest}" ]] || publish_release_die \
        "release ${PUBLISH_RELEASE_TAG_NAME} asset ${asset_name} did not converge to digest ${expected_digest}"
}

publish_release_prepare_for_asset_mutation() {
    local release_is_draft
    local asset_path
    local asset_name
    local expected_digest
    local observed_digest

    publish_release_ensure_mutable_release
    release_is_draft="$(publish_release_api_field '.draft')"
    [[ "${release_is_draft}" == "true" || "${release_is_draft}" == "false" ]] || publish_release_die \
        "release draft state must resolve to true or false"
    if [[ "${release_is_draft}" == "true" ]]; then
        return
    fi

    for asset_path in "${PUBLISH_RELEASE_ASSET_PATHS[@]}"; do
        asset_name="$(basename -- "${asset_path}")"
        expected_digest="sha256:$(publish_release_compute_sha256 "${asset_path}")"
        observed_digest="$(publish_release_asset_digest "${asset_name}")"
        if [[ "${observed_digest}" != "${expected_digest}" ]]; then
            publish_release_patch_state true false
            return
        fi
    done
}

publish_release_main() {
    local tag_name="${RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
    if [[ -z "${tag_name}" && $# -gt 0 && "$1" == v* ]]; then
        tag_name="$1"
        shift
    fi

    readonly PUBLISH_RELEASE_TAG_NAME="${tag_name}"
    readonly PUBLISH_RELEASE_ASSET_PATHS=("$@")
    readonly PUBLISH_RELEASE_MARK_LATEST="${FINGRIND_RELEASE_MARK_LATEST:-false}"

    [[ -n "${GH_TOKEN:-}" ]] || publish_release_die "GH_TOKEN is required"
    [[ -n "${PUBLISH_RELEASE_TAG_NAME}" ]] || publish_release_die "release tag is required"
    [[ "${PUBLISH_RELEASE_MARK_LATEST}" == "true" || "${PUBLISH_RELEASE_MARK_LATEST}" == "false" ]] || \
        publish_release_die "FINGRIND_RELEASE_MARK_LATEST must be true or false"

    readonly PUBLISH_RELEASE_REPO_FULL_NAME="$(publish_release_resolve_repository_slug)"
    [[ -n "${PUBLISH_RELEASE_REPO_FULL_NAME}" ]] || publish_release_die \
        "failed to resolve GitHub repository slug"

    publish_release_prepare_for_asset_mutation
    local asset_path
    for asset_path in "${PUBLISH_RELEASE_ASSET_PATHS[@]}"; do
        publish_release_converge_asset "${asset_path}"
    done
    publish_release_patch_state false "${PUBLISH_RELEASE_MARK_LATEST}"
    printf 'GitHub release publish converged for %s (latest=%s)\n' \
        "${PUBLISH_RELEASE_TAG_NAME}" "${PUBLISH_RELEASE_MARK_LATEST}"
}
