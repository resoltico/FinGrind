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

readonly failing_tee_dir="${tmp_dir}/failing-tee"
mkdir -p "${failing_tee_dir}"
cat > "${failing_tee_dir}/tee" <<'EOF'
#!/usr/bin/env bash
cat >/dev/null
exit 1
EOF
chmod +x "${failing_tee_dir}/tee"

pulse_interval_seconds=1
stall_threshold_seconds=90
stall_exit_code=124
current_stage_id='startup'
current_stage_label='startup'
current_stage_log_path=''
current_stage_diagnostics_directory=''
set +e
PATH="${failing_tee_dir}:${PATH}" run_monitored_command \
    "log-failure" \
    "log-failure stage" \
    "${tmp_dir}" \
    env \
    true >/dev/null 2>&1
tee_failure_status=$?
set -e
[[ ${tee_failure_status} -ne 0 ]] || die \
    "check monitor accepted a stage whose required log stream failed"
last_report_index=$((${#check_report_stage_exit_codes[@]} - 1))
[[ "${check_report_stage_exit_codes[last_report_index]}" != '0' ]] || die \
    "check monitor recorded a successful stage after its required log stream failed"

printf 'check-monitor-runner regression: success\n'
