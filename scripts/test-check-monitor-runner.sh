#!/usr/bin/env bash
# Prove the root check monitor emits bounded stage and Java-compiler warning records.

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
readonly common_helper="${repo_root}/scripts/check-monitor-common.sh"
readonly runner_helper="${repo_root}/scripts/check-monitor-runner.sh"

[[ -f "${common_helper}" ]] || die "missing check monitor common helper"
[[ -f "${runner_helper}" ]] || die "missing check monitor runner helper"

# shellcheck source=/dev/null
source "${common_helper}"
# shellcheck source=/dev/null
source "${runner_helper}"

tmp_dir="$(mktemp -d)"
cleanup() {
    rm -rf "${tmp_dir}"
}
trap cleanup EXIT

readonly stage_log="${tmp_dir}/quality-gates.log"
readonly manifest_path="${tmp_dir}/warnings.txt"
readonly report_path="${tmp_dir}/report.txt"

cat >"${stage_log}" <<'EOF'
> Task :cli:compileJava
src/main/java/example/LegacyApi.java:12: warning: [deprecation] oldApi() in Example has been deprecated
> Task :contract:compileTestJava
Note: Some input files use or override a deprecated API.
> Task :contract:test
warning: expected negative-test output that must not enter the compiler manifest
EOF

emit_stage_warning_manifest quality-gates "${stage_log}" >"${manifest_path}"
[[ "${current_stage_warning_count}" == '2' ]] || die \
    "expected two Java compiler warnings, got ${current_stage_warning_count}"
[[ "${check_report_warning_total}" == '2' ]] || die \
    "expected warning total two, got ${check_report_warning_total}"
grep -Fq 'stage=quality-gates category=java-compiler source=:cli:compileJava fingerprint=java-compiler::cli:compileJava' "${manifest_path}" || die \
    "compiler manifest did not retain the CLI compile task"
grep -Fq 'stage=quality-gates category=java-compiler source=:contract:compileTestJava fingerprint=java-compiler::contract:compileTestJava' "${manifest_path}" || die \
    "compiler manifest did not retain the contract test compile task"
if grep -Fq 'expected negative-test output' "${manifest_path}"; then
    die "compiler manifest admitted arbitrary warning text from a non-compiler task"
fi

record_stage_report quality-gates 0 17 "${current_stage_warning_count}" "${stage_log}" "${tmp_dir}/diagnostics"
emit_check_report >"${report_path}"
grep -Fq '[CHECK-REPORT] stage=quality-gates status=success exit_code=0 elapsed_seconds=17 warning_count=2' "${report_path}" || die \
    "stage report omitted the quality-gate result"
grep -Fq '[CHECK-WARNING-SUMMARY] total=2' "${report_path}" || die \
    "warning summary omitted the normalized warning count"

printf 'check-monitor-runner regression: success\n'
