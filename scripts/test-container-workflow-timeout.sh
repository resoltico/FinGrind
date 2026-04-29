#!/usr/bin/env bash
# Keep the tagged container publication workflow budget aligned with the real release path.

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
readonly workflow_file="${repo_root}/.github/workflows/container.yml"
readonly developer_distribution_doc="${repo_root}/docs/DEVELOPER_DISTRIBUTION.md"

[[ -f "${workflow_file}" ]] || die "missing container workflow at ${workflow_file}"
[[ -f "${developer_distribution_doc}" ]] || die \
    "missing developer distribution doc at ${developer_distribution_doc}"

timeout_minutes="$(
    awk '
        /name: Build and push container image/ {
            in_container_job = 1
            next
        }
        in_container_job && /timeout-minutes:/ {
            print $2
            exit
        }
    ' "${workflow_file}"
)"

[[ -n "${timeout_minutes}" ]] || die "failed to resolve container workflow timeout"
[[ "${timeout_minutes}" =~ ^[0-9]+$ ]] || die \
    "container workflow timeout must be an integer, got '${timeout_minutes}'"
(( timeout_minutes >= 30 )) || die \
    "container workflow timeout must leave budget for post-publish verification; expected at least 30 minutes, got ${timeout_minutes}"

grep -Fq './scripts/verify-github-release.sh' "${workflow_file}" || die \
    "container workflow no longer waits for the GitHub release asset handoff"
grep -Fq './scripts/verify-container-publication.sh' "${workflow_file}" || die \
    "container workflow no longer verifies the published version and latest tags"
grep -Fq 'post-publish verification' "${developer_distribution_doc}" || die \
    "developer distribution doc no longer describes the post-publish verification budget"

printf 'container workflow timeout regression: success\n'
