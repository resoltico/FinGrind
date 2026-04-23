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
[[ "${wrapper_contents}" == *':scanFinGrindArguments'* ]] || die \
    "expected gradlew.bat to define the argument scanner"
[[ "${wrapper_contents}" == *':resolveFinGrindProjectCacheKey'* ]] || die \
    "expected gradlew.bat to define the cache-key resolver"
[[ "${wrapper_contents}" == *'System.Security.Cryptography.SHA256'* ]] || die \
    "expected gradlew.bat to hash APP_HOME for the project-cache key on Windows"
[[ "${wrapper_contents}" != *':==_%'* ]] || die \
    "gradlew.bat must not use the fragile equals-sign replacement that breaks cmd parsing"
[[ "${wrapper_contents}" != *':!=_%'* ]] || die \
    "gradlew.bat must not use the fragile exclamation-mark replacement that breaks cmd parsing"
[[ "${wrapper_contents}" != *':^=_%'* ]] || die \
    "gradlew.bat must not use the fragile caret replacement that breaks cmd parsing"

printf 'gradlew.bat wrapper regression: success\n'
