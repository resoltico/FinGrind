#!/usr/bin/env bash
# Diagnostics capture and termination helpers for the root check monitor.

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
    done < <(collect_process_tree_pids "${child_pid}")

    {
        printf 'process_count=%s\n' "${#process_ids[@]}"
        printf 'diagnostics_process_capture_limit=%s\n' "${diagnostics_process_capture_limit}"
    } >>"${snapshot_dir}/metadata.txt"

    if ((${#process_ids[@]} > 0)); then
        ps -o pid=,ppid=,etime=,%cpu=,%mem=,command= -p "${process_ids[@]}" \
            >"${snapshot_dir}/process-tree.txt" 2>&1 || true
        local captured_process_ids=("${process_ids[@]}")
        if ((${#captured_process_ids[@]} > diagnostics_process_capture_limit)); then
            captured_process_ids=("${captured_process_ids[@]:0:${diagnostics_process_capture_limit}}")
            printf 'process_capture_truncated=true\n' >>"${snapshot_dir}/metadata.txt"
        fi
        if command -v lsof >/dev/null 2>&1; then
            for process_id in "${captured_process_ids[@]}"; do
                capture_with_timeout \
                    "${snapshot_dir}/lsof-${process_id}.txt" \
                    "${diagnostics_command_timeout_seconds}" \
                    lsof -p "${process_id}"
            done
        fi
        if command -v jcmd >/dev/null 2>&1; then
            for process_id in "${captured_process_ids[@]}"; do
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
    terminate_process_tree "${child_pid}" 5
}
