#!/usr/bin/env bash
# Shared wrapper support for the direct-Java and source-checkout CLI wrappers.

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    printf '%s\n' "source-checkout-cli-common.sh is a library and must be sourced by a launcher wrapper." >&2
    exit 1
fi

fg_cli_wrapper_die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

fg_cli_wrapper_initialize() {
    local wrapper_script_dir=$1

    readonly fg_cli_wrapper_repo_root="$(cd -P -- "${wrapper_script_dir}/.." && pwd)"
    readonly fg_cli_wrapper_gradle_wrapper_support="${fg_cli_wrapper_repo_root}/scripts/gradle-wrapper-support.sh"
    readonly fg_cli_wrapper_repo_lock_support="${fg_cli_wrapper_repo_root}/scripts/repo-verification-lock-support.sh"

    local is_darwin=false
    case "$(uname -s)" in
        Darwin) is_darwin=true ;;
    esac
    readonly fg_cli_wrapper_is_darwin="${is_darwin}"

    [[ -f "${fg_cli_wrapper_gradle_wrapper_support}" ]] || fg_cli_wrapper_die \
        "missing Gradle wrapper support helper at ${fg_cli_wrapper_gradle_wrapper_support}"
    [[ -f "${fg_cli_wrapper_repo_lock_support}" ]] || fg_cli_wrapper_die \
        "missing repo verification lock helper at ${fg_cli_wrapper_repo_lock_support}"

    # shellcheck source=/dev/null
    source "${fg_cli_wrapper_gradle_wrapper_support}"
    repo_root="${fg_cli_wrapper_repo_root}"
    lock_scope_name='FinGrind CLI runtime prepare'
    lock_scope_advice='run one developer launcher prepare at a time'
    # shellcheck source=/dev/null
    source "${fg_cli_wrapper_repo_lock_support}"

    readonly fg_cli_wrapper_cli_build_dir="$(
        fg_gradle_project_build_dir \
            "${fg_cli_wrapper_repo_root}" \
            'cli' \
            "${fg_cli_wrapper_is_darwin}"
    )"
    readonly fg_cli_wrapper_root_build_dir="$(
        fg_gradle_project_build_dir \
            "${fg_cli_wrapper_repo_root}" \
            'root' \
            "${fg_cli_wrapper_is_darwin}"
    )"
    readonly fg_cli_wrapper_raw_jar="${fg_cli_wrapper_cli_build_dir}/libs/fingrind.jar"
    readonly fg_cli_wrapper_source_checkout_runtime_manifest="$(
        fg_gradle_source_checkout_runtime_manifest_path \
            "${fg_cli_wrapper_repo_root}" \
            'cli' \
            "${fg_cli_wrapper_is_darwin}"
    )"
    fg_cli_wrapper_java_executable=''
    fg_cli_wrapper_application_module=''
    fg_cli_wrapper_native_access_module=''
    fg_cli_wrapper_runtime_input_paths=()
}

fg_cli_wrapper_prepare_runtime_if_needed() {
    local prepare_failure_message=$1
    local force_rerun=false
    if [[ -f "${fg_cli_wrapper_raw_jar}" ]] \
        && fg_cli_wrapper_runtime_manifest_is_usable \
        && fg_cli_wrapper_runtime_inputs_are_fresh; then
        return 0
    fi
    if [[ -f "${fg_cli_wrapper_raw_jar}" ]] \
        && fg_cli_wrapper_runtime_manifest_is_usable \
        && ! fg_cli_wrapper_runtime_inputs_are_fresh; then
        force_rerun=true
    fi
    acquire_lock
    if [[ -f "${fg_cli_wrapper_raw_jar}" ]] \
        && fg_cli_wrapper_runtime_manifest_is_usable \
        && fg_cli_wrapper_runtime_inputs_are_fresh; then
        cleanup_lock
        return 0
    fi
    if [[ -f "${fg_cli_wrapper_raw_jar}" ]] \
        && fg_cli_wrapper_runtime_manifest_is_usable \
        && ! fg_cli_wrapper_runtime_inputs_are_fresh; then
        force_rerun=true
    fi
    if ! (
        cd "${fg_cli_wrapper_repo_root}"
        if [[ "${force_rerun}" == true ]]; then
            ./gradlew :cli:prepareSourceCheckoutCliRuntime --rerun-tasks --quiet >/dev/null 2>&1
        else
            ./gradlew :cli:prepareSourceCheckoutCliRuntime --quiet >/dev/null 2>&1
        fi
    ); then
        cleanup_lock
        fg_cli_wrapper_die "${prepare_failure_message}"
    fi
    cleanup_lock
}

fg_cli_wrapper_verify_raw_jar() {
    local missing_message=$1

    [[ -f "${fg_cli_wrapper_raw_jar}" ]] || fg_cli_wrapper_die "${missing_message}"
}

fg_cli_wrapper_load_runtime_manifest() {
    local missing_message=$1
    local stale_message=$2

    [[ -f "${fg_cli_wrapper_source_checkout_runtime_manifest}" ]] || fg_cli_wrapper_die "${missing_message}"
    fg_cli_wrapper_parse_runtime_manifest || fg_cli_wrapper_die "${stale_message}"

    [[ -n "${fg_cli_wrapper_java_executable}" ]] || fg_cli_wrapper_die "${stale_message}"
    [[ -n "${fg_cli_wrapper_application_module}" ]] || fg_cli_wrapper_die "${stale_message}"
    [[ -n "${fg_cli_wrapper_native_access_module}" ]] || fg_cli_wrapper_die "${stale_message}"
    [[ -x "${fg_cli_wrapper_java_executable}" ]] || fg_cli_wrapper_die "${stale_message}"
}

fg_cli_wrapper_runtime_manifest_is_usable() {
    fg_cli_wrapper_parse_runtime_manifest || return 1
    [[ -n "${fg_cli_wrapper_java_executable}" ]] || return 1
    [[ -n "${fg_cli_wrapper_application_module}" ]] || return 1
    [[ -n "${fg_cli_wrapper_native_access_module}" ]] || return 1
    (( ${#fg_cli_wrapper_runtime_input_paths[@]} > 0 )) || return 1
    [[ -x "${fg_cli_wrapper_java_executable}" ]] || return 1
    return 0
}

fg_cli_wrapper_runtime_inputs_are_fresh() {
    local runtime_input_path=''

    [[ -f "${fg_cli_wrapper_source_checkout_runtime_manifest}" ]] || return 1
    (( ${#fg_cli_wrapper_runtime_input_paths[@]} > 0 )) || return 1
    for runtime_input_path in "${fg_cli_wrapper_runtime_input_paths[@]}"; do
        if [[ -d "${runtime_input_path}" ]]; then
            if find "${runtime_input_path}" -type f -newer \
                "${fg_cli_wrapper_source_checkout_runtime_manifest}" -print -quit 2>/dev/null \
                | grep -q .; then
                return 1
            fi
            continue
        fi
        if [[ ! -f "${runtime_input_path}" ]]; then
            return 1
        fi
        if [[ "${runtime_input_path}" -nt "${fg_cli_wrapper_source_checkout_runtime_manifest}" ]]; then
            return 1
        fi
    done
    return 0
}

fg_cli_wrapper_parse_runtime_manifest() {
    local record_type=''
    local record_value=''
    local format_version=''

    [[ -f "${fg_cli_wrapper_source_checkout_runtime_manifest}" ]] || return 1

    fg_cli_wrapper_java_executable=''
    fg_cli_wrapper_application_module=''
    fg_cli_wrapper_native_access_module=''
    fg_cli_wrapper_runtime_input_paths=()
    while IFS="$(printf '\t')" read -r record_type record_value || [[ -n "${record_type}${record_value}" ]]; do
        case "${record_type}" in
            javaExecutable)
                fg_cli_wrapper_java_executable="${record_value}"
                ;;
            applicationModule)
                fg_cli_wrapper_application_module="${record_value}"
                ;;
            nativeAccessModule)
                fg_cli_wrapper_native_access_module="${record_value}"
                ;;
            runtimeInputPath)
                [[ -n "${record_value}" ]] || return 1
                fg_cli_wrapper_runtime_input_paths+=("${record_value}")
                ;;
            javaInstallationDirectory)
                ;;
            formatVersion=*)
                format_version="${record_type#formatVersion=}"
                ;;
            ownerTask=*|'')
                ;;
            *)
                return 1
                ;;
        esac
    done < "${fg_cli_wrapper_source_checkout_runtime_manifest}"
    [[ "${format_version}" == '4' ]] || return 1
    return 0
}

fg_cli_wrapper_exec_java() {
    local runtime_distribution=$1
    shift

    exec "${fg_cli_wrapper_java_executable}" \
        "--enable-native-access=${fg_cli_wrapper_native_access_module}" \
        "-Dfingrind.runtime.distribution=${runtime_distribution}" \
        "-Dfingrind.source-checkout.root=${fg_cli_wrapper_repo_root}" \
        "-Dfingrind.source-checkout.build-root=${fg_cli_wrapper_root_build_dir}" \
        --module-path "${fg_cli_wrapper_raw_jar}" \
        --module "${fg_cli_wrapper_application_module}" \
        "$@"
}
