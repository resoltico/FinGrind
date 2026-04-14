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

[[ -n "${GH_TOKEN:-}" ]] || die "GH_TOKEN is required"
[[ -n "${tag_name}" ]] || die "tag name is required"

release_tag="$(gh release view "${tag_name}" --json tagName --jq '.tagName')"
[[ "${release_tag}" == "${tag_name}" ]] || die \
    "expected release tag ${tag_name}, got ${release_tag}"

is_draft="$(gh release view "${tag_name}" --json isDraft --jq '.isDraft')"
[[ "${is_draft}" == "false" ]] || die "release ${tag_name} is still a draft"

is_prerelease="$(gh release view "${tag_name}" --json isPrerelease --jq '.isPrerelease')"
[[ "${is_prerelease}" == "false" ]] || die "release ${tag_name} is marked prerelease"

for asset_name in "${asset_names[@]}"; do
    has_asset="$(gh release view "${tag_name}" --json assets --jq \
        ".assets | map(.name) | index(\"${asset_name}\") != null")"
    [[ "${has_asset}" == "true" ]] || die \
        "release ${tag_name} is missing required asset ${asset_name}"
done

release_url="$(gh release view "${tag_name}" --json url --jq '.url')"
printf 'Verified GitHub release handoff: %s\n' "${release_url}"
