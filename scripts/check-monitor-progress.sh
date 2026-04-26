#!/usr/bin/env bash
# Stage-specific progress summaries and progress markers for the root check monitor.

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
    local deterministic_total_classes
    local completed_deterministic_classes
    local finished_regression_targets
    local latest_pulse

    deterministic_total_classes="$(
        find "${project_dir}/src/test/java" -type f -name '*Test.java' | wc -l | tr -d '[:space:]'
    )"
    completed_deterministic_classes="$(
        grep -c '^\[JAZZER-PULSE\] deterministic-tests phase=class-complete ' "${log_path}" 2>/dev/null || true
    )"
    finished_regression_targets="$(
        grep -c '^\[JAZZER-PULSE\] harness-class=.* phase=finish ' "${log_path}" 2>/dev/null || true
    )"
    latest_pulse="$(grep '^\[JAZZER-PULSE\]' "${log_path}" | tail -1 2>/dev/null || true)"
    if [[ -z "${latest_pulse}" ]]; then
        latest_pulse="$(latest_task_line "${log_path}")"
    fi

    printf 'deterministic-classes=%s/%s regression-targets=%s/%s latest=%s' \
        "${completed_deterministic_classes}" \
        "${deterministic_total_classes}" \
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
