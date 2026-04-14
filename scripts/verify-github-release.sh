#!/usr/bin/env bash
# Verify that the GitHub release for the current tag exists as a published release with the
# expected bundle and checksum assets attached.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

tag_name="${RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
if [[ -z "${tag_name}" && $# -gt 0 && "$1" == v* ]]; then
    tag_name="$1"
    shift
fi
readonly tag_name
readonly asset_names=("$@")
readonly retry_count="${FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES:-1}"
readonly retry_delay_seconds="${FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS:-0}"

[[ -n "${GH_TOKEN:-}" ]] || die "GH_TOKEN is required"
[[ -n "${tag_name}" ]] || die "tag name is required"

verify_release_once() {
    local release_tag is_draft is_prerelease has_asset asset_name

    release_tag="$(gh release view "${tag_name}" --json tagName --jq '.tagName' 2>/dev/null)" || return 1
    [[ "${release_tag}" == "${tag_name}" ]] || return 1

    is_draft="$(gh release view "${tag_name}" --json isDraft --jq '.isDraft' 2>/dev/null)" || return 1
    [[ "${is_draft}" == "false" ]] || return 1

    is_prerelease="$(gh release view "${tag_name}" --json isPrerelease --jq '.isPrerelease' 2>/dev/null)" || return 1
    [[ "${is_prerelease}" == "false" ]] || return 1

    for asset_name in "${asset_names[@]}"; do
        has_asset="$(gh release view "${tag_name}" --json assets --jq \
            ".assets | map(.name) | index(\"${asset_name}\") != null" 2>/dev/null)" || return 1
        [[ "${has_asset}" == "true" ]] || return 1
    done

    return 0
}

attempt=1
until verify_release_once; do
    if (( attempt >= retry_count )); then
        die "release ${tag_name} is missing or incomplete for the required asset set"
    fi
    attempt=$((attempt + 1))
    sleep "${retry_delay_seconds}"
done

release_url="$(gh release view "${tag_name}" --json url --jq '.url')"
printf 'Verified GitHub release handoff: %s\n' "${release_url}"
