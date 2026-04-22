#!/usr/bin/env bash
# Shared process-tree helpers for bounded diagnostics and monitored stage teardown.

collect_descendant_pids() {
    local parent_pid=${1:-}
    local child_pid=''
    [[ "${parent_pid}" =~ ^[0-9]+$ ]] || return 0
    while IFS= read -r child_pid; do
        [[ -n "${child_pid}" ]] || continue
        printf '%s\n' "${child_pid}"
        collect_descendant_pids "${child_pid}"
    done < <(pgrep -P "${parent_pid}" 2>/dev/null || true)
}

collect_process_tree_pids() {
    local root_pid=${1:-}
    [[ "${root_pid}" =~ ^[0-9]+$ ]] || return 0
    {
        printf '%s\n' "${root_pid}"
        collect_descendant_pids "${root_pid}"
    } | awk '!seen[$0]++'
}

terminate_process_tree() {
    local root_pid=${1:-}
    local grace_seconds=${2:-1}
    local process_ids=()
    local process_id=''

    while IFS= read -r process_id; do
        [[ -n "${process_id}" ]] || continue
        process_ids+=("${process_id}")
    done < <(collect_process_tree_pids "${root_pid}")

    if ((${#process_ids[@]} == 0)); then
        return 0
    fi

    kill -TERM "${process_ids[@]}" 2>/dev/null || true
    sleep "${grace_seconds}"

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

capture_with_timeout() {
    local capture_output_path=$1
    local capture_timeout_seconds=$2
    local term_grace_seconds="${CHECK_PROCESS_TIMEOUT_TERM_GRACE_SECONDS:-1}"
    shift 2

    "$@" >"${capture_output_path}" 2>&1 &
    local command_pid=$!
    (
        sleep "${capture_timeout_seconds}"
        if kill -0 "${command_pid}" 2>/dev/null; then
            terminate_process_tree "${command_pid}" "${term_grace_seconds}"
        fi
    ) &
    local watchdog_pid=$!

    wait "${command_pid}" 2>/dev/null || true
    kill "${watchdog_pid}" 2>/dev/null || true
    wait "${watchdog_pid}" 2>/dev/null || true
}
