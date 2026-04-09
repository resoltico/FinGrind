#!/usr/bin/env bash
# Run all local verification gates and release packaging checks.
#
# This file intentionally lives at the repository root beside gradlew because it is the canonical
# human-facing project gate entrypoint. The scripts/ directory is reserved for subordinate helper
# scripts that workflows and this root gate invoke.
#
# Stage 1 runs all root-project quality gates and generates coverage reports for local inspection:
#   check    -> Spotless (format), Error Prone (compile-time checks), PMD (static analysis),
#               JaCoCo coverage thresholds, and all unit tests
#   coverage -> per-module and aggregated JaCoCo HTML/XML reports
#
# Stage 2 runs the nested Jazzer verification build:
#   jazzer check -> deterministic Jazzer support tests plus committed-seed regression replay
#
# CI runs only the root check task (verification without report generation).
#
# Stage 3 mirrors the GitHub release packaging workflow:
#   :cli:shadowJar -> build the distributable fat JAR
#
# Stage 4 syntax-checks the release-surface shell scripts:
#   bash -n check.sh scripts/*.sh
#
# Stage 5 exercises the Docker release surface from a non-default working directory:
#   scripts/docker-smoke.sh -> build the image and verify help/request/response/save behavior
#
# The script is location-independent: it always targets the repository that contains this file,
# even when invoked from another working directory or through a symlink.
#
# Local runs keep the Gradle daemon for speed. When CI is set, the script adds --no-daemon
# automatically to match the GitHub workflows. Non-interactive runs use --console=plain unless
# the caller already selected a console mode.
#
# Local shell resolution must already point at Java 26. FinGrind's product modules, CLI fat JAR,
# and release flow all rely on the ambient `java` command, not only Gradle toolchains. The macOS
# `/usr/bin/java` launcher stub is therefore an invalid local runtime for this script.
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
    local resolved_java resolved_javac java_version_line java_version_token

    resolved_java="$(command -v java || true)"
    [[ -n "${resolved_java}" ]] || die "no 'java' command found in PATH; FinGrind requires Java 26 in the active shell. See docs/DEVELOPER_JAVA.md."
    [[ "${resolved_java}" != '/usr/bin/java' ]] || die "java resolves to the macOS /usr/bin/java stub. Activate Java 26 in your shell before running ./check.sh. See docs/DEVELOPER_JAVA.md."

    resolved_javac="$(command -v javac || true)"
    [[ -n "${resolved_javac}" ]] || die "no 'javac' command found in PATH; FinGrind requires a full Java 26 JDK in the active shell. See docs/DEVELOPER_JAVA.md."
    [[ "${resolved_javac}" != '/usr/bin/javac' ]] || die "javac resolves to the macOS /usr/bin/javac stub. Activate Java 26 in your shell before running ./check.sh. See docs/DEVELOPER_JAVA.md."

    java_version_line="$("${resolved_java}" --version 2>/dev/null | head -1 || true)"
    java_version_token="$(printf '%s\n' "${java_version_line}" | awk 'NR == 1 { print $2 }')"
    case "${java_version_token}" in
        26|26.*) ;;
        *)
            die "java resolves to ${resolved_java} but reports '${java_version_line:-unknown version}'. FinGrind requires Java 26 in the active shell. See docs/DEVELOPER_JAVA.md."
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
readonly stall_exit_code=124

format_duration() {
    local total_seconds=$1
    local hours=$((total_seconds / 3600))
    local minutes=$(((total_seconds % 3600) / 60))
    local seconds=$((total_seconds % 60))

    if (( hours > 0 )); then
        printf '%dh%02dm%02ds' "${hours}" "${minutes}" "${seconds}"
        return
    fi
    if (( minutes > 0 )); then
        printf '%dm%02ds' "${minutes}" "${seconds}"
        return
    fi
    printf '%ss' "${seconds}"
}

print_usage() {
    printf '%s\n' \
        'Usage: ./check.sh [supported gradle options]' \
        '' \
        'Runs five fixed stages against the repository that contains this script:' \
        '  1. check coverage' \
        '  2. jazzer check' \
        '  3. :cli:shadowJar' \
        '  4. bash -n check.sh scripts/*.sh' \
        '  5. scripts/docker-smoke.sh' \
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

epoch_seconds() {
    date +%s
}

readonly check_started_at="$(epoch_seconds)"

file_size_bytes() {
    local file_path=$1
    if [[ ! -f "${file_path}" ]]; then
        printf '0'
        return
    fi
    stat -f '%z' "${file_path}"
}

latest_nonempty_line() {
    local log_path=$1
    if [[ ! -s "${log_path}" ]]; then
        return 0
    fi
    awk 'NF { line = $0 } END { if (line != "") print line }' "${log_path}"
}

latest_nonempty_line_marker() {
    local log_path=$1
    if [[ ! -s "${log_path}" ]]; then
        return 0
    fi
    awk 'NF { line = $0; line_number = NR } END { if (line != "") printf "%s:%s", line_number, line }' "${log_path}"
}

compact_text() {
    printf '%s' "$1" \
        | tr '\n' ' ' \
        | sed -E 's/[[:space:]]+/ /g; s/^ //; s/ $//' \
        | cut -c1-220
}

latest_task_line() {
    local log_path=$1
    local task_line=''
    task_line="$(grep '^> Task ' "${log_path}" | tail -1 2>/dev/null || true)"
    if [[ -n "${task_line}" ]]; then
        printf '%s' "${task_line}"
        return
    fi
    latest_nonempty_line "${log_path}"
}

latest_jazzer_pulse_line() {
    local log_path=$1
    grep '^\[JAZZER-PULSE\]' "${log_path}" | tail -1 2>/dev/null || true
}

latest_jazzer_pulse_marker() {
    local log_path=$1
    grep -n '^\[JAZZER-PULSE\]' "${log_path}" | tail -1 2>/dev/null || true
}

latest_gradle_test_pulse_line() {
    local log_path=$1
    grep '^\[GRADLE-TEST-PULSE\]' "${log_path}" | tail -1 2>/dev/null || true
}

latest_gradle_test_pulse_marker() {
    local log_path=$1
    grep -n '^\[GRADLE-TEST-PULSE\]' "${log_path}" | tail -1 2>/dev/null || true
}

stage_progress_summary_quality_gates() {
    local log_path=$1
    local completed_test_classes
    local latest_pulse

    completed_test_classes="$(
        grep -c '^\[GRADLE-TEST-PULSE\].* phase=class-complete ' "${log_path}" 2>/dev/null || true
    )"
    latest_pulse="$(latest_gradle_test_pulse_line "${log_path}")"
    if [[ -z "${latest_pulse}" ]]; then
        latest_pulse="$(latest_task_line "${log_path}")"
    fi

    printf 'test-classes=%s latest=%s' \
        "${completed_test_classes}" \
        "$(compact_text "${latest_pulse}")"
}

stage_progress_summary_jazzer() {
    local project_dir=$1
    local log_path=$2
    local support_total_classes
    local completed_support_classes
    local finished_regression_targets
    local latest_pulse

    support_total_classes="$(
        find "${project_dir}/src/test/java" -type f -name '*Test.java' | wc -l | tr -d '[:space:]'
    )"
    completed_support_classes="$(
        grep -c '^\[JAZZER-PULSE\] support-tests phase=class-complete ' "${log_path}" 2>/dev/null || true
    )"
    finished_regression_targets="$(
        grep -c '^\[JAZZER-PULSE\] harness-class=.* phase=finish ' "${log_path}" 2>/dev/null || true
    )"
    latest_pulse="$(grep '^\[JAZZER-PULSE\]' "${log_path}" | tail -1 2>/dev/null || true)"
    if [[ -z "${latest_pulse}" ]]; then
        latest_pulse="$(latest_task_line "${log_path}")"
    fi

    printf 'support-classes=%s/%s regression-targets=%s/%s latest=%s' \
        "${completed_support_classes}" \
        "${support_total_classes}" \
        "${finished_regression_targets}" \
        "${jazzer_regression_target_count}" \
        "$(compact_text "${latest_pulse}")"
}

stage_progress_summary() {
    local stage_id=$1
    local project_dir=$2
    local log_path=$3
    case "${stage_id}" in
        quality-gates)
            stage_progress_summary_quality_gates "${log_path}"
            ;;
        jazzer-check)
            stage_progress_summary_jazzer "${project_dir}" "${log_path}"
            ;;
        *)
            latest_task_line "${log_path}"
            ;;
    esac
}

stage_progress_marker_jazzer() {
    local log_path=$1
    local latest_pulse
    latest_pulse="$(latest_jazzer_pulse_marker "${log_path}")"
    if [[ -n "${latest_pulse}" ]]; then
        printf '%s' "${latest_pulse}"
        return
    fi
    latest_nonempty_line_marker "${log_path}"
}

stage_progress_marker_quality_gates() {
    local log_path=$1
    local latest_pulse
    latest_pulse="$(latest_gradle_test_pulse_marker "${log_path}")"
    if [[ -n "${latest_pulse}" ]]; then
        printf '%s' "${latest_pulse}"
        return
    fi
    latest_nonempty_line_marker "${log_path}"
}

stage_progress_marker() {
    local stage_id=$1
    local project_dir=$2
    local log_path=$3
    case "${stage_id}" in
        quality-gates)
            stage_progress_marker_quality_gates "${log_path}"
            ;;
        jazzer-check)
            stage_progress_marker_jazzer "${log_path}"
            ;;
        *)
            latest_nonempty_line_marker "${log_path}"
            ;;
    esac
}

collect_descendant_pids() {
    local parent_pid=$1
    local child_pid=''
    while IFS= read -r child_pid; do
        [[ -n "${child_pid}" ]] || continue
        printf '%s\n' "${child_pid}"
        collect_descendant_pids "${child_pid}"
    done < <(pgrep -P "${parent_pid}" 2>/dev/null || true)
}

capture_with_timeout() {
    local output_path=$1
    local timeout_seconds=$2
    shift 2

    (
        "$@" >"${output_path}" 2>&1
    ) &
    local command_pid=$!
    (
        sleep "${timeout_seconds}"
        if kill -0 "${command_pid}" 2>/dev/null; then
            kill -TERM "${command_pid}" 2>/dev/null || true
        fi
    ) &
    local watchdog_pid=$!

    wait "${command_pid}" 2>/dev/null || true
    kill "${watchdog_pid}" 2>/dev/null || true
    wait "${watchdog_pid}" 2>/dev/null || true
}

capture_stage_diagnostics() {
    local stage_id=$1
    local child_pid=$2
    local log_path=$3
    local diagnostics_root=$4
    local quiet_seconds=$5
    local snapshot_dir="${diagnostics_root}/$(date -u +%Y%m%dT%H%M%SZ)"
    mkdir -p "${snapshot_dir}"

    {
        printf 'stage=%s\n' "${stage_id}"
        printf 'captured_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf 'quiet_seconds=%s\n' "${quiet_seconds}"
        printf 'log_path=%s\n' "${log_path}"
    } >"${snapshot_dir}/metadata.txt"

    local process_ids=()
    local process_id=''
    while IFS= read -r process_id; do
        [[ -n "${process_id}" ]] || continue
        process_ids+=("${process_id}")
    done < <(
        {
            printf '%s\n' "${child_pid}"
            collect_descendant_pids "${child_pid}"
        } | awk '!seen[$0]++'
    )

    if ((${#process_ids[@]} > 0)); then
        ps -o pid=,ppid=,etime=,%cpu=,%mem=,command= -p "${process_ids[@]}" \
            >"${snapshot_dir}/process-tree.txt" 2>&1 || true
        if command -v lsof >/dev/null 2>&1; then
            for process_id in "${process_ids[@]}"; do
                capture_with_timeout \
                    "${snapshot_dir}/lsof-${process_id}.txt" \
                    "${diagnostics_command_timeout_seconds}" \
                    lsof -p "${process_id}"
            done
        fi
        if command -v jcmd >/dev/null 2>&1; then
            for process_id in "${process_ids[@]}"; do
                if ps -o command= -p "${process_id}" 2>/dev/null | grep -q '[j]ava'; then
                    capture_with_timeout \
                        "${snapshot_dir}/jcmd-${process_id}-thread-print.txt" \
                        "${diagnostics_command_timeout_seconds}" \
                        jcmd "${process_id}" Thread.print
                fi
            done
        fi
    fi

    tail -n 200 "${log_path}" >"${snapshot_dir}/log-tail.txt" 2>&1 || true
    printf '[CHECK-DIAG] stage=%s quiet=%ss diagnostics=%s\n' \
        "${stage_id}" \
        "${quiet_seconds}" \
        "${snapshot_dir}"
}

terminate_stage_process() {
    local child_pid=$1
    local process_ids=()
    local process_id=''
    while IFS= read -r process_id; do
        [[ -n "${process_id}" ]] || continue
        process_ids+=("${process_id}")
    done < <(
        {
            printf '%s\n' "${child_pid}"
            collect_descendant_pids "${child_pid}"
        } | awk '!seen[$0]++'
    )

    if ((${#process_ids[@]} == 0)); then
        return
    fi

    kill -TERM "${process_ids[@]}" 2>/dev/null || true
    sleep 5

    local remaining_process_ids=()
    for process_id in "${process_ids[@]}"; do
        if kill -0 "${process_id}" 2>/dev/null; then
            remaining_process_ids+=("${process_id}")
        fi
    done

    if ((${#remaining_process_ids[@]} > 0)); then
        kill -KILL "${remaining_process_ids[@]}" 2>/dev/null || true
    fi
}

monitor_stage_process() {
    local stage_id=$1
    local project_dir=$2
    local log_path=$3
    local diagnostics_root=$4
    local child_pid=$5
    local started_at
    local last_output_at
    local last_progress_at
    local last_seen_size=0
    local last_progress_marker=''

    started_at="$(epoch_seconds)"
    last_output_at="${started_at}"
    last_progress_at="${started_at}"

    while kill -0 "${child_pid}" 2>/dev/null; do
        sleep "${pulse_interval_seconds}"
        if ! kill -0 "${child_pid}" 2>/dev/null; then
            break
        fi

        local now
        local current_size
        now="$(epoch_seconds)"
        current_size="$(file_size_bytes "${log_path}")"
        if (( current_size > last_seen_size )); then
            last_output_at="${now}"
            last_seen_size="${current_size}"
        fi

        local elapsed_seconds
        local quiet_seconds
        local stalled_seconds
        local progress_summary
        local progress_marker
        progress_marker="$(stage_progress_marker "${stage_id}" "${project_dir}" "${log_path}")"
        if [[ -n "${progress_marker}" && "${progress_marker}" != "${last_progress_marker}" ]]; then
            last_progress_at="${now}"
            last_progress_marker="${progress_marker}"
        fi
        elapsed_seconds=$((now - started_at))
        quiet_seconds=$((now - last_output_at))
        stalled_seconds=$((now - last_progress_at))
        progress_summary="$(stage_progress_summary "${stage_id}" "${project_dir}" "${log_path}")"
        if [[ -z "${progress_summary}" ]]; then
            progress_summary='(no progress reported yet)'
        fi
        printf '[CHECK-PULSE] stage=%s elapsed=%ss quiet=%ss stalled=%ss progress=%s\n' \
            "${stage_id}" \
            "${elapsed_seconds}" \
            "${quiet_seconds}" \
            "${stalled_seconds}" \
            "$(compact_text "${progress_summary}")"

        if (( stalled_seconds >= stall_threshold_seconds )); then
            capture_stage_diagnostics \
                "${stage_id}" \
                "${child_pid}" \
                "${log_path}" \
                "${diagnostics_root}" \
                "${stalled_seconds}"
            printf '[CHECK-STALL] stage=%s stalled=%ss action=terminate\n' \
                "${stage_id}" \
                "${stalled_seconds}"
            terminate_stage_process "${child_pid}"
            return "${stall_exit_code}"
        fi
    done
}

run_monitored_command() {
    local stage_id=$1
    local stage_label=$2
    local project_dir=$3
    shift 3

    current_stage_id="${stage_id}"
    current_stage_label="${stage_label}"
    printf '%s\n' "${stage_label}"
    local stage_started_at
    stage_started_at="$(epoch_seconds)"

    local stage_temp_dir
    stage_temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-check-${stage_id}.XXXXXX")"
    local log_path="${stage_temp_dir}/${stage_id}.log"
    local diagnostics_root="${stage_temp_dir}/diagnostics"
    local pipe_path="${stage_temp_dir}/${stage_id}.pipe"
    mkdir -p "${diagnostics_root}"
    current_stage_log_path="${log_path}"
    current_stage_diagnostics_directory="${diagnostics_root}"

    printf '[CHECK-PULSE] stage=%s phase=start log=%s diagnostics=%s\n' \
        "${stage_id}" \
        "${log_path}" \
        "${diagnostics_root}"

    mkfifo "${pipe_path}"
    tee -a "${log_path}" <"${pipe_path}" &
    local tee_pid=$!
    (
        cd "${project_dir}"
        "$@"
    ) >"${pipe_path}" 2>&1 &
    local child_pid=$!
    rm -f "${pipe_path}"

    local monitor_exit_code=0
    if monitor_stage_process "${stage_id}" "${project_dir}" "${log_path}" "${diagnostics_root}" "${child_pid}"; then
        monitor_exit_code=0
    else
        monitor_exit_code=$?
    fi

    local child_exit_code=0
    if wait "${child_pid}"; then
        child_exit_code=0
    else
        child_exit_code=$?
    fi
    wait "${tee_pid}" 2>/dev/null || true

    if (( monitor_exit_code != 0 )); then
        child_exit_code="${monitor_exit_code}"
    fi

    printf '[CHECK-PULSE] stage=%s phase=finish exit=%d log=%s\n' \
        "${stage_id}" \
        "${child_exit_code}" \
        "${log_path}"
    local stage_finished_at
    local stage_elapsed_seconds
    stage_finished_at="$(epoch_seconds)"
    stage_elapsed_seconds=$((stage_finished_at - stage_started_at))
    printf '[CHECK-TIMING] stage=%s exit=%d elapsed_seconds=%d elapsed=%s log=%s\n' \
        "${stage_id}" \
        "${child_exit_code}" \
        "${stage_elapsed_seconds}" \
        "$(format_duration "${stage_elapsed_seconds}")" \
        "${log_path}"

    return "${child_exit_code}"
}

emit_final_status() {
    local exit_code=$?
    local total_elapsed_seconds
    total_elapsed_seconds=$(($(epoch_seconds) - check_started_at))
    [[ "${emit_final_status_enabled}" == true ]] || return 0
    if [[ "${exit_code}" -eq 0 ]]; then
        printf 'Result: success in %s\n' "$(format_duration "${total_elapsed_seconds}")"
        printf '[CHECK-SUMMARY] status=success stage=%s exit_code=%d total_elapsed_seconds=%d total_elapsed=%s\n' \
            "${current_stage_id}" \
            "${exit_code}" \
            "${total_elapsed_seconds}" \
            "$(format_duration "${total_elapsed_seconds}")"
    else
        printf 'Result: failure during %s after %s\n' \
            "${current_stage_label}" \
            "$(format_duration "${total_elapsed_seconds}")"
        print_failure_guidance
        if [[ -n "${current_stage_log_path}" ]]; then
            printf 'Stage log: %s\n' "${current_stage_log_path}"
        fi
        if [[ -n "${current_stage_diagnostics_directory}" ]]; then
            printf 'Diagnostics directory: %s\n' "${current_stage_diagnostics_directory}"
        fi
        printf '[CHECK-SUMMARY] status=failure stage=%s exit_code=%d total_elapsed_seconds=%d total_elapsed=%s\n' \
            "${current_stage_id}" \
            "${exit_code}" \
            "${total_elapsed_seconds}" \
            "$(format_duration "${total_elapsed_seconds}")"
    fi
}

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
        "${command_prefix[@]}" \
        "${gradlew}" \
        --project-dir "${project_dir}" \
        "$@" \
        "${gradle_args[@]}"
}

run_shell_stage() {
    local stage_id=$1
    local stage_label=$2
    shift 2
    run_monitored_command "${stage_id}" "${stage_label}" "${repo_root}" "$@"
}

run_stage 'quality-gates' 'Stage 1/5: running quality gates' "${repo_root}" check coverage
run_stage \
    'jazzer-check' \
    'Stage 2/5: running Jazzer support tests and regression replay' \
    "${repo_root}/jazzer" \
    check
run_stage 'cli-shadowjar' 'Stage 3/5: building CLI fat JAR' "${repo_root}" :cli:shadowJar

shell_syntax_targets=("${repo_root}/check.sh")
if [[ -d "${repo_root}/scripts" ]]; then
    while IFS= read -r shell_script_path; do
        shell_syntax_targets+=("${shell_script_path}")
    done < <(find "${repo_root}/scripts" -maxdepth 1 -type f -name '*.sh' | sort)
fi

run_shell_stage 'shell-syntax' 'Stage 4/5: syntax-checking release-surface shell scripts' \
    bash -n "${shell_syntax_targets[@]}"
run_shell_stage 'docker-smoke' 'Stage 5/5: running Docker smoke test' "${repo_root}/scripts/docker-smoke.sh"
