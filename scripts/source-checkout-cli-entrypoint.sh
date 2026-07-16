#!/usr/bin/env bash
# Shared entrypoint wiring for the developer CLI launcher wrappers.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf '%s\n' "source-checkout-cli-entrypoint.sh is a library and must be sourced by a launcher wrapper." >&2
    exit 1
fi

fg_cli_wrapper_resolve_script_dir() {
    local source_path=$1
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

fg_cli_wrapper_bootstrap_entrypoint() {
    local launcher_path=$1
    local launcher_script_dir
    launcher_script_dir="$(fg_cli_wrapper_resolve_script_dir "${launcher_path}")"
    local wrapper_common="${launcher_script_dir}/source-checkout-cli-common.sh"

    [[ -f "${wrapper_common}" ]] || {
        printf 'error: missing CLI wrapper support helper at %s\n' "${wrapper_common}" >&2
        exit 1
    }

    # shellcheck source=/dev/null
    source "${wrapper_common}"
    fg_cli_wrapper_initialize "${launcher_script_dir}"
}

fg_cli_wrapper_launch_source_checkout() {
    local launcher_path=$1
    shift

    fg_cli_wrapper_bootstrap_entrypoint "${launcher_path}"
    fg_cli_wrapper_prepare_runtime_if_needed \
        "failed to prepare the source-checkout wrapper runtime from the current checkout"
    fg_cli_wrapper_verify_raw_jar \
        "missing or invalid source-checkout wrapper JAR at ${fg_cli_wrapper_raw_jar}; run ./gradlew :cli:prepareSourceCheckoutCliRuntime"
    fg_cli_wrapper_load_runtime_manifest \
        "missing source-checkout runtime manifest at ${fg_cli_wrapper_source_checkout_runtime_manifest}; run ./gradlew :cli:prepareSourceCheckoutCliRuntime" \
        "source-checkout runtime manifest at ${fg_cli_wrapper_source_checkout_runtime_manifest} is not synchronized with the prepared runtime; rerun ./gradlew :cli:prepareSourceCheckoutCliRuntime"
    fg_cli_wrapper_exec_java source-checkout-gradle "${launcher_path}" "$@"
}

fg_cli_wrapper_launch_direct_java() {
    local launcher_path=$1
    shift

    fg_cli_wrapper_bootstrap_entrypoint "${launcher_path}"
    fg_cli_wrapper_prepare_runtime_if_needed \
        "failed to prepare the developer direct-Java wrapper runtime from the current checkout"
    fg_cli_wrapper_verify_raw_jar \
        "missing or invalid developer raw JAR at ${fg_cli_wrapper_raw_jar}; run ./gradlew :cli:prepareSourceCheckoutCliRuntime"
    fg_cli_wrapper_load_runtime_manifest \
        "missing source-checkout runtime manifest at ${fg_cli_wrapper_source_checkout_runtime_manifest}; run ./gradlew :cli:prepareSourceCheckoutCliRuntime" \
        "source-checkout runtime manifest at ${fg_cli_wrapper_source_checkout_runtime_manifest} is not synchronized with the prepared runtime; rerun ./gradlew :cli:prepareSourceCheckoutCliRuntime"
    fg_cli_wrapper_exec_java direct-java-invocation "${launcher_path}" "$@"
}
