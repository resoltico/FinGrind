#!/usr/bin/env bash
# Shared single-run lock support for top-level FinGrind verification entrypoints.
#
# Source mode:
# - Expects the caller to define `repo_root` or `FG_REPO_ROOT` unless `lock_dir` is already set.
# - Optional caller overrides:
#     lock_dir
#     pid_file
#     lock_scope_name
#     lock_scope_advice
#     lock_owner_pid
#
# CLI mode:
#   scripts/repo-verification-lock-support.sh print-default-lock-dir <repo-root>
#   scripts/repo-verification-lock-support.sh acquire-for-owner <lock-dir> <owner-pid>
#   scripts/repo-verification-lock-support.sh release-owned-lock <lock-dir> <owner-pid> <token>

lock_initialization_attempts=40
lock_initialization_sleep_seconds=0.05
lock_scope_name="${lock_scope_name:-FinGrind verification command}"
lock_scope_advice="${lock_scope_advice:-run one FinGrind verification command at a time}"
lock_is_reentrant=false
lock_owned_by_current_process=false
lock_acquired_token=''
lock_support_current_shell_pid_result=''
lock_support_current_owner_pid_result=''
if [[ -z "${lock_owner_token_env_var+x}" ]]; then
    lock_owner_token_env_var='FINGRIND_REPO_VERIFICATION_LOCK_TOKEN'
fi
if [[ -z "${lock_owner_dir_env_var+x}" ]]; then
    lock_owner_dir_env_var='FINGRIND_REPO_VERIFICATION_LOCK_DIR'
fi
readonly lock_owner_token_env_var
readonly lock_owner_dir_env_var

current_shell_pid() {
    if [[ -n "${BASHPID:-}" ]]; then
        lock_support_current_shell_pid_result="${BASHPID}"
        return 0
    fi
    lock_support_current_shell_pid_result="$(sh -c 'printf "%s\n" "$PPID"')"
}

current_owner_pid() {
    if [[ -n "${lock_owner_pid:-}" ]]; then
        lock_support_current_owner_pid_result="${lock_owner_pid}"
        return 0
    fi
    current_shell_pid
    lock_support_current_owner_pid_result="${lock_support_current_shell_pid_result}"
}

default_lock_home() {
    if [[ -n "${FINGRIND_REPO_VERIFICATION_LOCK_HOME:-}" ]]; then
        printf '%s\n' "${FINGRIND_REPO_VERIFICATION_LOCK_HOME}"
        return
    fi
    if [[ -n "${XDG_CACHE_HOME:-}" ]]; then
        printf '%s/fingrind/repo-verification-locks\n' "${XDG_CACHE_HOME}"
        return
    fi
    if [[ -n "${HOME:-}" ]]; then
        printf '%s/.cache/fingrind/repo-verification-locks\n' "${HOME}"
        return
    fi
    printf '%s/fingrind-repo-verification-locks\n' "${TMPDIR:-/tmp}"
}

hash_text() {
    local value=${1:-}

    if command -v shasum >/dev/null 2>&1; then
        printf '%s' "${value}" | shasum -a 256 | awk '{ print $1 }'
        return
    fi
    if command -v sha256sum >/dev/null 2>&1; then
        printf '%s' "${value}" | sha256sum | awk '{ print $1 }'
        return
    fi
    if command -v openssl >/dev/null 2>&1; then
        printf '%s' "${value}" | openssl dgst -sha256 -r | awk '{ print $1 }'
        return
    fi
    printf '%s' "${value}" | cksum | awk '{ print $1 }'
}

resolve_lock_repo_root() {
    local candidate=''
    local variable_name=''

    for variable_name in repo_root FG_REPO_ROOT run_lock_repo_root; do
        candidate="${!variable_name:-}"
        if [[ -n "${candidate}" ]]; then
            cd -P -- "${candidate}" && pwd
            return 0
        fi
    done
    return 1
}

default_lock_dir_for_repo_root() {
    local repository_root=${1:-}
    local normalized_repo_root=''
    local repo_name=''
    local repo_hash=''

    normalized_repo_root="$(cd -P -- "${repository_root}" && pwd)"
    repo_name="$(basename -- "${normalized_repo_root}" | tr -cs 'A-Za-z0-9._-' '-')"
    [[ -n "${repo_name}" ]] || repo_name='repo'
    repo_hash="$(hash_text "${normalized_repo_root}")"
    printf '%s/%s-%s\n' "$(default_lock_home)" "${repo_name}" "${repo_hash}"
}

initialize_lock_paths() {
    local resolved_repo_root=''

    if [[ -z "${lock_dir:-}" ]]; then
        resolved_repo_root="$(resolve_lock_repo_root)" || {
            printf 'error: repo verification lock support requires repo_root or FG_REPO_ROOT when lock_dir is not preset\n' >&2
            exit 1
        }
        lock_dir="$(default_lock_dir_for_repo_root "${resolved_repo_root}")"
    fi
    if [[ -z "${pid_file:-}" ]]; then
        pid_file="${lock_dir}/pid"
    fi
}

normalized_lock_dir() {
    local parent_dir=''
    local base_name=''

    initialize_lock_paths
    parent_dir="$(cd -P -- "$(dirname -- "${lock_dir}")" && pwd)"
    base_name="$(basename -- "${lock_dir}")"
    printf '%s/%s\n' "${parent_dir}" "${base_name}"
}

lock_token_file_path() {
    initialize_lock_paths
    printf '%s\n' "${lock_dir}/owner-token"
}

generate_lock_token() {
    local owner_pid=''
    current_owner_pid
    owner_pid="${lock_support_current_owner_pid_result}"
    printf '%s-%s-%s-%s\n' \
        "${owner_pid}" \
        "$(date +%s)" \
        "${RANDOM:-0}" \
        "${RANDOM:-0}"
}

export_lock_environment() {
    local current_lock_dir=${1:-}
    local current_lock_token=${2:-}

    export "${lock_owner_dir_env_var}=${current_lock_dir}"
    export "${lock_owner_token_env_var}=${current_lock_token}"
}

write_lock_pid() {
    local owner_pid=''
    current_owner_pid
    owner_pid="${lock_support_current_owner_pid_result}"
    printf '%s\n' "${owner_pid}" > "${pid_file}"
}

write_lock_token() {
    local lock_token=${1:-}
    local lock_token_file=''

    lock_token_file="$(lock_token_file_path)"
    printf '%s\n' "${lock_token}" > "${lock_token_file}"
}

write_lock_state() {
    local normalized_dir=''
    local lock_token=''

    normalized_dir="$(normalized_lock_dir)"
    lock_token="$(generate_lock_token)"
    write_lock_pid
    write_lock_token "${lock_token}"
    lock_acquired_token="${lock_token}"
    export_lock_environment "${normalized_dir}" "${lock_token}"
}

read_lock_pid() {
    initialize_lock_paths
    if [[ -f "${pid_file}" ]]; then
        <"${pid_file}" tr -d '[:space:]'
    fi
}

read_lock_token() {
    local lock_token_file=''

    lock_token_file="$(lock_token_file_path)"
    if [[ -f "${lock_token_file}" ]]; then
        <"${lock_token_file}" tr -d '[:space:]'
    fi
}

publish_reentrant_lock_environment() {
    local normalized_dir=''
    local published_token=''

    published_token="$(read_lock_token || true)"
    [[ -n "${published_token}" ]] || return 0
    normalized_dir="$(normalized_lock_dir)"
    export_lock_environment "${normalized_dir}" "${published_token}"
}

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

repo_verification_lock_cli_usage() {
    cat <<'EOF' >&2
Usage:
  repo-verification-lock-support.sh print-default-lock-dir <repo-root>
  repo-verification-lock-support.sh acquire-for-owner <lock-dir> <owner-pid>
  repo-verification-lock-support.sh release-owned-lock <lock-dir> <owner-pid> <token>
EOF
}

repo_verification_lock_cli() {
    local command=${1:-}

    case "${command}" in
        print-default-lock-dir)
            [[ $# -eq 2 ]] || {
                repo_verification_lock_cli_usage
                exit 1
            }
            default_lock_dir_for_repo_root "$2"
            ;;
        acquire-for-owner)
            [[ $# -eq 3 ]] || {
                repo_verification_lock_cli_usage
                exit 1
            }
            lock_dir="$2"
            pid_file="${lock_dir}/pid"
            lock_owner_pid="$3"
            acquire_lock
            printf 'lock_dir=%s\n' "$(normalized_lock_dir)"
            printf 'owned=%s\n' "${lock_owned_by_current_process}"
            printf 'reentrant=%s\n' "${lock_is_reentrant}"
            printf 'token=%s\n' "$(read_lock_token || true)"
            ;;
        release-owned-lock)
            [[ $# -eq 4 ]] || {
                repo_verification_lock_cli_usage
                exit 1
            }
            lock_dir="$2"
            pid_file="${lock_dir}/pid"
            release_owned_lock "$3" "$4"
            ;;
        *)
            repo_verification_lock_cli_usage
            exit 1
            ;;
    esac
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    set -euo pipefail
    repo_verification_lock_cli "$@"
fi
