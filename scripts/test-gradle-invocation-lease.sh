#!/usr/bin/env bash
# Regress the wrapper-owned cross-process Gradle invocation lease without invoking Gradle.

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

await_file() {
    local path="$1"
    local description="$2"
    for _ in $(seq 1 200); do
        if [[ -e "${path}" ]]; then
            return 0
        fi
        sleep 0.05
    done
    die "timed out waiting for ${description}"
}

await_text() {
    local path="$1"
    local expected="$2"
    local description="$3"
    for _ in $(seq 1 200); do
        if grep -Fq "${expected}" "${path}" 2>/dev/null; then
            return 0
        fi
        sleep 0.05
    done
    die "timed out waiting for ${description}"
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly lease_source="${repo_root}/scripts/GradleInvocationLease.java"

[[ -f "${lease_source}" ]] || die "missing Gradle invocation lease source at ${lease_source}"

if [[ -n "${JAVA_HOME:-}" ]]; then
    readonly java_executable="${JAVA_HOME}/bin/java"
else
    readonly java_executable="$(command -v java || true)"
fi
[[ -n "${java_executable}" && -x "${java_executable}" ]] || die \
    "a Java executable is required to test the Gradle invocation lease"

readonly tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-gradle-invocation-lease.XXXXXX")"
holder_pid=""
contender_pid=""
cleanup() {
    for process_pid in "${contender_pid}" "${holder_pid}"; do
        if [[ -n "${process_pid}" ]] && kill -0 "${process_pid}" 2>/dev/null; then
            kill "${process_pid}" 2>/dev/null || true
            wait "${process_pid}" 2>/dev/null || true
        fi
    done
    rm -rf "${tmp_dir}" 2>/dev/null || true
}
trap cleanup EXIT

readonly lease_file="${tmp_dir}/leases/project.lease"
readonly holder_started="${tmp_dir}/holder-started"
readonly holder_release="${tmp_dir}/holder-release"
readonly contender_started="${tmp_dir}/contender-started"
readonly holder_stdout="${tmp_dir}/holder.stdout"
readonly holder_stderr="${tmp_dir}/holder.stderr"
readonly contender_stdout="${tmp_dir}/contender.stdout"
readonly contender_stderr="${tmp_dir}/contender.stderr"

"${java_executable}" "${lease_source}" "${lease_file}" -- /bin/sh -c '
    printf "%s\\n" started > "$1"
    while [ ! -f "$2" ]; do
        sleep 0.05
    done
' ignored "${holder_started}" "${holder_release}" >"${holder_stdout}" 2>"${holder_stderr}" &
holder_pid=$!
await_file "${holder_started}" "the lease holder child"

"${java_executable}" "${lease_source}" "${lease_file}" -- /bin/sh -c '
    printf "%s\\n" acquired > "$1"
' ignored "${contender_started}" >"${contender_stdout}" 2>"${contender_stderr}" &
contender_pid=$!
await_text \
    "${contender_stderr}" \
    "waiting for the shared build-state lease" \
    "the lease contender diagnostic"
[[ ! -e "${contender_started}" ]] || die \
    "a second Gradle invocation entered while the first held the shared lease"

touch "${holder_release}"
wait "${holder_pid}"
holder_pid=""
wait "${contender_pid}"
contender_pid=""
[[ -e "${contender_started}" ]] || die "the lease contender did not run after the holder exited"
grep -Fq "acquired the shared build-state lease" "${contender_stderr}" || die \
    "the lease contender did not report acquisition after waiting"

set +e
"${java_executable}" "${lease_source}" "${lease_file}" -- /bin/sh -c 'exit 37'
exit_status=$?
set -e
[[ ${exit_status} -eq 37 ]] || die \
    "the lease runner did not preserve the Gradle child exit status: ${exit_status}"

readonly shutdown_descendant_pid_path="${tmp_dir}/shutdown-descendant-pid"
readonly shutdown_contender_started="${tmp_dir}/shutdown-contender-started"
readonly shutdown_holder_stderr="${tmp_dir}/shutdown-holder.stderr"
readonly shutdown_contender_stderr="${tmp_dir}/shutdown-contender.stderr"
"${java_executable}" "${lease_source}" "${lease_file}" -- /bin/sh -c '
    sleep 120 &
    descendant_pid=$!
    printf "%s\\n" "$descendant_pid" > "$1"
    trap "exit 0" TERM
    while :; do
        sleep 0.05
    done
' ignored "${shutdown_descendant_pid_path}" >"${tmp_dir}/shutdown-holder.stdout" 2>"${shutdown_holder_stderr}" &
holder_pid=$!
await_file "${shutdown_descendant_pid_path}" "the shutdown-test descendant process"

"${java_executable}" "${lease_source}" "${lease_file}" -- /bin/sh -c '
    printf "%s\\n" acquired > "$1"
' ignored "${shutdown_contender_started}" >"${tmp_dir}/shutdown-contender.stdout" 2>"${shutdown_contender_stderr}" &
contender_pid=$!
await_text \
    "${shutdown_contender_stderr}" \
    "waiting for the shared build-state lease" \
    "the shutdown-test lease contender diagnostic"

kill -TERM "${holder_pid}"
set +e
wait "${holder_pid}"
set -e
holder_pid=""
wait "${contender_pid}"
contender_pid=""
shutdown_descendant_pid="$(<"${shutdown_descendant_pid_path}")"
if kill -0 "${shutdown_descendant_pid}" 2>/dev/null; then
    die "the lease was released before the Gradle child descendant exited"
fi
[[ -e "${shutdown_contender_started}" ]] || die \
    "the shutdown-test lease contender did not run after the child process tree exited"

printf 'Gradle invocation lease regression: success\n'
