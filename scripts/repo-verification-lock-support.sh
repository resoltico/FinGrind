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

if [[ -z "${FINGRIND_REPO_VERIFICATION_LOCK_SUPPORT_LOADED+x}" ]]; then
    readonly FINGRIND_REPO_VERIFICATION_LOCK_SUPPORT_LOADED=1
    readonly lock_support_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
    source "${lock_support_dir}/repo-verification-lock-path-support.sh"
    source "${lock_support_dir}/repo-verification-lock-process-support.sh"
fi

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
