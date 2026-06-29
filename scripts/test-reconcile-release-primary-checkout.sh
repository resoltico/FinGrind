#!/usr/bin/env bash
# Exercise the replacement-based release closeout path so a corrupt primary checkout can be
# collapsed back to one truthful tree without relying on manual directory choreography.

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

create_release_checkout() {
    local target_dir=$1
    local version=$2
    local origin_dir="${target_dir}/origin.git"
    local release_dir="${target_dir}/release"

    git init --bare "${origin_dir}" >/dev/null
    git clone "${origin_dir}" "${release_dir}" >/dev/null 2>&1
    (
        cd "${release_dir}"
        git config user.name "FinGrind Test"
        git config user.email "fingrind-test@example.com"
        git checkout -b main >/dev/null 2>&1
        cat > gradle.properties <<EOF
version=${version}
fingrindDescription=FinGrind test description
EOF
        cat > CHANGELOG.md <<EOF
# Changelog

## [Unreleased]

## [${version}] - 2026-06-29

### Fixed

- Release state.
EOF
        git add gradle.properties CHANGELOG.md
        git commit -m "Release ${version}" >/dev/null
        git push -u origin main >/dev/null 2>&1
        git checkout -b "release/${version}" >/dev/null 2>&1
    )
    git -C "${origin_dir}" symbolic-ref HEAD refs/heads/main
    printf '%s\n' "${release_dir}"
}

run_expect_failure() {
    if "$@" >/dev/null 2>&1; then
        die "command unexpectedly succeeded: $*"
    fi
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly reconcile_script="${repo_root}/scripts/reconcile-release-primary-checkout.sh"
readonly verify_script="${repo_root}/scripts/verify-release-primary-checkout.sh"

[[ -x "${reconcile_script}" ]] || die "missing executable reconcile script at ${reconcile_script}"
[[ -x "${verify_script}" ]] || die "missing executable verifier at ${verify_script}"
grep -Fq './scripts/reconcile-release-primary-checkout.sh "$PRIMARY_CHECKOUT" "$RELEASE_CLONE" "X.Y.Z"' \
    "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer documents replacement-based primary-checkout reconciliation"

readonly temp_parent="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-reconcile-release-primary-checkout.XXXXXX")"
cleanup() {
    chmod -R u+rwx "${temp_parent}" 2>/dev/null || true
    rm -rf "${temp_parent}" 2>/dev/null || true
}
trap cleanup EXIT

success_root="${temp_parent}/success"
mkdir -p "${success_root}"
success_release="$(create_release_checkout "${success_root}" "9.9.9")"
success_primary="${success_root}/primary"
mkdir -p "${success_primary}"
printf 'corrupt placeholder\n' > "${success_primary}/BROKEN.txt"
bash "${reconcile_script}" "${success_primary}" "${success_release}" "9.9.9" >/dev/null
[[ ! -e "${success_release}" ]] || die "replacement checkout path still exists after reconciliation"
[[ -d "${success_primary}/.git" ]] || die "primary checkout was not replaced with the Git checkout"
[[ "$(git -C "${success_primary}" branch --show-current)" == 'main' ]] || die \
    "reconciled primary checkout is not on main"
"${verify_script}" "${success_primary}" "9.9.9" >/dev/null || die \
    "reconciled primary checkout did not satisfy the canonical verifier"
[[ ! -f "${success_primary}/BROKEN.txt" ]] || die \
    "displaced primary placeholder file survived the replacement reconciliation"

failure_root="${temp_parent}/failure"
mkdir -p "${failure_root}"
failure_release="$(create_release_checkout "${failure_root}" "8.8.8")"
failure_primary="${failure_root}/primary"
mkdir -p "${failure_primary}"
printf 'keep me\n' > "${failure_primary}/BROKEN.txt"
run_expect_failure bash "${reconcile_script}" "${failure_primary}" "${failure_release}" "8.8.9"
[[ -f "${failure_primary}/BROKEN.txt" ]] || die \
    "primary checkout was modified even though reconciliation failed before replacement"
[[ -d "${failure_release}/.git" ]] || die "replacement checkout disappeared after failed reconciliation"

printf 'reconcile-release-primary-checkout regression: success\n'
