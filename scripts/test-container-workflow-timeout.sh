#!/usr/bin/env bash
# Keep the container publication job inside the release workflow aligned with the real release
# path.

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
readonly workflow_file="${repo_root}/.github/workflows/release.yml"
readonly developer_distribution_doc="${repo_root}/docs/DEVELOPER_DISTRIBUTION.md"

[[ -f "${workflow_file}" ]] || die "missing release workflow at ${workflow_file}"
[[ -f "${developer_distribution_doc}" ]] || die \
    "missing developer distribution doc at ${developer_distribution_doc}"

timeout_minutes="$(
    awk '
        /^  build-staging-container:/ {
            in_container_job = 1
            next
        }
        in_container_job && /timeout-minutes:/ {
            print $2
            exit
        }
    ' "${workflow_file}"
)"

[[ -n "${timeout_minutes}" ]] || die "failed to resolve container publication timeout"
[[ "${timeout_minutes}" =~ ^[0-9]+$ ]] || die \
    "container publication timeout must be an integer, got '${timeout_minutes}'"
(( timeout_minutes >= 30 )) || die \
    "container publication timeout must leave budget for buildx publication and post-push verification; expected at least 30 minutes, got ${timeout_minutes}"

grep -Fq '      - verify-release' "${workflow_file}" || die \
    "container publication no longer waits for the verified GitHub release handoff"
grep -Fq 'build-staging-container:' "${workflow_file}" || die \
    "release workflow no longer stages native container images per Linux target"
grep -Fq 'promote-container:' "${workflow_file}" || die \
    "release workflow no longer promotes staged container images into the public tags"
grep -Fq 'verify-public-container-surface.sh' "${workflow_file}" || die \
    "container publication no longer verifies staged and public container surfaces through the repo-owned verifier"
grep -Fq 'path: workflow-owner-surface' "${workflow_file}" || die \
    "container publication no longer checks out the workflow-owner helper surface for rerun-safe helper scripts"
grep -Fq 'FINGRIND_WORKFLOW_HELPER_ROOT' "${workflow_file}" || die \
    "container publication no longer resolves helper-rooted release-control scripts"
grep -Fq 'FINGRIND_VERIFY_PUBLIC_CONTAINER_LATEST' "${workflow_file}" || die \
    "container publication no longer aligns latest verification with the canonical latest policy"
grep -Fq 'fg_gradle_docker_context_dir' "${workflow_file}" || die \
    "container publication no longer resolves the staged Docker build context through the canonical wrapper helper"
grep -Fq 'context: .' "${workflow_file}" && die \
    "container publication reopened the repository root instead of the staged Docker build context"
grep -Fq 'docker buildx imagetools create' "${workflow_file}" || die \
    "container publication no longer promotes staged platform images into one public manifest"
grep -Fq 'post-publish verification' "${developer_distribution_doc}" || die \
    "developer distribution doc no longer describes the post-publish verification budget"
if [[ -e "${repo_root}/.github/workflows/container.yml" ]]; then
    die "retired standalone container workflow resurfaced after publication unification"
fi

printf 'container publication regression: success\n'
