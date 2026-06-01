#!/usr/bin/env bash
# Process ownership and lifecycle helpers for repo verification locking.

current_process_matches_lock_environment() {
    local current_lock_dir=''
    local expected_lock_dir=''
    local expected_lock_token=''
    local published_lock_token=''

    current_lock_dir="$(normalized_lock_dir)"
    expected_lock_dir="${!lock_owner_dir_env_var:-}"
    [[ -n "${expected_lock_dir}" ]] || return 1
    [[ "${expected_lock_dir}" == "${current_lock_dir}" ]] || return 1

    expected_lock_token="${!lock_owner_token_env_var:-}"
    [[ -n "${expected_lock_token}" ]] || return 1

    published_lock_token="$(read_lock_token || true)"
    [[ -n "${published_lock_token}" ]] || return 1
    [[ "${published_lock_token}" == "${expected_lock_token}" ]]
}

lock_pid_description() {
    local lock_pid=${1:-}
    local command_line=''

    [[ "${lock_pid}" =~ ^[0-9]+$ ]] || return 0
    command_line="$(ps -o command= -p "${lock_pid}" 2>/dev/null | head -1 || true)"
    if [[ -n "${command_line}" ]]; then
        printf '%s' "${command_line}" | sed -E 's/[[:space:]]+/ /g' | cut -c1-160
    fi
}

current_process_descends_from_pid() {
    local target_pid=${1:-}
    local candidate_pid=''
    local parent_pid=''

    [[ "${target_pid}" =~ ^[0-9]+$ ]] || return 1
    current_shell_pid
    candidate_pid="${lock_support_current_shell_pid_result}"
    while [[ "${candidate_pid}" =~ ^[0-9]+$ ]] && (( candidate_pid > 1 )); do
        if [[ "${candidate_pid}" == "${target_pid}" ]]; then
            return 0
        fi
        parent_pid="$(ps -o ppid= -p "${candidate_pid}" 2>/dev/null | tr -d '[:space:]' || true)"
        [[ -n "${parent_pid}" ]] || return 1
        candidate_pid="${parent_pid}"
    done
    return 1
}

report_lock_conflict() {
    local lock_pid=${1:-}
    local lock_description=''

    if [[ -n "${lock_pid}" ]]; then
        lock_description="$(lock_pid_description "${lock_pid}")"
        if [[ -n "${lock_description}" ]]; then
            printf 'another %s is already running with PID %s (%s); %s\n' \
                "${lock_scope_name}" \
                "${lock_pid}" \
                "${lock_description}" \
                "${lock_scope_advice}" >&2
            return
        fi
        printf 'another %s is already running with PID %s; %s\n' \
            "${lock_scope_name}" \
            "${lock_pid}" \
            "${lock_scope_advice}" >&2
        return
    fi
    printf 'another %s is already starting; %s\n' \
        "${lock_scope_name}" \
        "${lock_scope_advice}" >&2
}

reclaim_stale_lock() {
    rm -rf "${lock_dir}"
    mkdir -p "${lock_dir}"
    write_lock_state
    lock_is_reentrant=false
    lock_owned_by_current_process=true
}

acquire_lock() {
    local attempt=0
    local lock_pid=''

    initialize_lock_paths
    mkdir -p "$(dirname -- "${lock_dir}")"
    if mkdir "${lock_dir}" 2>/dev/null; then
        write_lock_state
        lock_is_reentrant=false
        lock_owned_by_current_process=true
        return 0
    fi

    for ((attempt = 0; attempt < lock_initialization_attempts; attempt++)); do
        if [[ ! -d "${lock_dir}" ]]; then
            if mkdir "${lock_dir}" 2>/dev/null; then
                write_lock_state
                lock_is_reentrant=false
                lock_owned_by_current_process=true
                return 0
            fi
        fi

        lock_pid="$(read_lock_pid || true)"
        if [[ -n "${lock_pid}" ]]; then
            if kill -0 "${lock_pid}" 2>/dev/null; then
                if current_process_matches_lock_environment || current_process_descends_from_pid "${lock_pid}"; then
                    lock_is_reentrant=true
                    lock_owned_by_current_process=false
                    lock_acquired_token="$(read_lock_token || true)"
                    publish_reentrant_lock_environment
                    return 0
                fi
                report_lock_conflict "${lock_pid}"
                exit 1
            fi
            reclaim_stale_lock
            return 0
        fi

        sleep "${lock_initialization_sleep_seconds}"
    done

    if [[ -d "${lock_dir}" ]] && [[ ! -f "${pid_file}" ]]; then
        reclaim_stale_lock
        return 0
    fi

    lock_pid="$(read_lock_pid || true)"
    if [[ -n "${lock_pid}" ]] && kill -0 "${lock_pid}" 2>/dev/null; then
        if current_process_matches_lock_environment || current_process_descends_from_pid "${lock_pid}"; then
            lock_is_reentrant=true
            lock_owned_by_current_process=false
            lock_acquired_token="$(read_lock_token || true)"
            publish_reentrant_lock_environment
            return 0
        fi
        report_lock_conflict "${lock_pid}"
        exit 1
    fi

    report_lock_conflict ''
    exit 1
}

release_owned_lock() {
    local expected_pid=${1:-}
    local expected_token=${2:-}
    local published_pid=''
    local published_token=''

    initialize_lock_paths
    published_pid="$(read_lock_pid || true)"
    published_token="$(read_lock_token || true)"
    [[ -n "${expected_pid}" ]] || return 0
    [[ "${published_pid}" == "${expected_pid}" ]] || return 0
    [[ -n "${expected_token}" ]] || return 0
    [[ "${published_token}" == "${expected_token}" ]] || return 0
    rm -rf "${lock_dir}"
}

cleanup_lock() {
    if [[ "${lock_is_reentrant}" == true ]]; then
        return 0
    fi
    if [[ "${lock_owned_by_current_process}" == true ]]; then
        local owner_pid=''
        current_owner_pid
        owner_pid="${lock_support_current_owner_pid_result}"
        release_owned_lock "${owner_pid}" "${lock_acquired_token}"
    fi
}
