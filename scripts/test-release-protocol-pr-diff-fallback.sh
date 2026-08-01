#!/usr/bin/env bash
# Keep the release protocol's large-PR scope fallback documented so neither the GitHub diff-size
# cap nor the pull-files endpoint's separate record cap can silently weaken the procedure.

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
grep -Fq 'baseRefOid,headRefOid,changedFiles' "${release_protocol}" || die \
    "release protocol no longer resolves exact GitHub PR object ids for oversized diffs"
grep -Fq 'git diff --name-only --no-renames "$PR_BASE_SHA" "$PR_HEAD_SHA"' "${release_protocol}" || die \
    "release protocol no longer derives the full oversized-PR path inventory from exact GitHub refs"
grep -Fq 'PR_CHANGED_FILE_COUNT < 3000' "${release_protocol}" || die \
    "release protocol no longer guards the pull-files endpoint record ceiling"
grep -Fq '.previous_filename, .filename' "${release_protocol}" || die \
    "release protocol no longer normalizes renamed pull-file records"
grep -Fq 'diff -u "$PR_SCOPE_DIR/github-paths" "$PR_SCOPE_DIR/git-paths"' "${release_protocol}" || die \
    "release protocol no longer compares GitHub and exact Git path inventories"
grep -Fq 'GitHub pull-files responses are capped at 3000 records' "${release_protocol}" || die \
    "release protocol no longer documents the pull-files endpoint record cap"
grep -Fq 'gh api "repos/$REPO/pulls/<N>/files" --paginate' \
    "${release_protocol}" || die \
    "release protocol no longer documents the pull-files API fallback"

printf 'release protocol PR diff fallback regression: success\n'
