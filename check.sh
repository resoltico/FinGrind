#!/usr/bin/env bash
# Run all local verification gates and release packaging checks.
#
# This file intentionally lives at the repository root beside gradlew because it is the canonical
# human-facing project gate entrypoint. The scripts/ directory is reserved for subordinate helper
# scripts that workflows and this root gate invoke.
#
# The fixed six-stage contract is canonically owned by scripts/check-stage-contract.sh so usage
# text, stage selection, and Stage 5 script coverage cannot drift independently.
#
# The script is location-independent: it always targets the repository that contains this file,
# even when invoked from another working directory or through a symlink.
#
# Local runs keep the Gradle daemon for speed. When CI is set, the script adds --no-daemon
# automatically to match the GitHub workflows. Non-interactive runs use --console=plain unless
# the caller already selected a console mode.
#
# Local shell resolution must already provide Java 26. FinGrind's product modules, CLI fat JAR,
# and release flow all rely on the ambient `java` and `javac` commands, not only Gradle
# toolchains. On macOS those commands may resolve either directly into a JDK bin directory or
# through `/usr/bin/*` launcher stubs, so this script validates version output instead of path
# shape alone.
#
# Exit status: 0 on success. Any failing Gradle stage or script precondition returns a non-zero
# exit status. The script emits per-stage finish lines with durations plus one final human-readable
# result line and one machine-readable summary line:
# [CHECK-SUMMARY] status=<success|failure> stage=<stage-id> exit_code=<n> total_elapsed_seconds=<n>
#
# Usage: ./check.sh [supported gradle options]

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

require_shell_java_26() {
    local resolved_java resolved_javac java_version_line java_version_token javac_version_line javac_version_token

    resolved_java="$(command -v java || true)"
    [[ -n "${resolved_java}" ]] || die "no 'java' command found in PATH; FinGrind requires Java 26 in the active shell. See docs/DEVELOPER_JAVA.md."

    resolved_javac="$(command -v javac || true)"
    [[ -n "${resolved_javac}" ]] || die "no 'javac' command found in PATH; FinGrind requires a full Java 26 JDK in the active shell. See docs/DEVELOPER_JAVA.md."

    java_version_line="$("${resolved_java}" --version 2>/dev/null | head -1 || true)"
    java_version_token="$(printf '%s\n' "${java_version_line}" | awk 'NR == 1 { print $2 }')"
    case "${java_version_token}" in
        26|26.*) ;;
        *)
            die "java resolves to ${resolved_java} but reports '${java_version_line:-unknown version}'. FinGrind requires Java 26 in the active shell. See docs/DEVELOPER_JAVA.md."
            ;;
    esac

    javac_version_line="$("${resolved_javac}" --version 2>/dev/null | head -1 || true)"
    javac_version_token="$(printf '%s\n' "${javac_version_line}" | awk 'NR == 1 { print $2 }')"
    case "${javac_version_token}" in
        26|26.*) ;;
        *)
            die "javac resolves to ${resolved_javac} but reports '${javac_version_line:-unknown version}'. FinGrind requires a full Java 26 JDK in the active shell. See docs/DEVELOPER_JAVA.md."
            ;;
    esac
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

readonly repo_root="$(resolve_script_dir)"
readonly gradlew="${repo_root}/gradlew"
readonly process_support_script="${repo_root}/scripts/check-process-support.sh"
readonly monitor_support_script="${repo_root}/scripts/check-monitor-support.sh"
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
current_stage_id='startup'
current_stage_label='starting'
current_stage_log_path=''
current_stage_diagnostics_directory=''
emit_final_status_enabled=true
readonly pulse_interval_seconds=15
readonly stall_threshold_seconds=90
readonly gradle_test_pulse_interval_millis=$((pulse_interval_seconds * 1000))
readonly jazzer_regression_target_count=3
readonly diagnostics_command_timeout_seconds=5
readonly diagnostics_process_capture_limit=6
readonly stall_exit_code=124

[[ -f "${process_support_script}" ]] || die "missing process support helper at ${process_support_script}"
[[ -f "${monitor_support_script}" ]] || die "missing check monitor helper at ${monitor_support_script}"
[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
# shellcheck source=/dev/null
source "${process_support_script}"
# shellcheck source=/dev/null
source "${monitor_support_script}"
# shellcheck source=/dev/null
source "${stage_contract_script}"

print_usage() {
    printf '%s\n' \
        'Usage: ./check.sh [supported gradle options]' \
        '' \
        'Runs six fixed stages against the repository that contains this script:'
    check_stage_usage_lines
    printf '%s\n' \
        '' \
        'Supported options:' \
        '  -h, --help' \
        '  --console=plain|auto|rich|verbose' \
        '  --console plain|auto|rich|verbose' \
        '  --warning-mode=all|fail|summary|none' \
        '  --warning-mode all|fail|summary|none' \
        '  --daemon, --no-daemon' \
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
has_daemon_flag=false
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
        --daemon|--no-daemon)
            has_daemon_flag=true
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

if [[ -n "${CI:-}" && "${has_daemon_flag}" == false ]]; then
    gradle_args+=(--no-daemon)
fi

if [[ ( -n "${CI:-}" || ! -t 1 ) && "${has_console_flag}" == false ]]; then
    gradle_args+=(--console=plain)
fi

current_stage_id='java-validation'
current_stage_label='Java 26 shell validation'
require_shell_java_26

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

run_shell_stage() {
    local stage_id=$1
    local stage_label=$2
    shift 2
    run_monitored_command "${stage_id}" "${stage_label}" "${repo_root}" "$@"
}

for stage_index in "${!check_stage_ids[@]}"; do
    stage_id="${check_stage_ids[stage_index]}"
    stage_label="${check_stage_labels[stage_index]}"
    check_stage_execute "${stage_id}" "${stage_label}" "${repo_root}"
done
