#!/usr/bin/env bash
# Guard the Stage 1 quality-gate wrapper so repository hygiene verification cannot drift out of the canonical gate.

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
readonly quality_gate_script="${repo_root}/scripts/run-quality-gates.sh"
readonly repo_hygiene_verifier="${repo_root}/scripts/verify-repo-hygiene.sh"

[[ -x "${quality_gate_script}" ]] || die "missing executable quality-gate helper"
[[ -x "${repo_hygiene_verifier}" ]] || die "missing executable repo hygiene verifier"

help_output="$("${quality_gate_script}" --help)"
printf '%s' "${help_output}" | grep -F './scripts/verify-repo-hygiene.sh' >/dev/null || die \
    "quality-gate help no longer documents repository hygiene verification"

verifier_call_line="$(grep -n '"${repo_hygiene_verifier}"' "${quality_gate_script}" | head -1 | cut -d: -f1)"
gradle_check_line="$(grep -n '"${gradlew}" check coverage' "${quality_gate_script}" | head -1 | cut -d: -f1)"
[[ -n "${verifier_call_line}" ]] || die "quality-gate helper no longer invokes the repo hygiene verifier"
[[ -n "${gradle_check_line}" ]] || die "quality-gate helper no longer invokes Gradle check coverage"
(( verifier_call_line < gradle_check_line )) || die \
    "repository hygiene verification must run before Gradle quality gates"

printf 'quality gate hygiene contract regression: success\n'
