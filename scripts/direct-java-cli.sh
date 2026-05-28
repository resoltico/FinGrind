#!/usr/bin/env bash
# Resolve the active CLI build directory and invoke the developer raw JAR surface.

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
readonly wrapper_common="${script_dir}/source-checkout-cli-common.sh"

[[ -f "${wrapper_common}" ]] || {
    printf 'error: missing CLI wrapper support helper at %s\n' "${wrapper_common}" >&2
    exit 1
}

# shellcheck source=/dev/null
source "${wrapper_common}"

fg_cli_wrapper_initialize "${script_dir}"
fg_cli_wrapper_refresh_raw_jar_if_needed \
    "failed to refresh the developer raw JAR from the current checkout"
fg_cli_wrapper_verify_raw_jar \
    "missing developer raw JAR at ${fg_cli_wrapper_raw_jar}; run ./gradlew :cli:shadowJar prepareManagedSqlite" \
    "developer raw JAR at ${fg_cli_wrapper_raw_jar} is not synchronized with the current checkout; rerun ./gradlew :cli:shadowJar prepareManagedSqlite"
fg_cli_wrapper_exec_java direct-java-invocation "$@"
