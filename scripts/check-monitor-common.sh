#!/usr/bin/env bash
# Shared formatting and log-inspection helpers for the root check monitor.

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

epoch_seconds() {
    date +%s
}

file_size_bytes() {
    local file_path=$1
    if [[ ! -f "${file_path}" ]]; then
        printf '0'
        return
    fi
    if stat -f '%z' "${file_path}" >/dev/null 2>&1; then
        stat -f '%z' "${file_path}"
        return
    fi
    if stat -c '%s' "${file_path}" >/dev/null 2>&1; then
        stat -c '%s' "${file_path}"
        return
    fi
    wc -c < "${file_path}" | tr -d '[:space:]'
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
