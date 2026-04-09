#!/usr/bin/env bash
# Idempotently publish the GitHub release for the current tag and converge it onto the expected
# public state even if duplicate workflow runs race on the same tag.

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
readonly tag_name="${1:-${RELEASE_TAG:-${GITHUB_REF_NAME:-}}}"
readonly asset_path="${repo_root}/cli/build/libs/fingrind.jar"
readonly asset_name="$(basename -- "${asset_path}")"

[[ -n "${GH_TOKEN:-}" ]] || die "GH_TOKEN is required"
[[ -n "${tag_name}" ]] || die "GITHUB_REF_NAME is required"
[[ -f "${asset_path}" ]] || die "missing release asset at ${asset_path}"

release_exists() {
    gh release view "${tag_name}" >/dev/null 2>&1
}

release_has_asset() {
    gh release view "${tag_name}" --json assets --jq \
        ".assets | map(.name) | index(\"${asset_name}\") != null"
}

create_or_converge_release() {
    if release_exists; then
        gh release edit "${tag_name}" \
            --title "${tag_name}" \
            --draft=false \
            --prerelease=false \
            --latest \
            --verify-tag >/dev/null
        return
    fi

    if gh release create "${tag_name}" "${asset_path}" \
        --title "${tag_name}" \
        --generate-notes \
        --latest \
        --verify-tag >/dev/null 2>&1; then
        return
    fi

    release_exists || die "failed to create release ${tag_name}"
}

upload_asset_if_missing() {
    if [[ "$(release_has_asset)" == "true" ]]; then
        return
    fi

    if gh release upload "${tag_name}" "${asset_path}" >/dev/null 2>&1; then
        return
    fi

    [[ "$(release_has_asset)" == "true" ]] || die \
        "failed to upload ${asset_name} to release ${tag_name}"
}

create_or_converge_release
upload_asset_if_missing
printf 'GitHub release publish converged for %s\n' "${tag_name}"
