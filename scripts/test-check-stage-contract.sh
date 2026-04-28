#!/usr/bin/env bash
# Guard the root fixed-stage gate so stage execution wiring cannot drift away from the
# canonical stage-contract owner.

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
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly root_check_script="${repo_root}/check.sh"

[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${root_check_script}" ]] || die "missing root check entrypoint at ${root_check_script}"

grep -Fq 'check_stage_execute()' "${stage_contract_script}" || die \
    "check stage contract no longer owns the stage execution mapping"
grep -Fq 'check_stage_execute "${stage_id}" "${stage_label}" "${repo_root}"' "${root_check_script}" || die \
    "check.sh no longer delegates fixed-stage execution through the canonical stage-contract owner"
grep -Fq './scripts/check-shell-syntax.sh' "${stage_contract_script}" || die \
    "check stage contract no longer advertises the canonical shell-syntax gate script"
if grep -Fq 'case "${stage_id}" in' "${root_check_script}"; then
    die "check.sh still carries its own fixed-stage execution case mapping"
fi

printf 'check stage contract regression: success\n'
