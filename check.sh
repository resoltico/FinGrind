#!/usr/bin/env bash
# Run all local verification gates and release packaging checks.
#
# This file intentionally lives at the repository root beside gradlew because it is the canonical
# operator-facing project gate entrypoint. The scripts/ directory is reserved for subordinate helper
# scripts that workflows and this root gate invoke.
#
# The fixed six-stage contract is canonically owned by scripts/check-stage-contract.sh so usage
# text, stage selection, and Stage 5 script coverage cannot drift independently.
#
# The script is location-independent: it always targets the repository that contains this file,
# even when invoked from another working directory or through a symlink.
#
# Full verification always uses --no-daemon plus one repo-keyed cache-root GRADLE_USER_HOME so
# root verification, nested Jazzer checks, and direct Docker or devcontainer verification do not
# share daemon or cache state accidentally while wrapper lock files and ordinary project build
# trees remain outside the checkout by default. Non-interactive runs use --console=plain unless
# the caller already selected a console mode.
#
# Exit status: 0 on success. Any failing Gradle stage or script precondition returns a non-zero
# exit status. The script emits per-stage finish lines with durations plus one final plain-language
# result line and one machine-readable summary line:
# [CHECK-SUMMARY] status=<success|failure> stage=<stage-id> exit_code=<n> total_elapsed_seconds=<n>
#
# Usage: ./check.sh [supported gradle options]

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

print_usage_stage_lines() {
    printf '%s\n' \
        '  1. scripts/run-quality-gates.sh (repo hygiene + structural governance + check coverage + included build-logic check)' \
        '  2. jazzer/bin/check' \
        '  3. :cli:bundleCliArchive' \
        '  4. scripts/bundle-smoke.sh (bundle acceptance workflow)' \
        '  5. ./scripts/check-release-surface-scripts.sh' \
        '  6. scripts/docker-smoke.sh (Docker acceptance workflow)'
}

print_usage() {
    printf '%s\n' \
        'Usage: ./check.sh [supported gradle options]' \
        '' \
        'Runs six fixed stages against the repository that contains this script:'
    print_usage_stage_lines
    printf '%s\n' \
        '' \
        'Supported options:' \
        '  -h, --help' \
        '  --console=plain|auto|rich|verbose' \
        '  --console plain|auto|rich|verbose' \
        '  --warning-mode=all|fail|summary|none' \
        '  --warning-mode all|fail|summary|none' \
        '  --no-daemon' \
        '  --dry-run, -m' \
        '  --stacktrace, --full-stacktrace, -s, -S' \
        '  --info, --debug, --warn, --quiet, -i, -q' \
        '  --scan, --profile, --continue, --no-continue' \
        '  --parallel, --no-parallel' \
        '  --build-cache, --no-build-cache' \
        '  --configuration-cache, --no-configuration-cache' \
        '  --rerun-tasks, --refresh-dependencies, --offline' \
        '  -Dname=value, -Pname=value' \
        '' \
        'Unsupported inputs:' \
        '  - positional Gradle tasks/selectors such as help, test, tasks, or :cli:test' \
        '  - project-location overrides such as --project-dir, --build-file, or --settings-file' \
        '' \
        'Diagnostic escalation:' \
        '  - Use ./check.sh --info ONLY for normal project-verification failures when the default output' \
        '    does not yet show enough assertion detail or task context to fix the code.' \
        '  - Use ./check.sh --stacktrace ONLY for build-tool, plugin, environment, or filesystem' \
        '    failures when the default output does not already point to the failing source location.' \
        '' \
        'For anything outside this fixed interface, run ./gradlew directly.'
}

for early_argument in "$@"; do
    case "${early_argument}" in
        -h|--help)
            print_usage
            exit 0
            ;;
    esac
done

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

readonly repo_root="$(resolve_script_dir)"
readonly gradlew="${repo_root}/gradlew"
readonly gradle_wrapper_support_script="${repo_root}/scripts/gradle-wrapper-support.sh"
readonly process_support_script="${repo_root}/scripts/check-process-support.sh"
readonly monitor_support_script="${repo_root}/scripts/check-monitor-support.sh"
readonly repo_lock_support_script="${repo_root}/scripts/repo-verification-lock-support.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly python_runtime_support_script="${repo_root}/scripts/python-runtime-support.sh"
readonly quality_gate_script="${repo_root}/scripts/run-quality-gates.sh"
is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac
current_stage_id='startup'
current_stage_label='starting'
current_stage_log_path=''
current_stage_diagnostics_directory=''
emit_final_status_enabled=true
readonly pulse_interval_seconds=15
readonly stall_threshold_seconds=90
readonly gradle_test_pulse_interval_millis=$((pulse_interval_seconds * 1000))
readonly diagnostics_command_timeout_seconds=5
readonly diagnostics_process_capture_limit=6
readonly stall_exit_code=124

[[ -f "${gradle_wrapper_support_script}" ]] || die "missing Gradle wrapper support helper at ${gradle_wrapper_support_script}"
[[ -f "${process_support_script}" ]] || die "missing process support helper at ${process_support_script}"
[[ -f "${monitor_support_script}" ]] || die "missing check monitor helper at ${monitor_support_script}"
[[ -f "${repo_lock_support_script}" ]] || die "missing repo verification lock helper at ${repo_lock_support_script}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${python_runtime_support_script}" ]] || die "missing Python runtime support helper at ${python_runtime_support_script}"
[[ -f "${quality_gate_script}" ]] || die "missing quality gate helper at ${quality_gate_script}"
# shellcheck source=/dev/null
source "${gradle_wrapper_support_script}"
# shellcheck source=/dev/null
source "${process_support_script}"
# shellcheck source=/dev/null
source "${monitor_support_script}"
# shellcheck source=/dev/null
source "${repo_lock_support_script}"
# shellcheck source=/dev/null
source "${stage_contract_script}"
# shellcheck source=/dev/null
source "${python_runtime_support_script}"

readonly gradle_user_home="${FINGRIND_GRADLE_USER_HOME:-$(fg_gradle_user_home_dir "${repo_root}" "${is_darwin}")}"

prepare_python_runtime_env

count_jazzer_regression_targets() {
    local harnesses_path=$1
    python3 - "${harnesses_path}" <<'PY'
import json
import sys
from pathlib import Path

harnesses_path = Path(sys.argv[1])
harnesses = json.loads(harnesses_path.read_text())
print(len(harnesses))
PY
}

readonly jazzer_regression_target_count="$(
    count_jazzer_regression_targets \
        "${repo_root}/jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-harnesses.json"
)"

print_failure_guidance() {
    case "${current_stage_id}" in
        argument-validation)
            printf '%s\n' \
                'Diagnostic escalation:' \
                '  - Do not retry with --info or --stacktrace.' \
                '  - Fix the script arguments first; this failure happened before Gradle started.'
            ;;
        *)
            printf '%s\n' \
                'Diagnostic escalation:' \
                '  - Retry with ./check.sh --info ONLY if this is a normal verification failure and the' \
                '    default output still lacks the exact assertion message, expected/actual values, or' \
                '    enough task context to fix the code.' \
                '  - Use ./check.sh --stacktrace ONLY if this looks like build infrastructure rather than' \
                '    a normal code defect: AccessDeniedException, file-lock or configuration-cache errors,' \
                '    plugin or worker crashes, toolchain failures, or task exceptions without an' \
                '    actionable source file and line.' \
                '  - Do not enable either flag by default; use them only as targeted escalation.'
            ;;
    esac
}

readonly check_started_at="$(epoch_seconds)"

trap emit_final_status EXIT

[[ -x "${gradlew}" ]] || die "missing executable Gradle wrapper at ${gradlew}"
[[ -f "${repo_root}/settings.gradle.kts" ]] || die "missing Gradle settings file at ${repo_root}/settings.gradle.kts"

current_stage_id='argument-validation'
current_stage_label='argument validation'

gradle_args=()
has_console_flag=false
expects_value=''
for gradle_arg in "$@"; do
    if [[ -n "${expects_value}" ]]; then
        if [[ "${gradle_arg}" == -* ]]; then
            die "option ${expects_value} requires a value"
        fi
        gradle_args+=("${gradle_arg}")
        expects_value=''
        continue
    fi
    case "${gradle_arg}" in
        -h|--help)
            emit_final_status_enabled=false
            print_usage
            exit 0
            ;;
        --daemon)
            die "./check.sh always runs without the Gradle daemon; remove --daemon"
            ;;
        --no-daemon)
            gradle_args+=("${gradle_arg}")
            ;;
        --console)
            has_console_flag=true
            gradle_args+=("${gradle_arg}")
            expects_value='--console'
            ;;
        --console=*)
            has_console_flag=true
            gradle_args+=("${gradle_arg}")
            ;;
        --warning-mode)
            gradle_args+=("${gradle_arg}")
            expects_value='--warning-mode'
            ;;
        --warning-mode=*|--dry-run|--stacktrace|--full-stacktrace|--info|--debug|--warn|--quiet|--scan|--profile|--continue|--no-continue|--parallel|--no-parallel|--build-cache|--no-build-cache|--configuration-cache|--no-configuration-cache|--rerun-tasks|--refresh-dependencies|--offline|-m|-q|-i|-s|-S|-D*|-P*)
            gradle_args+=("${gradle_arg}")
            ;;
        -p|--project-dir|--project-dir=*|-b|--build-file|--build-file=*|-c|--settings-file|--settings-file=*)
            die "do not override the project location; this script always targets ${repo_root}"
            ;;
        -x|--exclude-task|--exclude-task=*|--include-build|--include-build=*|--tests|--tests=*)
            die "unsupported Gradle option for this fixed script interface: ${gradle_arg}"
            ;;
        -*)
            die "unsupported Gradle option for this fixed script interface: ${gradle_arg}"
            ;;
        *)
            die "positional Gradle tasks or selectors are not supported here: ${gradle_arg}"
            ;;
    esac
done

[[ -z "${expects_value}" ]] || die "option ${expects_value} requires a value"

if ! printf '%s\n' "${gradle_args[@]}" | grep -Fx -- '--no-daemon' >/dev/null 2>&1; then
    gradle_args+=(--no-daemon)
fi

if [[ ( -n "${CI:-}" || ! -t 1 ) && "${has_console_flag}" == false ]]; then
    gradle_args+=(--console=plain)
fi

mkdir -p "${gradle_user_home}"
export GRADLE_USER_HOME="${gradle_user_home}"
acquire_lock

run_stage() {
    local stage_id=$1
    local stage_label=$2
    local project_dir=$3
    shift 3
    local command_prefix=()
    if [[ "${stage_id}" == 'quality-gates' ]]; then
        command_prefix+=(
            env
            "FINGRIND_TEST_PULSE=1"
            "FINGRIND_TEST_PULSE_INTERVAL_MS=${gradle_test_pulse_interval_millis}"
            "GRADLE_USER_HOME=${gradle_user_home}"
        )
    else
        command_prefix+=(
            env
            "GRADLE_USER_HOME=${gradle_user_home}"
        )
    fi
    run_monitored_command \
        "${stage_id}" \
        "${stage_label}" \
        "${project_dir}" \
        ${command_prefix[@]+"${command_prefix[@]}"} \
        "${gradlew}" \
        --project-dir "${project_dir}" \
        "$@" \
        ${gradle_args[@]+"${gradle_args[@]}"}
}

verify_jazzer_stage_runtime_warnings() {
    [[ "${current_stage_log_path}" != '' ]] || return 0
    local warning_patterns=(
        'WARNING: A terminally deprecated method in sun.misc.Unsafe has been called'
        'sun.misc.Unsafe::objectFieldOffset has been called'
        'Sharing is only supported for boot loader classes because bootstrap classpath has been appended'
    )
    local matched_pattern
    for matched_pattern in "${warning_patterns[@]}"; do
        if grep -Fq "${matched_pattern}" "${current_stage_log_path}"; then
            printf 'Forbidden Jazzer runtime warning detected: %s\n' "${matched_pattern}" | tee -a "${current_stage_log_path}" >&2
            return 1
        fi
    done
}

run_shell_stage() {
    local stage_id=$1
    local stage_label=$2
    shift 2
    run_monitored_command "${stage_id}" "${stage_label}" "${repo_root}" "$@" || return $?
    if [[ "${stage_id}" == 'jazzer-check' ]]; then
        if ! verify_jazzer_stage_runtime_warnings; then
            printf '[CHECK-PULSE] stage=%s event=postcheck-failure reason=forbidden-runtime-warning log=%s\n' \
                "${stage_id}" \
                "${current_stage_log_path}"
            return 1
        fi
    fi
}

run_quality_gate_stage() {
    local stage_id=$1
    local stage_label=$2
    run_monitored_command \
        "${stage_id}" \
        "${stage_label}" \
        "${repo_root}" \
        env \
        "FINGRIND_TEST_PULSE=1" \
        "FINGRIND_TEST_PULSE_INTERVAL_MS=${gradle_test_pulse_interval_millis}" \
        "${quality_gate_script}" \
        ${gradle_args[@]+"${gradle_args[@]}"}
}

for stage_index in "${!check_stage_ids[@]}"; do
    stage_id="${check_stage_ids[stage_index]}"
    stage_label="${check_stage_labels[stage_index]}"
    check_stage_execute "${stage_id}" "${stage_label}" "${repo_root}"
done
