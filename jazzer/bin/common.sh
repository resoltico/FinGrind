#!/bin/bash

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf '%s\n' "common.sh is a library and must be sourced by a jazzer/bin wrapper." >&2
    exit 1
fi

readonly FG_JAZZER_BIN_DIR="${FG_JAZZER_BIN_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
readonly FG_JAZZER_DIR="${FG_JAZZER_DIR:-$(cd "${FG_JAZZER_BIN_DIR}/.." && pwd)}"
readonly FG_REPO_ROOT="${FG_REPO_ROOT:-$(cd "${FG_JAZZER_DIR}/.." && pwd)}"
readonly FG_GRADLEW="${FG_GRADLEW:-${FG_REPO_ROOT}/gradlew}"
readonly FG_RUN_LOCK_SUPPORT="${FG_REPO_ROOT}/jazzer/bin/_run-lock-support"
readonly FG_TOPOLOGY_READER="${FG_REPO_ROOT}/scripts/read-jazzer-topology.py"
readonly FG_TIMEOUT_GRACE_SECONDS=15

fg_active_pid=""
fg_watchdog_pid=""
fg_wrapper_name=""

[[ -f "${FG_RUN_LOCK_SUPPORT}" ]] || {
    printf '%s\n' "Missing Jazzer run-lock support helper: ${FG_RUN_LOCK_SUPPORT}" >&2
    exit 1
}
[[ -f "${FG_TOPOLOGY_READER}" ]] || {
    printf '%s\n' "Missing Jazzer topology reader: ${FG_TOPOLOGY_READER}" >&2
    exit 1
}

# shellcheck source=/dev/null
source "${FG_RUN_LOCK_SUPPORT}"

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

fg_has_help_flag() {
    local argument

    for argument in "$@"; do
        case "${argument}" in
            -h|--help)
                return 0
                ;;
        esac
    done
    return 1
}

fg_print_gradle_passthrough_help() {
    printf '%s\n' \
        '' \
        'Pass-through:' \
        '  Any remaining arguments are forwarded to the owning Gradle task.' \
        "  For raw Gradle task help, run ${FG_GRADLEW} -p ${FG_JAZZER_DIR} help --task <task-name>."
}

fg_restore_errexit() {
    local errexit_enabled=$1

    if [[ ${errexit_enabled} -eq 1 ]]; then
        set -e
        return 0
    fi
    set +e
}

fg_active_target_keys() {
    acquire_lock
    python3 "${FG_TOPOLOGY_READER}" active-target-keys
}

fg_replayable_target_keys() {
    acquire_lock
    python3 "${FG_TOPOLOGY_READER}" replayable-target-keys
}

fg_require_replayable_target_key() {
    local target_key=$1
    local known_target
    local known_targets_output
    local status=0
    local errexit_enabled=0

    case $- in
        *e*) errexit_enabled=1 ;;
    esac

    set +e
    known_targets_output="$(fg_replayable_target_keys)"
    status=$?
    fg_restore_errexit "${errexit_enabled}"
    if [[ ${status} -ne 0 ]]; then
        return "${status}"
    fi

    while IFS= read -r known_target; do
        [[ -n "${known_target}" ]] || continue
        [[ "${known_target}" == "${target_key}" ]] && return 0
    done <<< "${known_targets_output}"

    printf '%s\n' "Unknown Jazzer run target: ${target_key}" >&2
    return 1
}

fg_require_active_target_key() {
    local target_key=$1
    local known_target
    local known_targets_output
    local status=0
    local errexit_enabled=0

    case $- in
        *e*) errexit_enabled=1 ;;
    esac

    set +e
    known_targets_output="$(fg_active_target_keys)"
    status=$?
    fg_restore_errexit "${errexit_enabled}"
    if [[ ${status} -ne 0 ]]; then
        return "${status}"
    fi

    while IFS= read -r known_target; do
        [[ -n "${known_target}" ]] || continue
        [[ "${known_target}" == "${target_key}" ]] && return 0
    done <<< "${known_targets_output}"

    printf '%s\n' "Unknown active Jazzer run target: ${target_key}" >&2
    return 1
}

fg_active_wrapper_scripts() {
    local active_target_output
    local status=0
    local target_key
    local errexit_enabled=0

    case $- in
        *e*) errexit_enabled=1 ;;
    esac

    set +e
    active_target_output="$(fg_active_target_keys)"
    status=$?
    fg_restore_errexit "${errexit_enabled}"
    if [[ ${status} -ne 0 ]]; then
        return "${status}"
    fi

    while IFS= read -r target_key; do
        [[ -n "${target_key}" ]] || continue
        printf 'fuzz-%s\n' "${target_key}"
    done <<< "${active_target_output}"
}

fg_on_exit() {
    fg_stop_watchdog
    fg_terminate_active_process
    cleanup_lock
}

fg_on_signal() {
    local exit_code=$1
    fg_stop_watchdog
    fg_terminate_active_process
    cleanup_lock
    trap - EXIT INT TERM
    exit "${exit_code}"
}

fg_run_read_only_command() {
    local task_name=$1
    shift
    acquire_lock
    "${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-configuration-cache "${task_name}" "$@"
}

fg_run_machine_json_read_only_command() {
    local task_name=$1
    shift
    acquire_lock
    "${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-configuration-cache -q "${task_name}" "$@"
}

fg_run_maintenance_command() {
    local task_name=$1
    shift
    acquire_lock
    "${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-configuration-cache "${task_name}" "$@"
}

fg_run_verification_command() {
    local task_name=$1
    shift
    acquire_lock
    "${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-configuration-cache clean "${task_name}" "$@"
}

fg_run_active_command() {
    local target_key=$1
    local task_name=$2
    shift 2
    acquire_lock
    fg_run_active_command_unlocked "${target_key}" "${task_name}" "$@"
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

fg_absolute_candidate_path() {
    local path=$1

    if [[ "${path}" == /* ]]; then
        printf '%s\n' "${path}"
        return 0
    fi
    printf '%s/%s\n' "$(pwd -P)" "${path}"
}

fg_require_existing_regular_file_path() {
    local path=$1
    local candidate_path
    local directory_name
    local file_name
    local resolved_directory
    local resolved_path
    local status=0
    local errexit_enabled=0

    case $- in
        *e*) errexit_enabled=1 ;;
    esac

    candidate_path="$(fg_absolute_candidate_path "${path}")"
    directory_name="$(dirname "${path}")"
    file_name="$(basename "${path}")"

    set +e
    resolved_directory="$(cd "${directory_name}" 2>/dev/null && pwd -P)"
    status=$?
    fg_restore_errexit "${errexit_enabled}"
    if [[ ${status} -ne 0 ]]; then
        printf '%s\n' "Replay input path parent directory does not exist: ${candidate_path}" >&2
        return 1
    fi

    resolved_path="${resolved_directory}/${file_name}"
    if [[ ! -e "${resolved_path}" ]]; then
        printf '%s\n' "Replay input path does not exist: ${resolved_path}" >&2
        return 1
    fi
    if [[ ! -f "${resolved_path}" ]]; then
        printf '%s\n' "Replay input path must be a regular file: ${resolved_path}" >&2
        return 1
    fi
    printf '%s\n' "${resolved_path}"
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
