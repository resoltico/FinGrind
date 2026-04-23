#!/usr/bin/env bash
# Keep the release protocol's large-PR file-list fallback documented so GitHub's diff-size cap
# cannot silently break the operator procedure again.

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
readonly release_protocol="${repo_root}/docs/RELEASE_PROTOCOL.md"

[[ -f "${release_protocol}" ]] || die "missing release protocol at ${release_protocol}"
grep -Fq 'PullRequest.diff too_large' "${release_protocol}" || die \
    "release protocol no longer documents the oversized-PR diff failure mode"
grep -Fq 'gh api "repos/$REPO/pulls/<N>/files" --paginate --jq '\''.[].filename'\''' \
    "${release_protocol}" || die \
    "release protocol no longer documents the pull-files API fallback"

printf 'release protocol PR diff fallback regression: success\n'
