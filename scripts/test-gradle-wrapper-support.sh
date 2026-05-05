#!/usr/bin/env bash
# Regress the shared Gradle wrapper support helpers that keep project cache state off
# fragile checkout filesystems.

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
readonly support_script="${repo_root}/scripts/gradle-wrapper-support.sh"

[[ -f "${support_script}" ]] || die "missing wrapper helper script at ${support_script}"
# shellcheck source=/dev/null
source "${support_script}"

fg_gradle_has_project_cache_arg --stacktrace --project-cache-dir /tmp/cache || die \
    "failed to detect split --project-cache-dir argument"
fg_gradle_has_project_cache_arg --project-cache-dir=/tmp/cache || die \
    "failed to detect inline --project-cache-dir argument"
if fg_gradle_has_project_cache_arg --stacktrace --info; then
    die "incorrectly detected project cache argument when none was present"
fi
fg_gradle_has_build_logic_dir_property_arg -Dfingrind.gradle.build-logic-dir=/tmp/build-logic || die \
    "failed to detect build-logic system property"
if fg_gradle_has_build_logic_dir_property_arg --stacktrace --info; then
    die "incorrectly detected build-logic system property when none was present"
fi
fg_gradle_has_jacoco_root_property_arg -Dfingrind.gradle.jacoco-root=/tmp/jacoco || die \
    "failed to detect JaCoCo system property"
if fg_gradle_has_jacoco_root_property_arg --stacktrace --info; then
    die "incorrectly detected JaCoCo system property when none was present"
fi
fg_gradle_has_project_build_root_property_arg -Dfingrind.gradle.project-build-root=/tmp/project-build || die \
    "failed to detect project-build-root system property"
if fg_gradle_has_project_build_root_property_arg --stacktrace --info; then
    die "incorrectly detected project-build-root system property when none was present"
fi
fg_gradle_should_externalize_project_builds_for_filesystem_type smbfs || die \
    "failed to flag smbfs as requiring externalized project builds"
if fg_gradle_should_externalize_project_builds_for_filesystem_type apfs; then
    die "incorrectly flagged apfs as requiring externalized project builds"
fi

readonly sample_network_repo_root="/Volumes/erst/Tools/FinGrind"
readonly sample_local_repo_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-gradle-wrapper-support.XXXXXX")"
cleanup() {
    rm -rf "${sample_local_repo_root}" 2>/dev/null || true
}
trap cleanup EXIT

readonly expected_repo_key="$(fg_gradle_cache_key "${sample_network_repo_root}")"
readonly expected_linux_root="/cache-root/fingrind/gradle-project-cache"
readonly expected_linux_dir="${expected_linux_root}/${expected_repo_key}"

actual_linux_dir="$(
        HOME='/tmp/fingrind-home' \
        XDG_CACHE_HOME='/cache-root' \
        TMPDIR='/tmp/fingrind-tmp' \
        FINGRIND_GRADLE_PROJECT_CACHE_ROOT='' \
        FINGRIND_GRADLE_PROJECT_CACHE_DIR='' \
        fg_gradle_project_cache_dir "${sample_network_repo_root}" false
)"
[[ "${actual_linux_dir}" == "${expected_linux_dir}" ]] || die \
    "unexpected Linux cache directory: ${actual_linux_dir}"

actual_darwin_root="$(
    HOME='/Users/fingrind' \
        XDG_CACHE_HOME='/cache-root' \
        TMPDIR='/tmp/fingrind-tmp' \
        FINGRIND_GRADLE_PROJECT_CACHE_ROOT='' \
        FINGRIND_GRADLE_PROJECT_CACHE_DIR='' \
        fg_gradle_project_cache_root true
)"
[[ "${actual_darwin_root}" == '/Users/fingrind/Library/Caches/FinGrind/gradle-project-cache' ]] || die \
    "unexpected macOS cache root: ${actual_darwin_root}"

actual_override_dir="$(
        FINGRIND_GRADLE_PROJECT_CACHE_DIR='/override/project-cache' \
        fg_gradle_project_cache_dir "${sample_network_repo_root}" false
)"
[[ "${actual_override_dir}" == '/override/project-cache' ]] || die \
    "project cache directory override was not honored"

actual_override_root="$(
        FINGRIND_GRADLE_PROJECT_CACHE_ROOT='/override/root' \
        fg_gradle_project_cache_dir "${sample_network_repo_root}" false
)"
[[ "${actual_override_root}" == "/override/root/${expected_repo_key}" ]] || die \
    "project cache root override was not honored"

actual_build_logic_dir="$(
    HOME='/tmp/fingrind-home' \
        XDG_CACHE_HOME='/cache-root' \
        TMPDIR='/tmp/fingrind-tmp' \
        FINGRIND_GRADLE_BUILD_LOGIC_DIR='' \
        fg_gradle_build_logic_dir "${sample_network_repo_root}" false
)"
[[ "${actual_build_logic_dir}" == "${expected_linux_dir}/build-logic" ]] || die \
    "unexpected build-logic directory: ${actual_build_logic_dir}"

actual_jacoco_root="$(
    HOME='/tmp/fingrind-home' \
        XDG_CACHE_HOME='/cache-root' \
        TMPDIR='/tmp/fingrind-tmp' \
        FINGRIND_GRADLE_JACOCO_ROOT='' \
        fg_gradle_jacoco_root "${sample_network_repo_root}" false
)"
[[ "${actual_jacoco_root}" == "${expected_linux_dir}/jacoco" ]] || die \
    "unexpected JaCoCo root: ${actual_jacoco_root}"

actual_project_build_root="$(
    HOME='/tmp/fingrind-home' \
        XDG_CACHE_HOME='/cache-root' \
        TMPDIR='/tmp/fingrind-tmp' \
        FINGRIND_GRADLE_PROJECT_BUILD_ROOT='' \
        fg_gradle_project_build_root "${sample_network_repo_root}" false
)"
[[ "${actual_project_build_root}" == "${expected_linux_dir}/project-build" ]] || die \
    "unexpected project build root: ${actual_project_build_root}"

actual_gradle_user_home="$(
    HOME='/tmp/fingrind-home' \
        XDG_CACHE_HOME='/cache-root' \
        TMPDIR='/tmp/fingrind-tmp' \
        FINGRIND_GRADLE_USER_HOME='' \
        fg_gradle_user_home_dir "${sample_network_repo_root}" false
)"
[[ "${actual_gradle_user_home}" == "${expected_linux_dir}/gradle-user-home" ]] || die \
    "unexpected Gradle user home directory: ${actual_gradle_user_home}"

actual_override_gradle_user_home="$(
    FINGRIND_GRADLE_USER_HOME='/override/gradle-user-home' \
        fg_gradle_user_home_dir "${sample_network_repo_root}" false
)"
[[ "${actual_override_gradle_user_home}" == '/override/gradle-user-home' ]] || die \
    "Gradle user home override was not honored"

actual_local_cli_build_dir="$(
    FINGRIND_GRADLE_PROJECT_BUILD_ROOT='' \
        fg_gradle_project_build_dir "${sample_local_repo_root}" 'cli' false
)"
[[ "${actual_local_cli_build_dir}" == "${sample_local_repo_root}/cli/build" ]] || die \
    "unexpected local cli build directory: ${actual_local_cli_build_dir}"

actual_filesystem_externalized_cli_build_dir="$(
    HOME='/tmp/fingrind-home' \
        XDG_CACHE_HOME='/cache-root' \
        TMPDIR='/tmp/fingrind-tmp' \
        FINGRIND_GRADLE_FILESYSTEM_TYPE='smbfs' \
        FINGRIND_GRADLE_PROJECT_BUILD_ROOT='' \
        fg_gradle_project_build_dir "${sample_network_repo_root}" 'cli' false
)"
[[ "${actual_filesystem_externalized_cli_build_dir}" == "${expected_linux_dir}/project-build/cli" ]] || die \
    "unexpected filesystem-externalized cli build directory: ${actual_filesystem_externalized_cli_build_dir}"

actual_external_cli_build_dir="$(
    FINGRIND_GRADLE_PROJECT_BUILD_ROOT='/override/project-build-root' \
        fg_gradle_project_build_dir "${sample_network_repo_root}" 'cli' false
)"
[[ "${actual_external_cli_build_dir}" == '/override/project-build-root/cli' ]] || die \
    "unexpected externalized cli build directory: ${actual_external_cli_build_dir}"

printf 'gradle-wrapper-support regression: success\n'
