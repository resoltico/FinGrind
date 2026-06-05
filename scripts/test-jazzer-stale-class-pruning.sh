#!/usr/bin/env bash
# Prove the nested Jazzer build prunes orphaned cached classfiles without a manual clean.

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
readonly gradle_wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"

[[ -f "${gradle_wrapper_support}" ]] || die \
    "missing Gradle wrapper support helper at ${gradle_wrapper_support}"

# shellcheck source=/dev/null
source "${gradle_wrapper_support}"

is_darwin=false
if [[ "$(uname -s)" == 'Darwin' ]]; then
    is_darwin=true
fi

readonly project_cache_dir="$(fg_gradle_project_cache_dir "${repo_root}" "${is_darwin}")"
readonly main_classes_dir="${project_cache_dir}/jazzer-build/classes/java/main/dev/erst/fingrind/cli"
readonly stale_executor_class="${main_classes_dir}/SqliteRoundTripWorkflowConcurrencyCoverage\$ConcurrentWriterExecutor.class"
readonly stale_classifier_class="${main_classes_dir}/SqliteRoundTripWorkflowConcurrencyCoverage\$ConcurrentDecisionClass.class"
readonly live_owner_class="${main_classes_dir}/SqliteRoundTripWorkflowConcurrencyCoverage.class"
readonly live_task_class="${main_classes_dir}/SqliteRoundTripWorkflowConcurrencyCoverage\$ConcurrentCommitTask.class"
readonly compile_log="$(mktemp "${TMPDIR:-/tmp}/fingrind-jazzer-compile.XXXXXX")"

cleanup() {
    rm -f "${compile_log}"
}

trap cleanup EXIT

mkdir -p "${main_classes_dir}"
printf 'orphaned cached class\n' > "${stale_executor_class}"
printf 'orphaned cached class\n' > "${stale_classifier_class}"

if ! fg_run_with_log_heartbeat \
    "jazzer stale-class pruning check: compileJava in progress" \
    "${compile_log}" \
    ./gradlew \
        --project-dir jazzer \
        compileJava \
        --rerun-tasks \
        --no-daemon \
        --no-configuration-cache \
        --console=plain; then
    exit 1
fi

if grep -F 'warning: [module]' "${compile_log}" >/dev/null; then
    cat "${compile_log}" >&2
    die "jazzer compileJava emitted JPMS module warnings"
fi

[[ ! -e "${stale_executor_class}" ]] || die \
    "jazzer compileJava left the seeded orphaned executor helper behind"
[[ ! -e "${stale_classifier_class}" ]] || die \
    "jazzer compileJava left the seeded orphaned classifier helper behind"
[[ -f "${live_owner_class}" ]] || die \
    "jazzer compileJava did not rebuild the concurrency coverage owner class"
[[ -f "${live_task_class}" ]] || die \
    "jazzer compileJava did not rebuild the live concurrent commit task class"

printf 'jazzer stale-class pruning regression: success\n'
