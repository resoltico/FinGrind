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
grep -Fq 'git -C "$PRIMARY_CHECKOUT" diff --binary HEAD > "$RELEASE_PATCH"' \
    "${release_protocol}" || die \
    "release protocol no longer documents a secure bootstrap patch that includes staged and unstaged tracked changes"
grep -Fq 'captures both staged and unstaged tracked changes' "${release_protocol}" || die \
    "release protocol no longer explains the bootstrap patch's tracked-change coverage"
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
grep -Fq './scripts/reconcile-release-primary-checkout.sh "$PRIMARY_CHECKOUT" "$RELEASE_CLONE" "X.Y.Z"' \
    "${release_protocol}" || die \
    "release protocol no longer documents replacement-based clean-clone primary-checkout closeout"
grep -Fq 'git -C "$PRIMARY_CHECKOUT" fetch origin --prune --tags' "${release_protocol}" || die \
    "release protocol no longer refreshes the primary checkout before closeout fast-forward"

fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-release-protocol-worktree-handoff.XXXXXX")"
cleanup() {
    rm -rf "${fixture_root}"
}
trap cleanup EXIT

readonly fixture_primary="${fixture_root}/primary"
readonly fixture_target="${fixture_root}/target"
readonly bootstrap_patch="${fixture_root}/release-bootstrap.patch"

git init -q "${fixture_primary}"
git -C "${fixture_primary}" config user.name 'FinGrind release protocol regression'
git -C "${fixture_primary}" config user.email 'release-protocol-regression@example.invalid'
printf '%s\n' 'baseline' > "${fixture_primary}/tracked.txt"
git -C "${fixture_primary}" add tracked.txt
git -C "${fixture_primary}" commit -q -m 'baseline'

printf '%s\n' 'unstaged payload' > "${fixture_primary}/tracked.txt"
printf '%s\n' 'staged payload' > "${fixture_primary}/staged-payload.txt"
git -C "${fixture_primary}" add staged-payload.txt
git -C "${fixture_primary}" diff --binary HEAD > "${bootstrap_patch}"

git clone -q "${fixture_primary}" "${fixture_target}"
git -C "${fixture_target}" apply --index "${bootstrap_patch}"

[[ "$(<"${fixture_target}/tracked.txt")" == 'unstaged payload' ]] || die \
    "bootstrap patch did not carry the unstaged tracked payload"
[[ "$(<"${fixture_target}/staged-payload.txt")" == 'staged payload' ]] || die \
    "bootstrap patch did not carry the staged new payload"
git -C "${fixture_target}" diff --cached --name-only | grep -Fxq 'tracked.txt' || die \
    "bootstrap application did not stage the tracked payload"
git -C "${fixture_target}" diff --cached --name-only | grep -Fxq 'staged-payload.txt' || die \
    "bootstrap application did not stage the staged new payload"

printf 'release protocol worktree handoff regression: success\n'
