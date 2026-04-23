#!/usr/bin/env bash
# Regress the Windows Gradle wrapper customizations with static checks. The local root gate does
# not have a Windows shell, so this script guards the failure-prone batch surface directly.

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
readonly wrapper_path="${repo_root}/gradlew.bat"

[[ -f "${wrapper_path}" ]] || die "missing Windows Gradle wrapper at ${wrapper_path}"

wrapper_contents="$(<"${wrapper_path}")"

[[ "${wrapper_contents}" == *'setlocal EnableExtensions'* ]] || die \
    "expected gradlew.bat to keep command extensions enabled"
[[ "${wrapper_contents}" != *'EnableDelayedExpansion'* ]] || die \
    "gradlew.bat must not rely on delayed expansion in the FinGrind wrapper prelude"
[[ "${wrapper_contents}" == *'call :scanFinGrindArguments %*'* ]] || die \
    "expected gradlew.bat to scan arguments before injecting FinGrind defaults"
[[ "${wrapper_contents}" == *'call :ensureFinGrindProjectCacheArgument'* ]] || die \
    "expected gradlew.bat to route project-cache setup through a dedicated helper"
[[ "${wrapper_contents}" == *'call :ensureFinGrindBuildLogicArgument'* ]] || die \
    "expected gradlew.bat to route build-logic setup through a dedicated helper"
[[ "${wrapper_contents}" == *'call :ensureFinGrindJacocoArgument'* ]] || die \
    "expected gradlew.bat to route JaCoCo setup through a dedicated helper"
[[ "${wrapper_contents}" == *'call :ensureFinGrindProjectBuildRootArgument'* ]] || die \
    "expected gradlew.bat to route project-build-root setup through a dedicated helper"
[[ "${wrapper_contents}" == *':scanFinGrindArguments'* ]] || die \
    "expected gradlew.bat to define the argument scanner"
[[ "${wrapper_contents}" == *':ensureFinGrindProjectCacheArgument'* ]] || die \
    "expected gradlew.bat to define the project-cache setup helper"
[[ "${wrapper_contents}" == *':ensureFinGrindBuildLogicArgument'* ]] || die \
    "expected gradlew.bat to define the build-logic setup helper"
[[ "${wrapper_contents}" == *':ensureFinGrindJacocoArgument'* ]] || die \
    "expected gradlew.bat to define the JaCoCo setup helper"
[[ "${wrapper_contents}" == *':ensureFinGrindProjectBuildRootArgument'* ]] || die \
    "expected gradlew.bat to define the project-build-root setup helper"
[[ "${wrapper_contents}" == *':resolveFinGrindProjectCacheKey'* ]] || die \
    "expected gradlew.bat to define the cache-key resolver"
[[ "${wrapper_contents}" == *'set "FINGRIND_PROJECT_CACHE_KEY=%APP_HOME%"'* ]] || die \
    "expected gradlew.bat to seed the project-cache key from the full APP_HOME path"
printf '%s' "${wrapper_contents}" | grep -Fq -- 'set "FINGRIND_PROJECT_CACHE_KEY=%FINGRIND_PROJECT_CACHE_KEY:\=_%"' || die \
    "expected gradlew.bat to replace backslashes in the project-cache key"
printf '%s' "${wrapper_contents}" | grep -Fq -- 'set "FINGRIND_PROJECT_CACHE_KEY=%FINGRIND_PROJECT_CACHE_KEY:/=_%"' || die \
    "expected gradlew.bat to replace forward slashes in the project-cache key"
printf '%s' "${wrapper_contents}" | grep -Fq -- '%FINGRIND_GRADLE_BUILD_LOGIC_ARG% %FINGRIND_GRADLE_JACOCO_ARG% %FINGRIND_GRADLE_PROJECT_BUILD_ROOT_ARG% "-Dorg.gradle.appname=%APP_BASE_NAME%" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %FINGRIND_GRADLE_PROJECT_CACHE_ARG% %*' || die \
    "expected gradlew.bat to pass the project-cache argument to Gradle after -jar instead of to the JVM"
[[ "${wrapper_contents}" != *'if /I not "%FINGRIND_HAS_PROJECT_CACHE%"=="true" ('* ]] || die \
    "gradlew.bat must not expand the project-cache path inside a parenthesized block"
[[ "${wrapper_contents}" != *'if /I not "%FINGRIND_HAS_BUILD_LOGIC_DIR%"=="true" ('* ]] || die \
    "gradlew.bat must not expand the build-logic path inside a parenthesized block"
[[ "${wrapper_contents}" != *'if /I not "%FINGRIND_HAS_JACOCO_ROOT%"=="true" ('* ]] || die \
    "gradlew.bat must not expand the JaCoCo path inside a parenthesized block"
[[ "${wrapper_contents}" != *'if /I not "%FINGRIND_HAS_PROJECT_BUILD_ROOT%"=="true" ('* ]] || die \
    "gradlew.bat must not expand the project-build-root path inside a parenthesized block"
[[ "${wrapper_contents}" != *':==_%'* ]] || die \
    "gradlew.bat must not use the fragile equals-sign replacement that breaks cmd parsing"
[[ "${wrapper_contents}" != *':!=_%'* ]] || die \
    "gradlew.bat must not use the fragile exclamation-mark replacement that breaks cmd parsing"
[[ "${wrapper_contents}" != *':^=_%'* ]] || die \
    "gradlew.bat must not use the fragile caret replacement that breaks cmd parsing"
[[ "${wrapper_contents}" != *'System.Security.Cryptography.SHA256'* ]] || die \
    "gradlew.bat must not depend on PowerShell hashing for the Windows project-cache key"
[[ "${wrapper_contents}" != *'[Convert]::ToHexString'* ]] || die \
    "gradlew.bat must not depend on PowerShell hex conversion for the Windows project-cache key"
[[ "${wrapper_contents}" != *'FINGRIND_GRADLE_HASH_SHELL'* ]] || die \
    "gradlew.bat must not carry the old PowerShell hash-launch plumbing"

printf 'gradlew.bat wrapper regression: success\n'
