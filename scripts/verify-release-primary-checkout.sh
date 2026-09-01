#!/usr/bin/env bash
# Verify that the primary checkout is truthful again after a release that may have been cut from a
# dedicated worktree or another checkout.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

trimmed_csv_entries() {
    local csv_value=$1
    while IFS= read -r raw_entry || [[ -n "${raw_entry}" ]]; do
        printf '%s\n' "${raw_entry}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//'
    done < <(printf '%s' "${csv_value}" | tr ',' '\n')
}

usage() {
    die "usage: scripts/verify-release-primary-checkout.sh <primary-checkout> <expected-version> [default-branch]"
}

contains_allowed_untracked_prefix() {
    local path=$1
    local allowed_prefix
    for allowed_prefix in "${allowed_untracked_prefixes[@]}"; do
        [[ -n "${allowed_prefix}" ]] || continue
        if [[ "${path}" == "${allowed_prefix}"* ]]; then
            return 0
        fi
    done
    return 1
}

(( $# >= 2 && $# <= 3 )) || usage

readonly primary_checkout_input=$1
readonly expected_version=$2
readonly default_branch="${3:-${FINGRIND_RELEASE_DEFAULT_BRANCH:-main}}"
readonly allowed_untracked_csv="${FINGRIND_ALLOWED_RELEASE_PRIMARY_CHECKOUT_UNTRACKED:-tmp/}"

[[ -n "${primary_checkout_input}" ]] || usage
[[ -n "${expected_version}" ]] || usage
[[ -n "${default_branch}" ]] || die "default branch must not be blank"

primary_checkout="$(
    cd -P -- "${primary_checkout_input}" 2>/dev/null && pwd
)" || die "failed to resolve primary checkout path '${primary_checkout_input}'"
readonly primary_checkout

readonly repo_root="$(
    git -C "${primary_checkout}" rev-parse --show-toplevel 2>/dev/null || true
)"
[[ -n "${repo_root}" ]] || die "path '${primary_checkout}' is not a Git checkout"
[[ "${repo_root}" == "${primary_checkout}" ]] || die \
    "primary checkout path '${primary_checkout}' is not the repository root (actual root: ${repo_root})"

git -C "${primary_checkout}" fetch origin --prune --tags >/dev/null 2>&1 || die \
    "failed to fetch origin for primary checkout ${primary_checkout}"

readonly current_branch="$(git -C "${primary_checkout}" branch --show-current)"
[[ "${current_branch}" == "${default_branch}" ]] || die \
    "primary checkout ${primary_checkout} must be on '${default_branch}' after release closeout, found '${current_branch:-detached HEAD}'"

readonly remote_default_ref="refs/remotes/origin/${default_branch}"
git -C "${primary_checkout}" show-ref --verify --quiet "${remote_default_ref}" || die \
    "missing ${remote_default_ref} after fetch in primary checkout ${primary_checkout}"

readonly local_head_sha="$(git -C "${primary_checkout}" rev-parse HEAD)"
readonly remote_head_sha="$(git -C "${primary_checkout}" rev-parse "${remote_default_ref}")"
[[ "${local_head_sha}" == "${remote_head_sha}" ]] || die \
    "primary checkout ${primary_checkout} is not reconciled: HEAD ${local_head_sha} does not match origin/${default_branch} ${remote_head_sha}"

readonly gradle_properties_path="${primary_checkout}/gradle.properties"
readonly changelog_path="${primary_checkout}/CHANGELOG.md"
[[ -f "${gradle_properties_path}" ]] || die "missing gradle.properties in ${primary_checkout}"
[[ -f "${changelog_path}" ]] || die "missing CHANGELOG.md in ${primary_checkout}"

grep -Eq "^version=${expected_version}$" "${gradle_properties_path}" || die \
    "gradle.properties in ${primary_checkout} does not declare version=${expected_version}"
grep -Eq "^## \\[${expected_version//./\\.}\\] - [0-9]{4}-[0-9]{2}-[0-9]{2}$" "${changelog_path}" || die \
    "CHANGELOG.md in ${primary_checkout} does not contain a release section for ${expected_version}"
readonly tracked_status="$(
    git -C "${primary_checkout}" status --porcelain=v1 --untracked-files=no
)"
[[ -z "${tracked_status}" ]] || die \
    "primary checkout ${primary_checkout} has tracked local changes after release closeout:\n${tracked_status}"

allowed_untracked_prefixes=()
while IFS= read -r trimmed_prefix || [[ -n "${trimmed_prefix}" ]]; do
    [[ -n "${trimmed_prefix}" ]] || continue
    allowed_untracked_prefixes+=("${trimmed_prefix}")
done < <(trimmed_csv_entries "${allowed_untracked_csv}")

unexpected_untracked=()
while IFS= read -r -d '' untracked_path; do
    if contains_allowed_untracked_prefix "${untracked_path}"; then
        continue
    fi
    unexpected_untracked+=("${untracked_path}")
done < <(git -C "${primary_checkout}" ls-files --others --exclude-standard -z)

if (( ${#unexpected_untracked[@]} > 0 )); then
    printf -v unexpected_summary '%s\n' "${unexpected_untracked[@]}"
    die \
        "primary checkout ${primary_checkout} has unexpected untracked paths after release closeout:\n${unexpected_summary%$'\n'}"
fi

printf 'Verified release primary checkout %s on %s at %s for version %s\n' \
    "${primary_checkout}" \
    "${default_branch}" \
    "${local_head_sha}" \
    "${expected_version}"
