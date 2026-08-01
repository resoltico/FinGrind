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
readonly FG_RUN_TARGETS_FILE="${FG_REPO_ROOT}/jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-run-targets.json"
readonly FG_TIMEOUT_GRACE_SECONDS="${FG_TIMEOUT_GRACE_SECONDS:-15}"
readonly FG_TIMEOUT_STARTUP_SECONDS="${FG_TIMEOUT_STARTUP_SECONDS:-600}"
readonly FG_WRAPPER_EXIT_STATUS_GRADLE_PROPERTY="fingrindJazzerWrapperExitStatusFile"

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
[[ -f "${FG_RUN_TARGETS_FILE}" ]] || {
    printf '%s\n' "Missing Jazzer run-target catalog: ${FG_RUN_TARGETS_FILE}" >&2
    exit 1
}

# shellcheck source=/dev/null
source "${FG_RUN_LOCK_SUPPORT}"

fg_initialize_wrapper() {
    fg_wrapper_name="$(basename "$1")"
    lock_owner_pid="${BASHPID:-$$}"
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

fg_print_nested_gradle_passthrough_help() {
    printf '%s\n' \
        '' \
        'Pass-through:' \
        '  Any remaining arguments are forwarded to the owning Gradle task.' \
        "  For raw Gradle task help, run ${FG_GRADLEW} -p ${FG_JAZZER_DIR} help --task <task-name>."
}

fg_print_root_gradle_passthrough_help() {
    printf '%s\n' \
        '' \
        'Pass-through:' \
        '  Any remaining arguments are forwarded to the owning Gradle task.' \
        "  For raw Gradle task help, run ${FG_GRADLEW} -p ${FG_REPO_ROOT} help --task <task-name>."
}

fg_restore_errexit() {
    local errexit_enabled=$1

    if [[ ${errexit_enabled} -eq 1 ]]; then
        set -e
        return 0
    fi
    set +e
}

fg_replayable_target_keys_from_catalog() {
    fg_replayable_target_keys
}

fg_replayable_target_keys_shell_json() {
    local target_key
    local first=true

    printf '['
    while IFS= read -r target_key; do
        [[ -n "${target_key}" ]] || continue
        if [[ "${first}" == "true" ]]; then
            first=false
        else
            printf ', '
        fi
        printf '"%s"' "${target_key}"
    done < <(fg_replayable_target_keys_from_catalog)
    printf ']'
}

fg_render_replayable_target_keys_inline() {
    local target_key
    local first=true

    while IFS= read -r target_key; do
        [[ -n "${target_key}" ]] || continue
        if [[ "${first}" == "true" ]]; then
            first=false
        else
            printf ', '
        fi
        printf '%s' "${target_key}"
    done < <(fg_replayable_target_keys_from_catalog)
}

fg_print_replayable_target_key_help_block() {
    local target_key

    printf '%s\n' \
        '' \
        'Supported <target-key> values:'
    while IFS= read -r target_key; do
        [[ -n "${target_key}" ]] || continue
        printf '  %s\n' "${target_key}"
    done < <(fg_replayable_target_keys_from_catalog)
}

fg_emit_wrapper_failure_json() {
    local command_name=$1
    local command_usage=$2
    local message=$3
    local supported_target_keys_json=${4:-}

    python3 - "${command_name}" "${command_usage}" "${message}" "${supported_target_keys_json}" <<'PY'
import json
import sys

command_name = sys.argv[1]
usage = sys.argv[2]
message = sys.argv[3]
supported_target_keys_json = sys.argv[4]

payload = {
    "status": "error",
    "command": command_name,
    "exitCode": 1,
    "message": message,
    "usage": usage,
}
if supported_target_keys_json:
    payload["supportedTargetKeys"] = json.loads(supported_target_keys_json)
print(json.dumps(payload, indent=2))
PY
}

fg_fail_wrapper_command() {
    local json_output=$1
    local command_name=$2
    local command_usage=$3
    local message=$4
    local supported_target_keys_json=${5:-}

    if [[ "${json_output}" == "true" ]]; then
        fg_emit_wrapper_failure_json \
            "${command_name}" \
            "${command_usage}" \
            "${message}" \
            "${supported_target_keys_json}"
    else
        printf '%s\n' "${message}" >&2
    fi
    return 1
}

fg_active_target_keys() {
    python3 "${FG_TOPOLOGY_READER}" active-target-keys
}

fg_replayable_target_keys() {
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

    printf '%s\n' \
        "Unknown Jazzer run target: ${target_key}. Supported targets: $(fg_render_replayable_target_keys_inline)" >&2
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
    fg_run_tool_command false "${task_name}" "$@"
}

fg_run_machine_json_read_only_command() {
    fg_run_machine_json_command "$@"
}

fg_run_machine_json_command() {
    local task_name=$1
    shift

    local status_file
    local output_file
    local gradle_command
    local gradle_status
    local command_status
    local json_payload

    status_file="$(mktemp "${TMPDIR:-/tmp}/fingrind-jazzer-tool-status.XXXXXX")"
    output_file="$(mktemp "${TMPDIR:-/tmp}/fingrind-jazzer-tool-output.XXXXXX")"
    acquire_lock
    gradle_command=("${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-configuration-cache -q)
    gradle_command+=(
        "${task_name}"
        "-P${FG_WRAPPER_EXIT_STATUS_GRADLE_PROPERTY}=${status_file}"
    )
    gradle_command+=("$@")

    set +e
    "${gradle_command[@]}" > "${output_file}" 2>&1
    gradle_status=$?
    set -e

    if [[ ${gradle_status} -ne 0 ]]; then
        cat "${output_file}"
        rm -f "${status_file}" "${output_file}"
        return "${gradle_status}"
    fi

    if [[ ! -f "${status_file}" ]]; then
        cat "${output_file}"
        rm -f "${output_file}"
        printf '%s\n' "Missing Jazzer wrapper exit status for task ${task_name}." >&2
        return 1
    fi
    command_status="$(tr -d '[:space:]' < "${status_file}")"
    rm -f "${status_file}"
    if [[ -z "${command_status}" ]]; then
        cat "${output_file}"
        rm -f "${output_file}"
        printf '%s\n' "Missing Jazzer wrapper exit status for task ${task_name}." >&2
        return 1
    fi
    if [[ ! "${command_status}" =~ ^[0-9]+$ ]]; then
        cat "${output_file}"
        rm -f "${output_file}"
        printf '%s\n' \
            "Invalid Jazzer wrapper exit status for task ${task_name}: ${command_status}" >&2
        return 1
    fi
    if ! json_payload="$(fg_extract_terminal_json_payload "${output_file}")"; then
        cat "${output_file}"
        rm -f "${output_file}"
        printf '%s\n' "Machine JSON command ${task_name} did not emit a terminal JSON payload." >&2
        return 1
    fi
    rm -f "${output_file}"
    printf '%s\n' "${json_payload}"
    return "${command_status}"
}

fg_run_write_command() {
    local task_name=$1
    shift
    fg_run_tool_command false "${task_name}" "$@"
}

fg_run_root_verification_command() {
    local task_name=$1
    shift
    acquire_lock
    "${FG_GRADLEW}" -p "${FG_REPO_ROOT}" --no-configuration-cache "${task_name}" "$@"
}

fg_run_nested_verification_command() {
    local task_name=$1
    shift
    acquire_lock
    "${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-configuration-cache "${task_name}" "$@"
}

fg_run_clean_nested_verification_command() {
    acquire_lock
    "${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-configuration-cache clean "$@"
    "${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-configuration-cache check "$@"
}

fg_run_active_command() {
    local target_key=$1
    local task_name=$2
    shift 2
    acquire_lock
    fg_run_active_command_unlocked "${target_key}" "${task_name}" "$@"
}

fg_run_tool_command() {
    local quiet_mode=$1
    local task_name=$2
    shift 2

    local status_file
    local gradle_command
    local gradle_status
    local command_status

    status_file="$(mktemp "${TMPDIR:-/tmp}/fingrind-jazzer-tool-status.XXXXXX")"
    acquire_lock
    gradle_command=("${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-configuration-cache)
    if [[ "${quiet_mode}" == "true" ]]; then
        gradle_command+=("-q")
    fi
    gradle_command+=(
        "${task_name}"
        "-P${FG_WRAPPER_EXIT_STATUS_GRADLE_PROPERTY}=${status_file}"
    )
    gradle_command+=("$@")

    set +e
    "${gradle_command[@]}"
    gradle_status=$?
    set -e

    if [[ ${gradle_status} -ne 0 ]]; then
        rm -f "${status_file}"
        return "${gradle_status}"
    fi

    if [[ ! -f "${status_file}" ]]; then
        return 0
    fi
    command_status="$(tr -d '[:space:]' < "${status_file}")"
    rm -f "${status_file}"
    if [[ -z "${command_status}" ]]; then
        printf '%s\n' "Missing Jazzer wrapper exit status for task ${task_name}." >&2
        return 1
    fi
    if [[ ! "${command_status}" =~ ^[0-9]+$ ]]; then
        printf '%s\n' \
            "Invalid Jazzer wrapper exit status for task ${task_name}: ${command_status}" >&2
        return 1
    fi
    return "${command_status}"
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
    local timeout_reason

    requested_duration="$(fg_requested_duration "$@")"
    mkdir -p "${run_directory}"
    : > "${latest_log}"
    : > "${history_log}"

    (
        set -o pipefail
        "${FG_GRADLEW}" -p "${FG_JAZZER_DIR}" --no-daemon "${task_name}" "$@" 2>&1 | tee "${latest_log}" "${history_log}"
    ) &
    fg_active_pid=$!
    fg_start_watchdog "${requested_duration}" "${timed_out_marker}" "${latest_log}"

    local status
    set +e
    wait "${fg_active_pid}"
    status=$?
    set -e
    fg_active_pid=""
    fg_stop_watchdog

    if [[ -f "${timed_out_marker}" ]]; then
        timeout_reason="$(tr -d '\r\n' < "${timed_out_marker}" 2>/dev/null || true)"
        if [[ "${timeout_reason}" == "startup" ]]; then
            printf '%s\n' \
                "[JAZZER-WRAPPER] Timed out before fuzz execution reached the libFuzzer start marker within ${FG_TIMEOUT_STARTUP_SECONDS}s." \
                | tee -a "${latest_log}" "${history_log}"
        else
            printf '%s\n' \
                "[JAZZER-WRAPPER] Timed out after the libFuzzer start marker plus the requested duration and ${FG_TIMEOUT_GRACE_SECONDS}s grace." \
                | tee -a "${latest_log}" "${history_log}"
        fi
        status=124
    fi
    return "${status}"
}

fg_extract_terminal_json_payload() {
    local output_file=$1

    python3 - "${output_file}" <<'PY'
import json
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text(encoding="utf-8")
for index, character in enumerate(text):
    if character not in "{[":
        continue
    candidate = text[index:].strip()
    if not candidate:
        continue
    try:
        json.loads(candidate)
    except Exception:
        continue
    sys.stdout.write(candidate)
    raise SystemExit(0)
raise SystemExit(1)
PY
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

fg_log_has_fuzz_start_marker() {
    local log_path=$1

    [[ -f "${log_path}" ]] || return 1
    grep -Eq '^INFO: Seed: ' "${log_path}"
}

fg_write_timeout_marker() {
    local timed_out_marker=$1
    local timeout_reason=$2

    printf '%s\n' "${timeout_reason}" > "${timed_out_marker}"
}

fg_start_watchdog() {
    local requested_duration=$1
    local timed_out_marker=$2
    local latest_log=$3
    local requested_seconds

    if [[ -z "${requested_duration}" ]]; then
        return 0
    fi
    requested_seconds="$(fg_parse_duration_seconds "${requested_duration}")" || return 0

    (
        local startup_started_at=${SECONDS}

        while true; do
            if ! fg_is_pid_alive "${fg_active_pid}"; then
                exit 0
            fi
            if fg_log_has_fuzz_start_marker "${latest_log}"; then
                break
            fi
            if ((SECONDS - startup_started_at >= FG_TIMEOUT_STARTUP_SECONDS)); then
                fg_write_timeout_marker "${timed_out_marker}" startup
                fg_signal_process_tree "${fg_active_pid}" TERM
                sleep 2
                fg_signal_process_tree "${fg_active_pid}" KILL
                exit 0
            fi
            sleep 1
        done

        sleep "$((requested_seconds + FG_TIMEOUT_GRACE_SECONDS))"
        if fg_is_pid_alive "${fg_active_pid}"; then
            fg_write_timeout_marker "${timed_out_marker}" runtime
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
    local path_label=${2:-Replay input path}
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
        printf '%s\n' "${path_label} parent directory does not exist: ${candidate_path}" >&2
        return 1
    fi

    resolved_path="${resolved_directory}/${file_name}"
    if [[ ! -e "${resolved_path}" ]]; then
        printf '%s\n' "${path_label} does not exist: ${resolved_path}" >&2
        return 1
    fi
    if [[ ! -f "${resolved_path}" ]]; then
        printf '%s\n' "${path_label} must be a regular file: ${resolved_path}" >&2
        return 1
    fi
    printf '%s\n' "${resolved_path}"
}

fg_normalized_seed_name_suggestion() {
    local seed_name=$1
    local suggestion

    suggestion="$(
        printf '%s' "${seed_name}" |
            tr '[:upper:]' '[:lower:]' |
            sed -E 's/[^a-z0-9]+/_/g; s/^_+//; s/_+$//; s/_{2,}/_/g'
    )"
    if [[ -z "${suggestion}" ]]; then
        suggestion='seed'
    elif [[ "${suggestion}" != [a-z0-9]* ]]; then
        suggestion="seed_${suggestion}"
    fi
    printf '%s\n' "${suggestion}"
}

fg_require_seed_name() {
    local seed_name=$1
    local suggestion

    if [[ "${seed_name}" =~ ^[a-z0-9][a-z0-9_]*$ ]]; then
        printf '%s\n' "${seed_name}"
        return 0
    fi
    suggestion="$(fg_normalized_seed_name_suggestion "${seed_name}")"
    printf '%s\n' \
        "Seed name must use lower_snake_case ASCII letters, digits, and underscores. Try: ${suggestion}" >&2
    return 1
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
