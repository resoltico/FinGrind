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
readonly jacoco_snapshot_verifier="${repo_root}/scripts/verify-jacoco-snapshot.sh"
readonly build_logic_plugin_jar_verifier="${repo_root}/scripts/verify-build-logic-plugin-jar.sh"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"

[[ -x "${quality_gate_script}" ]] || die "missing executable quality-gate helper"
[[ -x "${repo_hygiene_verifier}" ]] || die "missing executable repo hygiene verifier"
[[ -x "${jacoco_snapshot_verifier}" ]] || die "missing executable JaCoCo snapshot verifier"
[[ -x "${build_logic_plugin_jar_verifier}" ]] || die \
    "missing executable build-logic plugin-jar verifier"
[[ -f "${python_runtime_support}" ]] || die "missing Python runtime support helper"

help_output="$("${quality_gate_script}" --help)"
printf '%s' "${help_output}" | grep -F './scripts/verify-repo-hygiene.sh' >/dev/null || die \
    "quality-gate help no longer documents repository hygiene verification"
printf '%s' "${help_output}" | grep -F './scripts/verify-jacoco-snapshot.sh' >/dev/null || die \
    "quality-gate help no longer documents JaCoCo snapshot verification"
printf '%s' "${help_output}" | grep -F './scripts/verify-build-logic-plugin-jar.sh' >/dev/null || die \
    "quality-gate help no longer documents build-logic plugin-jar verification"

verifier_call_line="$(grep -n '"${repo_hygiene_verifier}"' "${quality_gate_script}" | tail -1 | cut -d: -f1)"
jacoco_verifier_call_line="$(grep -n '"${jacoco_snapshot_verifier}"' "${quality_gate_script}" | tail -1 | cut -d: -f1)"
gradle_check_line="$(grep -n '"${gradlew}" check coverage' "${quality_gate_script}" | tail -1 | cut -d: -f1)"
build_logic_check_line="$(grep -n '"${gradlew}" -p "${build_logic_dir}" check' "${quality_gate_script}" | tail -1 | cut -d: -f1)"
build_logic_plugin_jar_verifier_line="$(grep -n '"${build_logic_plugin_jar_verifier}"' "${quality_gate_script}" | tail -1 | cut -d: -f1)"
python_support_source_line="$(grep -n '"${python_runtime_support}"' "${quality_gate_script}" | head -1 | cut -d: -f1)"
python_support_prepare_line="$(grep -n '^prepare_python_runtime_env$' "${quality_gate_script}" | head -1 | cut -d: -f1)"
[[ -n "${verifier_call_line}" ]] || die "quality-gate helper no longer invokes the repo hygiene verifier"
[[ -n "${jacoco_verifier_call_line}" ]] || die "quality-gate helper no longer invokes the JaCoCo snapshot verifier"
[[ -n "${gradle_check_line}" ]] || die "quality-gate helper no longer invokes Gradle check coverage"
[[ -n "${build_logic_check_line}" ]] || die "quality-gate helper no longer invokes the included build check"
[[ -n "${build_logic_plugin_jar_verifier_line}" ]] || die \
    "quality-gate helper no longer invokes the build-logic plugin-jar verifier"
[[ -n "${python_support_source_line}" ]] || die "quality-gate helper no longer sources Python runtime support"
[[ -n "${python_support_prepare_line}" ]] || die "quality-gate helper no longer prepares the repo-owned Python runtime"
(( verifier_call_line < gradle_check_line )) || die \
    "repository hygiene verification must run before Gradle quality gates"
(( verifier_call_line < jacoco_verifier_call_line )) || die \
    "repository hygiene verification must run before the JaCoCo snapshot verifier"
(( jacoco_verifier_call_line < gradle_check_line )) || die \
    "JaCoCo snapshot verification must run before Gradle quality gates"
(( python_support_source_line < python_support_prepare_line )) || die \
    "Python runtime support must be sourced before it is prepared"
(( python_support_prepare_line < gradle_check_line )) || die \
    "repo-owned Python runtime must be prepared before Gradle quality gates"
(( build_logic_check_line < build_logic_plugin_jar_verifier_line )) || die \
    "build-logic plugin-jar verification must run after the included build check"

printf 'quality gate hygiene contract regression: success\n'
