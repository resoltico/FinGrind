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
readonly quality_gate_script="${repo_root}/scripts/run-quality-gates.sh"

[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${root_check_script}" ]] || die "missing root check entrypoint at ${root_check_script}"
[[ -x "${quality_gate_script}" ]] || die "quality gate helper is not executable at ${quality_gate_script}"

grep -Fq 'check_stage_execute()' "${stage_contract_script}" || die \
    "check stage contract no longer owns the stage execution mapping"
grep -Fq 'check_stage_execute "${stage_id}" "${stage_label}" "${repo_root}"' "${root_check_script}" || die \
    "check.sh no longer delegates fixed-stage execution through the canonical stage-contract owner"
grep -Fq './scripts/check-release-surface-scripts.sh' "${stage_contract_script}" || die \
    "check stage contract no longer advertises the canonical release-surface gate script"
if grep -Fq 'case "${stage_id}" in' "${root_check_script}"; then
    die "check.sh still carries its own fixed-stage execution case mapping"
fi
# Under Bash strict-unset mode, expanding an initialized-but-empty array with the ordinary
# "${array[@]}" form emits an unbound-variable diagnostic. The root gate's no-argument path must
# remain silent before it adds its fixed Gradle options.
if grep -Fq 'printf '\''%s\n'\'' "${gradle_args[@]}" | grep -Fx' "${root_check_script}"; then
    die "check.sh expands its empty Gradle-option array unsafely under strict-unset mode"
fi
grep -Fq 'printf '\''%s\n'\'' ${gradle_args[@]+"${gradle_args[@]}"} | grep -Fx' "${root_check_script}" || die \
    "check.sh no longer guards an empty Gradle-option array under strict-unset mode"

# shellcheck source=/dev/null
source "${stage_contract_script}"
duplicate_stage5_script_paths="$(
    printf '%s\n' "${check_stage5_executable_script_paths[@]}" | sort | uniq -d
)"
[[ -z "${duplicate_stage5_script_paths}" ]] || die \
    "check stage contract executes duplicate Stage 5 script paths: ${duplicate_stage5_script_paths}"
check_help_output="$("${root_check_script}" --help)"
while IFS= read -r usage_line; do
    [[ -n "${usage_line}" ]] || continue
    printf '%s' "${check_help_output}" | grep -F "${usage_line}" >/dev/null || die \
        "check.sh help no longer matches the canonical stage usage line: ${usage_line}"
done < <(check_stage_usage_lines)

printf 'check stage contract regression: success\n'
