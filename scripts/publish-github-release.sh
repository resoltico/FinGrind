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
tag_name="${RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
if [[ -z "${tag_name}" && $# -gt 0 && "$1" == v* ]]; then
    tag_name="$1"
    shift
fi
readonly tag_name
readonly asset_paths=("$@")

[[ -n "${GH_TOKEN:-}" ]] || die "GH_TOKEN is required"
[[ -n "${tag_name}" ]] || die "release tag is required"

compute_sha256() {
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

release_exists() {
    gh release view "${tag_name}" >/dev/null 2>&1
}

release_asset_digest() {
    local asset_name=$1
    gh release view "${tag_name}" --json assets --jq \
        ".assets[]? | select(.name == \"${asset_name}\") | .digest // empty"
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

    if gh release create "${tag_name}" \
        --title "${tag_name}" \
        --generate-notes \
        --latest \
        --verify-tag >/dev/null 2>&1; then
        return
    fi

    release_exists || die "failed to create release ${tag_name}"
}

delete_release_asset_if_present() {
    local asset_path=$1
    local asset_name
    local observed_digest

    asset_name="$(basename -- "${asset_path}")"
    observed_digest="$(release_asset_digest "${asset_name}")"
    [[ -n "${observed_digest}" ]] || return 0

    if gh release delete-asset "${tag_name}" "${asset_name}" --yes >/dev/null 2>&1; then
        return
    fi

    observed_digest="$(release_asset_digest "${asset_name}")"
    [[ -z "${observed_digest}" ]] || die \
        "failed to replace stale release asset ${asset_name} on ${tag_name}"
}

upload_asset() {
    local asset_path=$1
    local asset_name
    asset_name="$(basename -- "${asset_path}")"
    gh release upload "${tag_name}" "${asset_path}" >/dev/null 2>&1 || {
        local observed_digest expected_digest
        observed_digest="$(release_asset_digest "${asset_name}")"
        expected_digest="sha256:$(compute_sha256 "${asset_path}")"
        [[ "${observed_digest}" == "${expected_digest}" ]] || die \
            "failed to upload ${asset_name} to release ${tag_name}"
    }
}

converge_asset() {
    local asset_path=$1
    local asset_name expected_digest observed_digest

    asset_name="$(basename -- "${asset_path}")"
    [[ -f "${asset_path}" ]] || die "missing release asset at ${asset_path}"
    expected_digest="sha256:$(compute_sha256 "${asset_path}")"
    observed_digest="$(release_asset_digest "${asset_name}")"

    if [[ "${observed_digest}" == "${expected_digest}" ]]; then
        return
    fi

    if [[ -n "${observed_digest}" ]]; then
        delete_release_asset_if_present "${asset_path}"
    fi
    upload_asset "${asset_path}"

    observed_digest="$(release_asset_digest "${asset_name}")"
    if [[ "${observed_digest}" == "${expected_digest}" ]]; then
        return
    fi

    die "release ${tag_name} asset ${asset_name} did not converge to digest ${expected_digest}"
}

create_or_converge_release
for asset_path in "${asset_paths[@]}"; do
    converge_asset "${asset_path}"
done
printf 'GitHub release publish converged for %s\n' "${tag_name}"
