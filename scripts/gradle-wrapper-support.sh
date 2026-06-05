#!/bin/sh
# Shared wrapper helpers for keeping wrapper-owned Gradle project state out of the checkout by default.

fg_gradle_has_project_cache_arg() {
    for fg_gradle_arg do
        case "${fg_gradle_arg}" in
            --project-cache-dir|--project-cache-dir=*)
                return 0
                ;;
        esac
    done
    return 1
}

fg_gradle_has_build_logic_dir_property_arg() {
    for fg_gradle_arg do
        case "${fg_gradle_arg}" in
            -Dfingrind.gradle.build-logic-dir=*)
                return 0
                ;;
        esac
    done
    return 1
}

fg_gradle_has_jacoco_root_property_arg() {
    for fg_gradle_arg do
        case "${fg_gradle_arg}" in
            -Dfingrind.gradle.jacoco-root=*)
                return 0
                ;;
        esac
    done
    return 1
}

fg_gradle_has_project_build_root_property_arg() {
    for fg_gradle_arg do
        case "${fg_gradle_arg}" in
            -Dfingrind.gradle.project-build-root=*)
                return 0
                ;;
        esac
    done
    return 1
}

fg_gradle_cache_key() {
    fg_gradle_cache_source=${1:-}
    printf '%s\n' "${fg_gradle_cache_source}" | cksum | awk '{ print $1 }'
}

fg_gradle_project_cache_root() {
    if [ -n "${FINGRIND_GRADLE_PROJECT_CACHE_ROOT:-}" ]; then
        printf '%s\n' "${FINGRIND_GRADLE_PROJECT_CACHE_ROOT}"
        return
    fi
    if [ "${1:-false}" = "true" ] && [ -n "${HOME:-}" ]; then
        printf '%s\n' "${HOME}/Library/Caches/FinGrind/gradle-project-cache"
        return
    fi
    if [ -n "${XDG_CACHE_HOME:-}" ]; then
        printf '%s\n' "${XDG_CACHE_HOME}/fingrind/gradle-project-cache"
        return
    fi
    if [ -n "${HOME:-}" ]; then
        printf '%s\n' "${HOME}/.cache/fingrind/gradle-project-cache"
        return
    fi
    if [ -n "${TMPDIR:-}" ]; then
        printf '%s\n' "${TMPDIR%/}/fingrind-gradle-project-cache"
        return
    fi
    printf '%s\n' '/tmp/fingrind-gradle-project-cache'
}

fg_gradle_project_cache_dir() {
    if [ -n "${FINGRIND_GRADLE_PROJECT_CACHE_DIR:-}" ]; then
        printf '%s\n' "${FINGRIND_GRADLE_PROJECT_CACHE_DIR}"
        return
    fi
    fg_gradle_repo_root=${1:-}
    fg_gradle_is_darwin=${2:-false}
    fg_gradle_cache_root=$(fg_gradle_project_cache_root "${fg_gradle_is_darwin}")
    fg_gradle_repo_key=$(fg_gradle_cache_key "${fg_gradle_repo_root}")
    printf '%s/%s\n' "${fg_gradle_cache_root}" "${fg_gradle_repo_key}"
}

fg_gradle_build_logic_dir() {
    if [ -n "${FINGRIND_GRADLE_BUILD_LOGIC_DIR:-}" ]; then
        printf '%s\n' "${FINGRIND_GRADLE_BUILD_LOGIC_DIR}"
        return
    fi
    fg_gradle_repo_root=${1:-}
    fg_gradle_is_darwin=${2:-false}
    fg_gradle_cache_root=$(fg_gradle_project_cache_root "${fg_gradle_is_darwin}")
    fg_gradle_repo_key=$(fg_gradle_cache_key "${fg_gradle_repo_root}")
    printf '%s/%s/build-logic\n' "${fg_gradle_cache_root}" "${fg_gradle_repo_key}"
}

fg_gradle_jacoco_root() {
    if [ -n "${FINGRIND_GRADLE_JACOCO_ROOT:-}" ]; then
        printf '%s\n' "${FINGRIND_GRADLE_JACOCO_ROOT}"
        return
    fi
    fg_gradle_repo_root=${1:-}
    fg_gradle_is_darwin=${2:-false}
    fg_gradle_cache_root=$(fg_gradle_project_cache_root "${fg_gradle_is_darwin}")
    fg_gradle_repo_key=$(fg_gradle_cache_key "${fg_gradle_repo_root}")
    printf '%s/%s/jacoco\n' "${fg_gradle_cache_root}" "${fg_gradle_repo_key}"
}

fg_gradle_project_build_root() {
    if [ -n "${FINGRIND_GRADLE_PROJECT_BUILD_ROOT:-}" ]; then
        printf '%s\n' "${FINGRIND_GRADLE_PROJECT_BUILD_ROOT}"
        return
    fi
    fg_gradle_repo_root=${1:-}
    fg_gradle_is_darwin=${2:-false}
    fg_gradle_cache_root=$(fg_gradle_project_cache_root "${fg_gradle_is_darwin}")
    fg_gradle_repo_key=$(fg_gradle_cache_key "${fg_gradle_repo_root}")
    printf '%s/%s/project-build\n' "${fg_gradle_cache_root}" "${fg_gradle_repo_key}"
}

fg_gradle_user_home_dir() {
    if [ -n "${FINGRIND_GRADLE_USER_HOME:-}" ]; then
        printf '%s\n' "${FINGRIND_GRADLE_USER_HOME}"
        return
    fi
    fg_gradle_repo_root=${1:-}
    fg_gradle_is_darwin=${2:-false}
    fg_gradle_cache_root=$(fg_gradle_project_cache_root "${fg_gradle_is_darwin}")
    fg_gradle_repo_key=$(fg_gradle_cache_key "${fg_gradle_repo_root}")
    printf '%s/%s/gradle-user-home\n' "${fg_gradle_cache_root}" "${fg_gradle_repo_key}"
}

fg_gradle_should_externalize_project_builds() {
    return 0
}

fg_gradle_project_build_dir() {
    fg_gradle_repo_root=${1:-}
    fg_gradle_project_segment=${2:-root}
    fg_gradle_is_darwin=${3:-false}
    if fg_gradle_should_externalize_project_builds "${fg_gradle_repo_root}"; then
        fg_gradle_build_root=$(fg_gradle_project_build_root "${fg_gradle_repo_root}" "${fg_gradle_is_darwin}")
        printf '%s/%s\n' "${fg_gradle_build_root}" "${fg_gradle_project_segment}"
        return
    fi
    if [ "${fg_gradle_project_segment}" = "root" ]; then
        printf '%s/build\n' "${fg_gradle_repo_root}"
        return
    fi
    printf '%s/%s/build\n' "${fg_gradle_repo_root}" "${fg_gradle_project_segment}"
}

fg_gradle_source_checkout_runtime_manifest_path() {
    fg_gradle_repo_root=${1:-}
    fg_gradle_project_segment=${2:-cli}
    fg_gradle_is_darwin=${3:-false}
    fg_gradle_build_dir=$(fg_gradle_project_build_dir "${fg_gradle_repo_root}" "${fg_gradle_project_segment}" "${fg_gradle_is_darwin}")
    printf '%s/generated/source-checkout/source-checkout-runtime-manifest.tsv\n' "${fg_gradle_build_dir}"
}

fg_gradle_bundle_archive_manifest_path() {
    fg_gradle_repo_root=${1:-}
    fg_gradle_project_segment=${2:-cli}
    fg_gradle_is_darwin=${3:-false}
    fg_gradle_build_dir=$(fg_gradle_project_build_dir "${fg_gradle_repo_root}" "${fg_gradle_project_segment}" "${fg_gradle_is_darwin}")
    printf '%s/generated/bundle/bundle-archive-manifest.json\n' "${fg_gradle_build_dir}"
}

fg_gradle_docker_context_dir() {
    fg_gradle_repo_root=${1:-}
    fg_gradle_project_segment=${2:-cli}
    fg_gradle_is_darwin=${3:-false}
    fg_gradle_build_dir=$(fg_gradle_project_build_dir "${fg_gradle_repo_root}" "${fg_gradle_project_segment}" "${fg_gradle_is_darwin}")
    printf '%s/docker-context\n' "${fg_gradle_build_dir}"
}
