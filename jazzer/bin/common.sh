#!/bin/bash

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf '%s\n' "common.sh is a library and must be sourced by a jazzer/bin wrapper." >&2
    exit 1
fi

readonly FG_JAZZER_BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly FG_JAZZER_DIR="$(cd "${FG_JAZZER_BIN_DIR}/.." && pwd)"
readonly FG_REPO_ROOT="$(cd "${FG_JAZZER_DIR}/.." && pwd)"
readonly FG_GRADLEW="${FG_REPO_ROOT}/gradlew"
readonly FG_LOCK_PARENT_DIR="${FG_JAZZER_DIR}/.local"
readonly FG_LOCK_DIR="${FG_LOCK_PARENT_DIR}/run-lock"
readonly FG_LOCK_OWNER_PID_FILE="${FG_LOCK_DIR}/owner.pid"
readonly FG_LOCK_OWNER_COMMAND_FILE="${FG_LOCK_DIR}/owner.command"
readonly FG_TIMEOUT_GRACE_SECONDS=15

fg_lock_acquired=0
fg_active_pid=""
fg_watchdog_pid=""
fg_wrapper_name=""

fg_initialize_wrapper() {
    fg_wrapper_name="$(basename "$1")"
    if [[ ! -x "${FG_GRADLEW}" ]]; then
        printf '%s\n' "FinGrind Gradle wrapper is missing or not executable: ${FG_GRADLEW}" >&2
        exit 1
    fi
    trap 'fg_on_exit' EXIT
    trap 'fg_on_signal 130' INT
    trap 'fg_on_signal 143' TERM
}

fg_on_exit() {
    fg_stop_watchdog
    fg_terminate_active_process
    fg_release_lock
}

fg_on_signal() {
    local exit_code=$1
    fg_stop_watchdog
    fg_terminate_active_process
    fg_release_lock
    trap - EXIT INT TERM
    exit "${exit_code}"
}

fg_run_passive_command() {
    local task_name=$1
    shift
    fg_acquire_lock
    "${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" "${task_name}" "$@"
}

fg_run_active_command() {
    local target_key=$1
    local task_name=$2
    shift 2
    fg_acquire_lock
    fg_run_active_command_unlocked "${target_key}" "${task_name}" "$@"
}

fg_run_all_active_commands() {
    fg_acquire_lock
    fg_run_active_command_unlocked "cli-request" "fuzzCliRequest" "$@"
    fg_run_active_command_unlocked "ledger-plan-request" "fuzzLedgerPlanRequest" "$@"
    fg_run_active_command_unlocked "posting-workflow" "fuzzPostingWorkflow" "$@"
    fg_run_active_command_unlocked "sqlite-book-roundtrip" "fuzzSqliteBookRoundTrip" "$@"
}

fg_run_active_command_unlocked() {
    local target_key=$1
    local task_name=$2
    shift 2

    local run_directory="${FG_JAZZER_DIR}/.local/runs/${target_key}"
    local history_root="${run_directory}/history"
    local history_directory
    history_directory="$(fg_create_history_directory "${history_root}")"
    local latest_log="${run_directory}/latest.log"
    local history_log="${history_directory}/run.log"
    local timed_out_marker="${history_directory}/timed-out"
    local requested_duration

    requested_duration="$(fg_requested_duration "$@")"
    mkdir -p "${run_directory}"

    (
        set -o pipefail
        "${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-daemon "${task_name}" "$@" 2>&1 | tee "${latest_log}" "${history_log}"
    ) &
    fg_active_pid=$!
    fg_start_watchdog "${requested_duration}" "${timed_out_marker}"

    local status
    set +e
    wait "${fg_active_pid}"
    status=$?
    set -e
    fg_active_pid=""
    fg_stop_watchdog

    if [[ -f "${timed_out_marker}" ]]; then
        printf '%s\n' \
            "[JAZZER-WRAPPER] Timed out after requested duration plus ${FG_TIMEOUT_GRACE_SECONDS}s grace." \
            | tee -a "${latest_log}" "${history_log}"
        status=124
    fi
    return "${status}"
}

fg_acquire_lock() {
    mkdir -p "${FG_LOCK_PARENT_DIR}"
    while ! mkdir "${FG_LOCK_DIR}" 2>/dev/null; do
        if fg_lock_is_stale; then
            fg_remove_stale_lock
            continue
        fi
        sleep 1
    done
    printf '%s\n' "$$" > "${FG_LOCK_OWNER_PID_FILE}"
    printf '%s\n' "${fg_wrapper_name}" > "${FG_LOCK_OWNER_COMMAND_FILE}"
    fg_lock_acquired=1
}

fg_release_lock() {
    if [[ ${fg_lock_acquired} -eq 0 ]]; then
        return
    fi
    rm -f "${FG_LOCK_OWNER_PID_FILE}" "${FG_LOCK_OWNER_COMMAND_FILE}"
    rmdir "${FG_LOCK_DIR}" 2>/dev/null || true
    fg_lock_acquired=0
}

fg_lock_is_stale() {
    local owner_pid
    owner_pid="$(fg_lock_owner_pid)"
    if [[ -z "${owner_pid}" ]]; then
        return 0
    fi
    ! fg_is_pid_alive "${owner_pid}"
}

fg_lock_owner_pid() {
    if [[ ! -f "${FG_LOCK_OWNER_PID_FILE}" ]]; then
        printf '%s' ""
        return 0
    fi
    tr -cd '0-9' < "${FG_LOCK_OWNER_PID_FILE}"
}

fg_remove_stale_lock() {
    rm -f "${FG_LOCK_OWNER_PID_FILE}" "${FG_LOCK_OWNER_COMMAND_FILE}"
    rmdir "${FG_LOCK_DIR}" 2>/dev/null || true
}

fg_is_pid_alive() {
    local pid=$1
    [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null
}

fg_child_pids() {
    local parent_pid=$1
    if command -v pgrep >/dev/null 2>&1; then
        pgrep -P "${parent_pid}" 2>/dev/null || true
        return 0
    fi
    ps -o pid= -o ppid= -ax | awk -v target="${parent_pid}" '$2 == target { print $1 }'
}

fg_signal_process_tree() {
    local pid=$1
    local signal_name=$2
    local child_pid

    if ! fg_is_pid_alive "${pid}"; then
        return 0
    fi
    for child_pid in $(fg_child_pids "${pid}"); do
        fg_signal_process_tree "${child_pid}" "${signal_name}"
    done
    kill "-${signal_name}" "${pid}" 2>/dev/null || true
}

fg_terminate_active_process() {
    if [[ -z "${fg_active_pid}" ]]; then
        return 0
    fi
    if fg_is_pid_alive "${fg_active_pid}"; then
        fg_signal_process_tree "${fg_active_pid}" TERM
        sleep 2
        fg_signal_process_tree "${fg_active_pid}" KILL
    fi
    set +e
    wait "${fg_active_pid}" 2>/dev/null
    set -e
    fg_active_pid=""
}

fg_start_watchdog() {
    local requested_duration=$1
    local timed_out_marker=$2
    local requested_seconds

    if [[ -z "${requested_duration}" ]]; then
        return 0
    fi
    requested_seconds="$(fg_parse_duration_seconds "${requested_duration}")" || return 0

    (
        sleep "$((requested_seconds + FG_TIMEOUT_GRACE_SECONDS))"
        if fg_is_pid_alive "${fg_active_pid}"; then
            : > "${timed_out_marker}"
            fg_signal_process_tree "${fg_active_pid}" TERM
            sleep 2
            fg_signal_process_tree "${fg_active_pid}" KILL
        fi
    ) &
    fg_watchdog_pid=$!
}

fg_stop_watchdog() {
    if [[ -z "${fg_watchdog_pid}" ]]; then
        return 0
    fi
    if fg_is_pid_alive "${fg_watchdog_pid}"; then
        kill "${fg_watchdog_pid}" 2>/dev/null || true
    fi
    set +e
    wait "${fg_watchdog_pid}" 2>/dev/null
    set -e
    fg_watchdog_pid=""
}

fg_requested_duration() {
    local expect_value=0
    local value=""
    local argument

    for argument in "$@"; do
        if [[ ${expect_value} -eq 1 ]]; then
            value="${argument}"
            expect_value=0
            continue
        fi
        case "${argument}" in
            -PjazzerMaxDuration=*)
                value="${argument#-PjazzerMaxDuration=}"
                ;;
            -PjazzerMaxDuration)
                expect_value=1
                ;;
        esac
    done
    printf '%s' "${value}"
}

fg_parse_duration_seconds() {
    local duration=$1
    local number="${duration%[sSmMhHdD]}"
    local unit="${duration#"${number}"}"
    local multiplier

    if [[ -z "${number}" || "${number}" == "${duration}" || "${number}" == *[!0-9]* ]]; then
        return 1
    fi

    case "${unit}" in
        s|S)
            multiplier=1
            ;;
        m|M)
            multiplier=60
            ;;
        h|H)
            multiplier=3600
            ;;
        d|D)
            multiplier=86400
            ;;
        *)
            return 1
            ;;
    esac
    printf '%s\n' "$((number * multiplier))"
}

fg_create_history_directory() {
    local history_root=$1
    local timestamp_base
    local candidate
    local suffix=1

    mkdir -p "${history_root}"
    timestamp_base="$(date -u +%Y%m%dT%H%M%SZ)"
    candidate="${history_root}/${timestamp_base}"
    while [[ -e "${candidate}" ]]; do
        candidate="${history_root}/${timestamp_base}-${suffix}"
        suffix=$((suffix + 1))
    done
    mkdir -p "${candidate}"
    printf '%s\n' "${candidate}"
}
