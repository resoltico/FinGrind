#!/usr/bin/env bash
# Ensure normal SQLite verification tasks do not rewrite committed fixture resources.

set -euo pipefail

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
readonly dry_run_output="$(mktemp)"

cleanup() {
    rm -f "${dry_run_output}"
}
trap cleanup EXIT

assert_task_graph_omits_refresh() {
    local task_path=$1
    ./gradlew --no-daemon --console=plain "${task_path}" --dry-run >"${dry_run_output}"
    if grep -Fq ":sqlite:refreshProtectedBookFixture" "${dry_run_output}"; then
        printf 'error: %s task graph must not invoke :sqlite:refreshProtectedBookFixture\n' "${task_path}" >&2
        cat "${dry_run_output}" >&2
        exit 1
    fi
}

cd "${repo_root}"
assert_task_graph_omits_refresh ":sqlite:test"
assert_task_graph_omits_refresh ":sqlite:pmdTest"

./gradlew --no-daemon --console=plain :sqlite:refreshProtectedBookFixture --dry-run \
    >"${dry_run_output}"
grep -Fq ":sqlite:refreshProtectedBookFixture SKIPPED" "${dry_run_output}" || {
    printf 'error: explicit fixture refresh task is missing from the SQLite task graph\n' >&2
    cat "${dry_run_output}" >&2
    exit 1
}

printf 'SQLite fixture refresh wiring regression: success\n'
