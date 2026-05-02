#!/usr/bin/env bash
# Keep the shared repo-verification lock wired into every top-level verification entrypoint.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

resolve_script_dir() {
    local source_path="${BASH_SOURCE[0]}"
    while [[ -h "${source_path}" ]]; do
        local source_dir
        source_dir="$(cd -P -- "$(dirname -- "${source_path}")" && pwd)"
        source_path="$(readlink "${source_path}")"
        if [[ "${source_path}" != /* ]]; then
            source_path="${source_dir}/${source_path}"
        fi
    done
    cd -P -- "$(dirname -- "${source_path}")" && pwd
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly lock_support="${repo_root}/scripts/repo-verification-lock-support.sh"

[[ -f "${lock_support}" ]] || die "missing repo verification lock helper"
grep -Fq 'repo-verification-lock-support.sh' "${repo_root}/check.sh" || die \
    "check.sh no longer sources the repo verification lock helper"
grep -Fq 'repo-verification-lock-support.sh' "${repo_root}/scripts/run-quality-gates.sh" || die \
    "run-quality-gates.sh no longer sources the repo verification lock helper"
grep -Fq 'repo-verification-lock-support.sh' "${repo_root}/scripts/docker-smoke.sh" || die \
    "docker-smoke.sh no longer sources the repo verification lock helper"
grep -Fq 'repo-verification-lock-support.sh' "${repo_root}/scripts/validate-devcontainer.sh" || die \
    "validate-devcontainer.sh no longer sources the repo verification lock helper"
grep -Fq 'scripts/repo-verification-lock-support.sh' "${repo_root}/jazzer/bin/_run-lock-support" || die \
    "Jazzer lock support no longer routes through the repo verification lock helper"

default_lock_dir="$("${lock_support}" print-default-lock-dir "${repo_root}")"
[[ -n "${default_lock_dir}" ]] || die "default repo verification lock directory was empty"
case "${default_lock_dir}" in
    "${repo_root}"|${repo_root}/*)
        die "default repo verification lock directory moved back under the repository tree"
        ;;
esac

tmp_dir="$(mktemp -d)"
cleanup() {
    rm -rf "${tmp_dir}"
}
trap cleanup EXIT

lock_dir="${tmp_dir}/repo-lock"
pid_file="${lock_dir}/pid"
lock_token_file="${lock_dir}/owner-token"

# shellcheck source=/dev/null
source "${lock_support}"

mkdir -p "${lock_dir}"
printf '999999\n' > "${pid_file}"
acquire_lock
[[ -d "${lock_dir}" ]] || die "stale lock recovery removed the repo verification lock directory"
[[ "$(tr -d '[:space:]' < "${pid_file}")" == "$$" ]] || die \
    "stale lock recovery did not rewrite the repo verification lock pid file"
[[ -f "${lock_token_file}" ]] || die "stale lock recovery did not publish the repo verification lock token"
cleanup_lock

mkdir -p "${lock_dir}"
printf '%s\n' "$$" > "${pid_file}"
acquire_lock
[[ "${lock_is_reentrant}" == true ]] || die "self-reentrant repo verification lock acquisition was not allowed"
cleanup_lock
rm -rf "${lock_dir}"

acquire_lock
descendant_output=''
set +e
descendant_output="$(
    lock_dir="${lock_dir}" pid_file="${pid_file}" bash -lc '
        set -euo pipefail
        source "'"${lock_support}"'"
        bash -lc '"'"'
            set -euo pipefail
            source "'"${lock_support}"'"
            acquire_lock
            [[ "${lock_is_reentrant}" == true ]] || exit 91
            printf "%s\n" "process-substitution descendant reentrant acquisition: success"
        '"'"' > >(cat) 2>&1
    ' 2>&1
)"
descendant_status=$?
set -e
cleanup_lock
rm -rf "${lock_dir}"
[[ ${descendant_status} -eq 0 ]] || die \
    "process-substitution descendant repo verification lock acquisition did not remain reentrant"
printf '%s' "${descendant_output}" | grep -F 'process-substitution descendant reentrant acquisition: success' >/dev/null || die \
    "process-substitution descendant repo verification lock acquisition did not complete successfully"

owner_pid=''
publisher_pid=''
mkdir -p "${lock_dir}"
sleep 2 &
owner_pid=$!
(
    sleep 0.2
    printf '%s\n' "${owner_pid}" > "${pid_file}"
) &
publisher_pid=$!

contender_output=''
set +e
contender_output="$(
    lock_dir="${lock_dir}" pid_file="${pid_file}" bash -lc '
        set -euo pipefail
        source "'"${lock_support}"'"
        acquire_lock
    ' 2>&1
)"
contender_status=$?
set -e

wait "${publisher_pid}"
kill "${owner_pid}" 2>/dev/null || true
wait "${owner_pid}" 2>/dev/null || true

[[ ${contender_status} -ne 0 ]] || die "concurrent contender unexpectedly acquired the repo verification lock"
printf '%s' "${contender_output}" | grep -F "already running with PID ${owner_pid}" >/dev/null || die \
    "concurrent contender did not report the active repo verification owner"
printf '%s' "${contender_output}" | grep -F 'FinGrind verification command' >/dev/null || die \
    "concurrent contender did not report the repo verification lock scope"

jazzer_lock_dir="${tmp_dir}/jazzer-wrapper-lock"
jazzer_pid_file="${jazzer_lock_dir}/pid"
(
    set -euo pipefail
    lock_dir="${jazzer_lock_dir}"
    pid_file="${jazzer_pid_file}"
    # shellcheck source=/dev/null
    source "${lock_support}"
    acquire_lock
    sleep 20
) &
jazzer_owner_pid=$!

for _ in $(seq 1 40); do
    [[ -f "${jazzer_pid_file}" ]] && break
    sleep 0.05
done
[[ -f "${jazzer_pid_file}" ]] || die "failed to publish the Jazzer wrapper lock fixture"
[[ "$(tr -d '[:space:]' < "${jazzer_pid_file}")" == "${jazzer_owner_pid}" ]] || die \
    "background helper lock owner pid did not match the actual background lock-holder process"

set +e
jazzer_wrapper_output="$(
    lock_dir="${jazzer_lock_dir}" \
        pid_file="${jazzer_pid_file}" \
        "${repo_root}/jazzer/bin/check" \
        --no-daemon \
        --console=plain 2>&1
)"
jazzer_wrapper_status=$?
set -e

kill "${jazzer_owner_pid}" 2>/dev/null || true
wait "${jazzer_owner_pid}" 2>/dev/null || true

[[ ${jazzer_wrapper_status} -ne 0 ]] || die \
    "supported Jazzer check wrapper unexpectedly ignored the repo verification lock"
printf '%s' "${jazzer_wrapper_output}" | grep -F 'another FinGrind verification command is already running' >/dev/null || die \
    "supported Jazzer check wrapper did not report the repo verification lock conflict"

kill "${jazzer_owner_pid}" 2>/dev/null || true
wait "${jazzer_owner_pid}" 2>/dev/null || true

printf 'repo-verification-lock regression: success\n'
