#!/usr/bin/env bash
# Exercise the primary-checkout release-closeout verifier against disposable repositories so the
# post-release reconciliation contract cannot drift back into prose-only guidance.

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

create_repo() {
    local target_dir=$1
    local version=$2
    local origin_dir="${target_dir}/origin.git"
    local primary_dir="${target_dir}/primary"

    git init --bare "${origin_dir}" >/dev/null
    git clone "${origin_dir}" "${primary_dir}" >/dev/null 2>&1
    (
        cd "${primary_dir}"
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

## [${version}] - 2026-04-22

### Fixed

- Initial release state.
EOF
        git add gradle.properties CHANGELOG.md
        git commit -m "Release ${version}" >/dev/null
        git push -u origin main >/dev/null 2>&1
    )
    git -C "${origin_dir}" symbolic-ref HEAD refs/heads/main
    printf '%s\n' "${primary_dir}"
}

run_verify_expect_success() {
    local primary_dir=$1
    local expected_version=$2
    shift 2
    (
        "$@" "${verify_script}" "${primary_dir}" "${expected_version}" >/dev/null
    )
}

run_verify_expect_failure() {
    local primary_dir=$1
    local expected_version=$2
    shift 2
    (
        if "$@" "${verify_script}" "${primary_dir}" "${expected_version}" >/dev/null 2>&1; then
            die "verifier unexpectedly succeeded"
        fi
    )
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly verify_script="${repo_root}/scripts/verify-release-primary-checkout.sh"

[[ -x "${verify_script}" ]] || die "missing executable verifier script at ${verify_script}"
grep -Fq 'scripts/test-verify-release-primary-checkout.sh' "${repo_root}/check.sh" || die \
    "root check no longer exercises the release primary-checkout regression"
grep -Fq './scripts/verify-release-primary-checkout.sh "$PRIMARY_CHECKOUT" "X.Y.Z"' \
    "${repo_root}/docs/RELEASE_PROTOCOL.md" || die \
    "release protocol no longer requires the primary-checkout closeout verifier"

readonly temp_parent="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-test-verify-release-primary-checkout.XXXXXX")"
test_root="${temp_parent}/run"
cleanup() {
    chmod -R u+rwx "${temp_parent}" 2>/dev/null || true
    rm -rf "${temp_parent}" 2>/dev/null || true
}
trap cleanup EXIT

success_repo="$(create_repo "${test_root}/success" "9.9.9")"
mkdir -p "${success_repo}/tmp/release-scratch"
printf 'scratch\n' > "${success_repo}/tmp/release-scratch/log.txt"
run_verify_expect_success "${success_repo}" "9.9.9" env

unexpected_untracked_repo="$(create_repo "${test_root}/unexpected-untracked" "8.8.8")"
printf 'oops\n' > "${unexpected_untracked_repo}/unexpected.txt"
run_verify_expect_failure "${unexpected_untracked_repo}" "8.8.8" env

dirty_repo="$(create_repo "${test_root}/dirty" "7.7.7")"
printf '\nreleaseNote=dirty\n' >> "${dirty_repo}/gradle.properties"
run_verify_expect_failure "${dirty_repo}" "7.7.7" env

wrong_branch_repo="$(create_repo "${test_root}/wrong-branch" "6.6.6")"
git -C "${wrong_branch_repo}" checkout -b feature/reconcile >/dev/null 2>&1
run_verify_expect_failure "${wrong_branch_repo}" "6.6.6" env

stale_repo="$(create_repo "${test_root}/stale" "5.5.5")"
stale_local_sha="$(git -C "${stale_repo}" rev-parse HEAD)"
printf '\nreleaseNote=peer\n' >> "${stale_repo}/gradle.properties"
git -C "${stale_repo}" add gradle.properties
git -C "${stale_repo}" commit -m "Advance origin" >/dev/null
git -C "${stale_repo}" push origin main >/dev/null 2>&1
git -C "${stale_repo}" reset --hard "${stale_local_sha}" >/dev/null
run_verify_expect_failure "${stale_repo}" "5.5.5" env

version_repo="$(create_repo "${test_root}/version" "4.4.4")"
run_verify_expect_failure "${version_repo}" "4.4.5" env

printf 'verify-release-primary-checkout regression: success\n'
