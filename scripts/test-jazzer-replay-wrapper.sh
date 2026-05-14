#!/usr/bin/env bash
# Reproduce and guard replay/list-findings wrapper behavior for fast-fail and valid replay paths.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

note() {
    printf 'jazzer replay wrapper check: %s\n' "$1"
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
readonly wrapper="${repo_root}/jazzer/bin/replay"
readonly list_findings_wrapper="${repo_root}/jazzer/bin/list-findings"
readonly fuzz_all_wrapper="${repo_root}/jazzer/bin/fuzz-all"
readonly repo_lock_support="${repo_root}/scripts/repo-verification-lock-support.sh"

[[ -x "${wrapper}" ]] || die "missing replay wrapper at ${wrapper}"
[[ -x "${list_findings_wrapper}" ]] || die "missing list-findings wrapper at ${list_findings_wrapper}"
[[ -x "${fuzz_all_wrapper}" ]] || die "missing fuzz-all wrapper at ${fuzz_all_wrapper}"
[[ -x "${repo_lock_support}" ]] || die "missing repo verification lock helper at ${repo_lock_support}"

tmp_dir="$(mktemp -d)"
lock_holder_pid=''
cleanup() {
    if [[ -n "${lock_holder_pid}" ]]; then
        kill "${lock_holder_pid}" 2>/dev/null || true
        wait "${lock_holder_pid}" 2>/dev/null || true
    fi
    rm -rf "${tmp_dir}"
}
trap cleanup EXIT

wait_for_published_lock_owner() {
    local pid_file_path=$1
    local expected_pid=$2

    for _ in $(seq 1 40); do
        if [[ -f "${pid_file_path}" ]]; then
            if [[ "$(tr -d '[:space:]' < "${pid_file_path}")" == "${expected_pid}" ]]; then
                return 0
            fi
        fi
        if ! kill -0 "${expected_pid}" 2>/dev/null; then
            break
        fi
        sleep 0.05
    done
    return 1
}

readonly input_path="${tmp_dir}/input.bin"
printf '%s\n' '{}' > "${input_path}"
readonly missing_file_path="${tmp_dir}/missing-input.bin"
readonly missing_parent_file_path="${tmp_dir}/missing-parent/input.bin"
readonly resolved_missing_file_path="$(cd "${tmp_dir}" && pwd -P)/$(basename "${missing_file_path}")"

note 'unknown-target fast-fail'
set +e
output="$("${wrapper}" missing-target "${input_path}" 2>&1)"
status=$?
set -e

[[ ${status} -eq 1 ]] || die "replay wrapper should fail with exit 1 for an unknown target"
[[ "${output}" == *'Unknown Jazzer run target: missing-target'* ]] ||
    die "replay wrapper did not report the unknown target"
[[ "${output}" != *'Task :'* ]] || die "replay wrapper leaked Gradle task output for an unknown target"
[[ "${output}" != *'BUILD FAILED'* ]] || die "replay wrapper leaked Gradle failure output for an unknown target"

note 'missing-file fast-fail'
set +e
missing_file_output="$("${wrapper}" cli-request "${missing_file_path}" --json 2>&1)"
missing_file_status=$?
set -e

[[ ${missing_file_status} -eq 1 ]] || die "replay wrapper should fail with exit 1 for a missing file"
[[ "${missing_file_output}" == *"Replay input path does not exist: ${resolved_missing_file_path}"* ]] || die \
    "replay wrapper did not report the missing file path"
[[ "${missing_file_output}" != *'NoSuchFileException'* ]] || die \
    "replay wrapper leaked the Java missing-file stacktrace"
[[ "${missing_file_output}" != *'Task :'* ]] || die \
    "replay wrapper leaked Gradle task output for a missing file"

note 'missing-parent fast-fail'
set +e
missing_parent_output="$("${wrapper}" cli-request "${missing_parent_file_path}" --json 2>&1)"
missing_parent_status=$?
set -e

[[ ${missing_parent_status} -eq 1 ]] || die \
    "replay wrapper should fail with exit 1 for a missing parent directory"
[[ "${missing_parent_output}" == *"Replay input path parent directory does not exist: ${missing_parent_file_path}"* ]] || die \
    "replay wrapper did not report the missing parent directory"
[[ "${missing_parent_output}" != *'cd:'* ]] || die \
    "replay wrapper leaked the raw shell cd failure for a missing parent directory"
[[ "${missing_parent_output}" != *'Task :'* ]] || die \
    "replay wrapper leaked Gradle task output for a missing parent directory"

note 'valid replay json path'
set +e
valid_replay_output="$("${wrapper}" cli-request "${input_path}" --json --console=plain 2>&1)"
valid_replay_status=$?
set -e

[[ ${valid_replay_status} -eq 0 ]] || die "replay wrapper should accept a valid cli-request replay invocation"
[[ "${valid_replay_output}" != *'Task :'* ]] || die "replay wrapper leaked Gradle task output for JSON replay"
[[ "${valid_replay_output}" != *'BUILD SUCCESSFUL'* ]] || die "replay wrapper leaked Gradle success output for JSON replay"
[[ "${valid_replay_output}" == *'"harnessKey"'* ]] || die "replay wrapper did not print the replay JSON payload"
[[ "${valid_replay_output}" == *'"outcomeKind"'* ]] || die "replay wrapper omitted outcomeKind from replay JSON"
[[ "${valid_replay_output}" != *'BUILD FAILED'* ]] || die "replay wrapper leaked Gradle failure output for a valid replay"
python3 - <<'PY' "${valid_replay_output}"
import json
import sys

json.loads(sys.argv[1])
PY

note 'list-findings json path'
set +e
list_findings_output="$("${list_findings_wrapper}" cli-request --json --console=plain 2>&1)"
list_findings_status=$?
set -e

[[ ${list_findings_status} -eq 0 ]] || die "list-findings wrapper should accept the supported positional target grammar"
[[ "${list_findings_output}" != *'Task :'* ]] || die "list-findings wrapper leaked Gradle task output for JSON mode"
[[ "${list_findings_output}" != *'BUILD SUCCESSFUL'* ]] || die "list-findings wrapper leaked Gradle success output for JSON mode"
[[ "${list_findings_output}" != *'BUILD FAILED'* ]] || die "list-findings wrapper leaked Gradle failure output"
python3 - <<'PY' "${list_findings_output}"
import json
import sys

payload = json.loads(sys.argv[1])
if not isinstance(payload, list):
    raise SystemExit("list-findings JSON payload was not an array")
PY

note 'list-findings plain path'
set +e
plain_list_findings_output="$("${list_findings_wrapper}" cli-request --console=plain 2>&1)"
plain_list_findings_status=$?
set -e

[[ ${plain_list_findings_status} -eq 0 ]] || die "list-findings wrapper should accept plain output mode"
[[ "${plain_list_findings_output}" != *'Task :clean'* ]] || die \
    "list-findings wrapper unexpectedly cleaned the nested build before read-only classification"

note 'inactive-target rejection path'
set +e
inactive_target_output="$("${list_findings_wrapper}" regression --json --console=plain 2>&1)"
inactive_target_status=$?
set -e

[[ ${inactive_target_status} -eq 1 ]] || die \
    "list-findings wrapper should fail with exit 1 for a non-active target"
[[ "${inactive_target_output}" == *'Unknown active Jazzer run target: regression'* ]] || die \
    "list-findings wrapper did not report the inactive target"
[[ "${inactive_target_output}" != *'Task :'* ]] || die \
    "list-findings wrapper leaked Gradle task output for an inactive target"
[[ "${inactive_target_output}" != *'BUILD FAILED'* ]] || die \
    "list-findings wrapper leaked Gradle failure output for an inactive target"

readonly lock_fixture_dir="${tmp_dir}/jazzer-wrapper-lock"
readonly lock_fixture_pid_file="${lock_fixture_dir}/pid"
(
    set -euo pipefail
    lock_dir="${lock_fixture_dir}"
    pid_file="${lock_fixture_pid_file}"
    # shellcheck source=/dev/null
    source "${repo_lock_support}"
    acquire_lock
    sleep 30
) &
lock_holder_pid=$!
wait_for_published_lock_owner "${lock_fixture_pid_file}" "${lock_holder_pid}" || die \
    "failed to publish the repo verification lock fixture for replay-wrapper regression"

note 'repo lock conflict for replay wrapper'
set +e
lock_conflict_output="$(
    lock_dir="${lock_fixture_dir}" \
        pid_file="${lock_fixture_pid_file}" \
        "${wrapper}" cli-request "${input_path}" --json 2>&1
)"
lock_conflict_status=$?
set -e

[[ ${lock_conflict_status} -eq 1 ]] || die \
    "replay wrapper should fail with exit 1 when the repo verification lock is held"
[[ "${lock_conflict_output}" == *'another FinGrind verification command is already running with PID '* ]] || die \
    "replay wrapper did not report the held repo verification lock"
[[ "${lock_conflict_output}" != *'Unknown Jazzer run target: cli-request'* ]] || die \
    "replay wrapper mislabeled a valid target as unknown during a lock conflict"
[[ "${lock_conflict_output}" != *'Task :'* ]] || die \
    "replay wrapper leaked Gradle task output during a lock conflict"

note 'repo lock conflict for fuzz-all wrapper'
set +e
fuzz_all_lock_output="$(
    lock_dir="${lock_fixture_dir}" \
        pid_file="${lock_fixture_pid_file}" \
        "${fuzz_all_wrapper}" --console=plain 2>&1
)"
fuzz_all_lock_status=$?
set -e

[[ ${fuzz_all_lock_status} -eq 1 ]] || die \
    "fuzz-all should fail with exit 1 when the repo verification lock is held"
[[ "${fuzz_all_lock_output}" == *'another FinGrind verification command is already running with PID '* ]] || die \
    "fuzz-all did not report the held repo verification lock"
[[ "${fuzz_all_lock_output}" != *'No active fuzz harnesses are configured.'* ]] || die \
    "fuzz-all mislabeled a lock conflict as missing active harnesses"

kill "${lock_holder_pid}" 2>/dev/null || true
wait "${lock_holder_pid}" 2>/dev/null || true
lock_holder_pid=''

printf 'jazzer replay wrapper regression: success\n'
