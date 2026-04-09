#!/usr/bin/env bash
# Verify that the GitHub release for the current tag exists as a published release with the
# expected fat-JAR asset attached.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

readonly tag_name="${1:-${RELEASE_TAG:-${GITHUB_REF_NAME:-}}}"
readonly asset_name="${2:-fingrind.jar}"

[[ -n "${GH_TOKEN:-}" ]] || die "GH_TOKEN is required"
[[ -n "${tag_name}" ]] || die "tag name is required"

release_tag="$(gh release view "${tag_name}" --json tagName --jq '.tagName')"
[[ "${release_tag}" == "${tag_name}" ]] || die \
    "expected release tag ${tag_name}, got ${release_tag}"

is_draft="$(gh release view "${tag_name}" --json isDraft --jq '.isDraft')"
[[ "${is_draft}" == "false" ]] || die "release ${tag_name} is still a draft"

is_prerelease="$(gh release view "${tag_name}" --json isPrerelease --jq '.isPrerelease')"
[[ "${is_prerelease}" == "false" ]] || die "release ${tag_name} is marked prerelease"

has_asset="$(gh release view "${tag_name}" --json assets --jq \
    ".assets | map(.name) | index(\"${asset_name}\") != null")"
[[ "${has_asset}" == "true" ]] || die \
    "release ${tag_name} is missing required asset ${asset_name}"

release_url="$(gh release view "${tag_name}" --json url --jq '.url')"
printf 'Verified GitHub release handoff: %s\n' "${release_url}"
