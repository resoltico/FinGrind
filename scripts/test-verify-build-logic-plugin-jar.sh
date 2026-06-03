#!/usr/bin/env bash
# Guard the included-build plugin-jar verifier and its place in the canonical Stage 1 gate.

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
readonly verifier="${repo_root}/scripts/verify-build-logic-plugin-jar.sh"
readonly quality_gate_script="${repo_root}/scripts/run-quality-gates.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly build_logic_plugin_jar_verifier="${repo_root}/scripts/verify-build-logic-plugin-jar.sh"

[[ -x "${verifier}" ]] || die "missing executable build-logic plugin-jar verifier"
[[ -x "${quality_gate_script}" ]] || die "missing executable quality-gate helper"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper"

help_output="$("${quality_gate_script}" --help)"
printf '%s' "${help_output}" | grep -F './scripts/verify-build-logic-plugin-jar.sh' >/dev/null || die \
    "quality-gate help no longer documents the build-logic plugin-jar verifier"

grep -Fq '"${build_logic_plugin_jar_verifier}"' "${quality_gate_script}" || die \
    "quality-gate helper no longer invokes the build-logic plugin-jar verifier"
grep -Fq 'scripts/test-verify-build-logic-plugin-jar.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the build-logic plugin-jar verifier regression"

build_logic_check_line="$(grep -n '"${gradlew}" -p "${build_logic_dir}" check' "${quality_gate_script}" | tail -1 | cut -d: -f1)"
verifier_line="$(grep -n '"${build_logic_plugin_jar_verifier}"' "${quality_gate_script}" | tail -1 | cut -d: -f1)"
[[ -n "${build_logic_check_line}" ]] || die "quality-gate helper no longer invokes the included build check"
[[ -n "${verifier_line}" ]] || die "quality-gate helper no longer invokes the build-logic plugin-jar verifier"
(( build_logic_check_line < verifier_line )) || die \
    "build-logic plugin-jar verification must run after the included build check"

printf 'build-logic plugin-jar verifier regression: success\n'
