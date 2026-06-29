#!/usr/bin/env bash
# Replace a corrupted or otherwise unusable primary checkout with a clean released checkout and
# prove the replacement is truthful before discarding the displaced tree.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

usage() {
    die "usage: scripts/reconcile-release-primary-checkout.sh <primary-checkout> <replacement-checkout> <expected-version> [default-branch]"
}

resolve_directory_path() {
    local input_path=$1
    cd -P -- "${input_path}" 2>/dev/null && pwd
}

ensure_path_is_not_nested() {
    local left_path=$1
    local right_path=$2
    if [[ "${left_path}" == "${right_path}" ]]; then
        die "primary checkout and replacement checkout must be distinct paths"
    fi
    if [[ "${left_path}" == "${right_path}/"* ]]; then
        die "primary checkout ${left_path} must not be nested inside replacement checkout ${right_path}"
    fi
    if [[ "${right_path}" == "${left_path}/"* ]]; then
        die "replacement checkout ${right_path} must not be nested inside primary checkout ${left_path}"
    fi
}

switch_replacement_to_default_branch() {
    local replacement_checkout_path=$1
    local default_branch_name=$2

    git -C "${replacement_checkout_path}" fetch origin --prune --tags >/dev/null 2>&1 || die \
        "failed to fetch origin for replacement checkout ${replacement_checkout_path}"
    git -C "${replacement_checkout_path}" show-ref --verify --quiet "refs/remotes/origin/${default_branch_name}" || die \
        "replacement checkout ${replacement_checkout_path} is missing origin/${default_branch_name}"

    if git -C "${replacement_checkout_path}" show-ref --verify --quiet "refs/heads/${default_branch_name}"; then
        git -C "${replacement_checkout_path}" switch "${default_branch_name}" >/dev/null 2>&1 || die \
            "failed to switch replacement checkout ${replacement_checkout_path} to ${default_branch_name}"
    else
        git -C "${replacement_checkout_path}" switch -c "${default_branch_name}" --track "origin/${default_branch_name}" \
            >/dev/null 2>&1 || die \
            "failed to create ${default_branch_name} in replacement checkout ${replacement_checkout_path}"
    fi

    git -C "${replacement_checkout_path}" merge --ff-only "origin/${default_branch_name}" >/dev/null 2>&1 || die \
        "replacement checkout ${replacement_checkout_path} could not fast-forward to origin/${default_branch_name}"
}

(( $# >= 3 && $# <= 4 )) || usage

readonly primary_checkout_input=$1
readonly replacement_checkout_input=$2
readonly expected_version=$3
readonly default_branch="${4:-${FINGRIND_RELEASE_DEFAULT_BRANCH:-main}}"

[[ -n "${primary_checkout_input}" ]] || usage
[[ -n "${replacement_checkout_input}" ]] || usage
[[ -n "${expected_version}" ]] || usage
[[ -n "${default_branch}" ]] || die "default branch must not be blank"

readonly primary_parent_input="$(dirname -- "${primary_checkout_input}")"
readonly primary_basename="$(basename -- "${primary_checkout_input}")"
readonly primary_parent="$(
    resolve_directory_path "${primary_parent_input}"
)" || die "failed to resolve parent directory for primary checkout '${primary_checkout_input}'"
readonly primary_checkout="${primary_parent}/${primary_basename}"

readonly replacement_checkout="$(
    resolve_directory_path "${replacement_checkout_input}"
)" || die "failed to resolve replacement checkout path '${replacement_checkout_input}'"

[[ -e "${primary_checkout}" ]] || die "primary checkout path '${primary_checkout}' does not exist"
[[ -d "${replacement_checkout}" ]] || die "replacement checkout path '${replacement_checkout}' is not a directory"

ensure_path_is_not_nested "${primary_checkout}" "${replacement_checkout}"

readonly script_dir="$(
    cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd
)"
readonly repo_root="$(
    cd -P -- "${script_dir}/.." && pwd
)"
readonly verify_script="${repo_root}/scripts/verify-release-primary-checkout.sh"
[[ -x "${verify_script}" ]] || die "missing executable verifier at ${verify_script}"

switch_replacement_to_default_branch "${replacement_checkout}" "${default_branch}"
"${verify_script}" "${replacement_checkout}" "${expected_version}" "${default_branch}" >/dev/null

backup_checkout="${primary_parent}/.${primary_basename}.pre-release-backup"
backup_suffix=0
while [[ -e "${backup_checkout}" ]]; do
    backup_suffix=$(( backup_suffix + 1 ))
    backup_checkout="${primary_parent}/.${primary_basename}.pre-release-backup.${backup_suffix}"
done
readonly backup_checkout

restore_state='pre-move'
restore_primary_checkout() {
    case "${restore_state}" in
        pre-move|done)
            ;;
        primary-backed-up)
            if [[ -e "${backup_checkout}" && ! -e "${primary_checkout}" ]]; then
                mv "${backup_checkout}" "${primary_checkout}" >/dev/null 2>&1 || true
            fi
            ;;
        replacement-installed)
            if [[ -e "${primary_checkout}" && ! -e "${replacement_checkout}" ]]; then
                mv "${primary_checkout}" "${replacement_checkout}" >/dev/null 2>&1 || true
            fi
            if [[ -e "${backup_checkout}" && ! -e "${primary_checkout}" ]]; then
                mv "${backup_checkout}" "${primary_checkout}" >/dev/null 2>&1 || true
            fi
            ;;
    esac
}
trap restore_primary_checkout EXIT

mv "${primary_checkout}" "${backup_checkout}" || die \
    "failed to move primary checkout ${primary_checkout} aside to ${backup_checkout}"
restore_state='primary-backed-up'

mv "${replacement_checkout}" "${primary_checkout}" || die \
    "failed to install replacement checkout ${replacement_checkout} at ${primary_checkout}"
restore_state='replacement-installed'

"${verify_script}" "${primary_checkout}" "${expected_version}" "${default_branch}" >/dev/null

rm -rf "${backup_checkout}" || die "failed to remove displaced primary checkout backup ${backup_checkout}"
restore_state='done'
trap - EXIT

printf 'Reconciled primary checkout %s with replacement checkout; version %s is truthful on %s\n' \
    "${primary_checkout}" \
    "${expected_version}" \
    "${default_branch}"
