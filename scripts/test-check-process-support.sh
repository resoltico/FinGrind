#!/usr/bin/env bash
# Reproduce and guard bounded timeout teardown for TERM-ignoring process trees.

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
readonly process_support="${repo_root}/scripts/check-process-support.sh"

[[ -f "${process_support}" ]] || die "missing process support helper"

# shellcheck source=/dev/null
source "${process_support}"

tmp_dir="$(mktemp -d)"
parent_pid=''
child_pid=''
cleanup() {
    if [[ -n "${parent_pid}" ]]; then
        kill -KILL "${parent_pid}" 2>/dev/null || true
    fi
    if [[ -n "${child_pid}" ]]; then
        kill -KILL "${child_pid}" 2>/dev/null || true
    fi
    rm -rf "${tmp_dir}"
}
trap cleanup EXIT

readonly parent_pid_path="${tmp_dir}/parent.pid"
readonly child_pid_path="${tmp_dir}/child.pid"
readonly output_path="${tmp_dir}/capture.txt"
readonly worker_path="${tmp_dir}/ignore_term_tree.py"

cat >"${worker_path}" <<'PY'
import os
import signal
import subprocess
import sys
import time

parent_pid_path = sys.argv[1]
child_pid_path = sys.argv[2]

signal.signal(signal.SIGTERM, signal.SIG_IGN)
child = subprocess.Popen(["bash", "-lc", "trap '' TERM; exec sleep 30"])

with open(parent_pid_path, "w", encoding="utf-8") as parent_file:
    parent_file.write(str(os.getpid()))
with open(child_pid_path, "w", encoding="utf-8") as child_file:
    child_file.write(str(child.pid))

print("ready", flush=True)
time.sleep(30)
PY

CHECK_PROCESS_TIMEOUT_TERM_GRACE_SECONDS=0.2 \
    capture_with_timeout \
    "${output_path}" \
    0.2 \
    python3 "${worker_path}" "${parent_pid_path}" "${child_pid_path}"

[[ -s "${parent_pid_path}" ]] || die "timeout regression did not publish the parent pid"
[[ -s "${child_pid_path}" ]] || die "timeout regression did not publish the child pid"
parent_pid="$(tr -d '[:space:]' < "${parent_pid_path}")"
child_pid="$(tr -d '[:space:]' < "${child_pid_path}")"
[[ "${parent_pid}" =~ ^[0-9]+$ ]] || die "published parent pid was not numeric"
[[ "${child_pid}" =~ ^[0-9]+$ ]] || die "published child pid was not numeric"

if kill -0 "${parent_pid}" 2>/dev/null; then
    die "capture_with_timeout left the TERM-ignoring parent process running"
fi
if kill -0 "${child_pid}" 2>/dev/null; then
    die "capture_with_timeout left the TERM-ignoring child process running"
fi

printf 'check-process-support regression: success\n'
