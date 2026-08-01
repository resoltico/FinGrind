#!/usr/bin/env bash
# Monitoring loop, stage runner, and final summary helpers for the root check entrypoint.

check_report_stage_ids=()
check_report_stage_exit_codes=()
check_report_stage_elapsed_seconds=()
check_report_stage_warning_counts=()
check_report_stage_log_paths=()
check_report_stage_diagnostics_paths=()
check_report_warning_total=0
current_stage_warning_count=0

emit_stage_warning_manifest() {
    local stage_id=$1
    local log_path=$2
    local compiler_task=''

    current_stage_warning_count=0
    while IFS= read -r compiler_task; do
        [[ -n "${compiler_task}" ]] || continue
        printf '[CHECK-WARNING] stage=%s category=java-compiler source=%s fingerprint=java-compiler:%s\n' \
            "${stage_id}" \
            "${compiler_task}" \
            "${compiler_task}"
        current_stage_warning_count=$((current_stage_warning_count + 1))
    done < <(java_compiler_warning_tasks "${log_path}")
    check_report_warning_total=$((check_report_warning_total + current_stage_warning_count))
}

record_stage_report() {
    local stage_id=$1
    local exit_code=$2
    local elapsed_seconds=$3
    local warning_count=$4
    local log_path=$5
    local diagnostics_path=$6

    check_report_stage_ids+=("${stage_id}")
    check_report_stage_exit_codes+=("${exit_code}")
    check_report_stage_elapsed_seconds+=("${elapsed_seconds}")
    check_report_stage_warning_counts+=("${warning_count}")
    check_report_stage_log_paths+=("${log_path}")
    check_report_stage_diagnostics_paths+=("${diagnostics_path}")
}

emit_check_report() {
    local index=0
    for ((index = 0; index < ${#check_report_stage_ids[@]}; index++)); do
        local stage_status='success'
        if [[ "${check_report_stage_exit_codes[index]}" != '0' ]]; then
            stage_status='failure'
        fi
        printf '[CHECK-REPORT] stage=%s status=%s exit_code=%s elapsed_seconds=%s warning_count=%s log=%s diagnostics=%s\n' \
            "${check_report_stage_ids[index]}" \
            "${stage_status}" \
            "${check_report_stage_exit_codes[index]}" \
            "${check_report_stage_elapsed_seconds[index]}" \
            "${check_report_stage_warning_counts[index]}" \
            "${check_report_stage_log_paths[index]}" \
            "${check_report_stage_diagnostics_paths[index]}"
    done
    printf '[CHECK-WARNING-SUMMARY] total=%s\n' "${check_report_warning_total}"
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
    mkdir -p "${diagnostics_root}"
    : >"${log_path}"
    current_stage_log_path="${log_path}"
    current_stage_diagnostics_directory="${diagnostics_root}"

    printf '[CHECK-PULSE] stage=%s event=start log=%s diagnostics=%s\n' \
        "${stage_id}" \
        "${log_path}" \
        "${diagnostics_root}"

    (
        cd "${project_dir}"
        "$@" > >(tee -a "${log_path}") 2>&1
    ) &
    local child_pid=$!

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

    if (( monitor_exit_code != 0 )); then
        child_exit_code="${monitor_exit_code}"
    fi

    printf '[CHECK-PULSE] stage=%s event=finish exit=%d log=%s\n' \
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
    emit_stage_warning_manifest "${stage_id}" "${log_path}"
    record_stage_report \
        "${stage_id}" \
        "${child_exit_code}" \
        "${stage_elapsed_seconds}" \
        "${current_stage_warning_count}" \
        "${log_path}" \
        "${diagnostics_root}"

    return "${child_exit_code}"
}

emit_final_status() {
    local exit_code=$?
    if declare -F cleanup_lock >/dev/null 2>&1; then
        cleanup_lock
    fi
    local total_elapsed_seconds
    total_elapsed_seconds=$(($(epoch_seconds) - check_started_at))
    [[ "${emit_final_status_enabled}" == true ]] || return 0
    emit_check_report
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
