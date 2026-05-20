#!/usr/bin/env bash
# Keep the tagged container publication workflow aligned with the real release path.

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

read -r release_retry_count release_delay_seconds release_wait_budget_seconds <<<"$(
    python3 - <<'PY' "${workflow_file}"
from pathlib import Path
import re
import sys

workflow = Path(sys.argv[1]).read_text(encoding="utf-8")
match = re.search(
    r'- name: Wait for GitHub release assets before publishing the container(?P<body>.*?)(?:\n\s*-\s+name:|\Z)',
    workflow,
    re.S,
)
if match is None:
    raise SystemExit("missing release-asset wait step")
body = match.group("body")
retry_match = re.search(r'FINGRIND_GITHUB_RELEASE_VERIFY_RETRIES:\s*"(\d+)"', body)
delay_match = re.search(r'FINGRIND_GITHUB_RELEASE_VERIFY_DELAY_SECONDS:\s*"(\d+)"', body)
if retry_match is None or delay_match is None:
    raise SystemExit("missing release-asset wait retry controls")
retry_count = int(retry_match.group(1))
delay_seconds = int(delay_match.group(1))
print(retry_count, delay_seconds, retry_count * delay_seconds)
PY
)"

[[ "${release_retry_count}" =~ ^[0-9]+$ ]] || die \
    "container workflow release wait retry count must be an integer, got '${release_retry_count}'"
[[ "${release_delay_seconds}" =~ ^[0-9]+$ ]] || die \
    "container workflow release wait delay must be an integer, got '${release_delay_seconds}'"
[[ "${release_wait_budget_seconds}" =~ ^[0-9]+$ ]] || die \
    "container workflow release wait budget must be an integer, got '${release_wait_budget_seconds}'"

(( release_wait_budget_seconds >= 20 * 60 )) || die \
    "container workflow release wait budget must cover slow multi-platform release publication; expected at least 20 minutes, got ${release_wait_budget_seconds} seconds"
(( timeout_minutes * 60 >= release_wait_budget_seconds + 15 * 60 )) || die \
    "container workflow timeout must leave at least 15 minutes beyond the release wait budget for image build and post-publish verification"

grep -Fq './scripts/verify-github-release.sh' "${workflow_file}" || die \
    "container workflow no longer waits for the GitHub release asset handoff"
grep -Fq './scripts/verify-public-container-surface.sh' "${workflow_file}" || die \
    "container workflow no longer verifies the published public container surface"
grep -Fq 'context: cli/build/docker-context' "${workflow_file}" || die \
    "container workflow no longer publishes from the staged Docker build context"
grep -Fq 'context: .' "${workflow_file}" && die \
    "container workflow reopened the repository root instead of the staged Docker build context"
grep -Fq 'post-publish verification' "${developer_distribution_doc}" || die \
    "developer distribution doc no longer describes the post-publish verification budget"

printf 'container workflow regression: success\n'
