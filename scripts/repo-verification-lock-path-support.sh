#!/usr/bin/env bash
# Path, token, and persisted-state helpers for repo verification locking.

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
