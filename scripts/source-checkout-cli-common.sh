#!/usr/bin/env bash
# Shared wrapper support for the direct-Java and source-checkout CLI launchers.

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

    local is_darwin=false
    case "$(uname -s)" in
        Darwin) is_darwin=true ;;
    esac
    readonly fg_cli_wrapper_is_darwin="${is_darwin}"

    [[ -f "${fg_cli_wrapper_gradle_wrapper_support}" ]] || fg_cli_wrapper_die \
        "missing Gradle wrapper support helper at ${fg_cli_wrapper_gradle_wrapper_support}"

    # shellcheck source=/dev/null
    source "${fg_cli_wrapper_gradle_wrapper_support}"

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
    readonly fg_cli_wrapper_source_checkout_artifact_manifest="$(
        fg_gradle_source_checkout_artifact_manifest_path \
            "${fg_cli_wrapper_repo_root}" \
            'cli' \
            "${fg_cli_wrapper_is_darwin}"
    )"
    readonly fg_cli_wrapper_application_module='fingrind/dev.erst.fingrind.cli.App'
}

fg_cli_wrapper_refresh_raw_jar_if_needed() {
    local refresh_failure_message=$1
    if fg_gradle_source_checkout_artifact_needs_refresh \
        "${fg_cli_wrapper_repo_root}" \
        "${fg_cli_wrapper_source_checkout_artifact_manifest}" \
        "${fg_cli_wrapper_raw_jar}"; then
        (
            cd "${fg_cli_wrapper_repo_root}"
            ./gradlew :cli:writeSourceCheckoutArtifactManifest prepareManagedSqlite --no-daemon --quiet >/dev/null
        ) || fg_cli_wrapper_die "${refresh_failure_message}"
    fi
}

fg_cli_wrapper_verify_raw_jar() {
    local missing_message=$1
    local stale_message=$2

    [[ -f "${fg_cli_wrapper_raw_jar}" ]] || fg_cli_wrapper_die "${missing_message}"
    if fg_gradle_source_checkout_artifact_needs_refresh \
        "${fg_cli_wrapper_repo_root}" \
        "${fg_cli_wrapper_source_checkout_artifact_manifest}" \
        "${fg_cli_wrapper_raw_jar}"; then
        fg_cli_wrapper_die "${stale_message}"
    fi
}

fg_cli_wrapper_exec_java() {
    local runtime_distribution=$1
    shift

    exec java \
        --enable-native-access=fingrind \
        "-Dfingrind.runtime.distribution=${runtime_distribution}" \
        "-Dfingrind.source-checkout.root=${fg_cli_wrapper_repo_root}" \
        "-Dfingrind.source-checkout.build-root=${fg_cli_wrapper_root_build_dir}" \
        --module-path "${fg_cli_wrapper_raw_jar}" \
        --module "${fg_cli_wrapper_application_module}" \
        "$@"
}
