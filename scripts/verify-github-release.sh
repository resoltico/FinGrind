#!/usr/bin/env bash
# Verify that the GitHub release for the current tag exists as a published release with the
# expected bundle and checksum assets attached.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

tag_name=''
if [[ $# -gt 0 && "$1" == v* ]]; then
    tag_name="$1"
    shift
fi
if [[ -z "${tag_name}" ]]; then
    tag_name="${RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
fi
readonly tag_name
readonly asset_names=("$@")
readonly retry_count="${FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES:-1}"
readonly retry_delay_seconds="${FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS:-0}"
readonly script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly archive_verifier="${script_dir}/verify-source-archive.py"

[[ -n "${tag_name}" ]] || die "tag name is required"
[[ -f "${archive_verifier}" ]] || die "missing source archive verifier at ${archive_verifier}"

resolve_repository_slug() {
    if [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
        printf '%s\n' "${GITHUB_REPOSITORY}"
        return
    fi
    gh repo view --json nameWithOwner --jq '.nameWithOwner' 2>/dev/null
}

download_source_archive() {
    local repository_slug=$1
    local archive_kind=$2
    local output_path=$3

    gh api "/repos/${repository_slug}/${archive_kind}/${tag_name}" > "${output_path}" 2>/dev/null
}

verify_source_archives() {
    local repository_slug=$1
    local work_dir zip_archive tar_archive

    work_dir="$(mktemp -d)"
    zip_archive="${work_dir}/source.zip"
    tar_archive="${work_dir}/source.tar.gz"

    download_source_archive "${repository_slug}" zipball "${zip_archive}" || {
        rm -rf "${work_dir}"
        return 1
    }
    download_source_archive "${repository_slug}" tarball "${tar_archive}" || {
        rm -rf "${work_dir}"
        return 1
    }
    python3 "${archive_verifier}" "${zip_archive}" "${tar_archive}" >/dev/null || {
        rm -rf "${work_dir}"
        return 1
    }
    rm -rf "${work_dir}"
}

verify_release_once() {
    local release_tag is_draft is_prerelease has_asset asset_name repository_slug

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

    repository_slug="$(resolve_repository_slug)" || return 1
    [[ -n "${repository_slug}" ]] || return 1
    verify_source_archives "${repository_slug}" || return 1

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
