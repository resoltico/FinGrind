#!/usr/bin/env bash
# Keep the release protocol's worktree/bootstrap and merge-handoff safeguards documented.

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
grep -Fq 'primary checkout contains the real release payload but release verification must happen from' "${release_protocol}" || die \
    "release protocol no longer documents bootstrapping unpublished release payload into a clean worktree"
grep -Fq 'git diff --binary > /tmp/fingrind-release-bootstrap.patch' "${release_protocol}" || die \
    "release protocol no longer documents the explicit patch bootstrap path"
grep -Fq 'repo-wide verification lock' "${release_protocol}" || die \
    "release protocol no longer documents live repo-lock handoff for release verification"
grep -Fq 'do not delete the lock by hand' "${release_protocol}" || die \
    "release protocol no longer forbids deleting a live verification lock by hand"
grep -Fq 'gh pr merge <N> --repo "$REPO" --merge --admin --delete-branch' "${release_protocol}" || die \
    "release protocol no longer pins repository-scoped gh pr merge for worktree-safe release merges"
grep -Fq 'git switch --detach origin/main' "${release_protocol}" || die \
    "release protocol no longer documents detached origin/main merge-handoff verification"
grep -Fq "do not treat a non-zero \`gh pr merge\` exit as proof that the merge failed" "${release_protocol}" || die \
    "release protocol no longer documents merged-state-authoritative gh merge recovery"

printf 'release protocol worktree handoff regression: success\n'
