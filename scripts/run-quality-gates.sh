#!/usr/bin/env bash
# Run the canonical Stage 1 quality gates for the root product build and the included build logic.

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
readonly gradlew="${repo_root}/gradlew"
readonly build_logic_dir="${repo_root}/gradle/build-logic"
readonly repo_lock_support="${repo_root}/scripts/repo-verification-lock-support.sh"
readonly repo_hygiene_verifier="${repo_root}/scripts/verify-repo-hygiene.sh"

print_usage() {
    printf '%s\n' \
        'Usage: ./scripts/run-quality-gates.sh [supported Gradle options]' \
        '' \
        'Runs the canonical Stage 1 verification surface:' \
        '  1. ./scripts/verify-repo-hygiene.sh' \
        '  2. ./gradlew check coverage' \
        '  3. ./gradlew -p gradle/build-logic test' \
        '' \
        'Any remaining arguments are forwarded to both Gradle invocations.' \
        'Use ./check.sh for the full six-stage repository gate.'
}

for argument in "$@"; do
    case "${argument}" in
        -h|--help)
            print_usage
            exit 0
            ;;
    esac
done

[[ -x "${gradlew}" ]] || {
    printf 'error: missing executable Gradle wrapper at %s\n' "${gradlew}" >&2
    exit 1
}
[[ -x "${repo_hygiene_verifier}" ]] || {
    printf 'error: missing executable repo hygiene verifier at %s\n' "${repo_hygiene_verifier}" >&2
    exit 1
}
[[ -d "${build_logic_dir}" ]] || {
    printf 'error: missing included build-logic directory at %s\n' "${build_logic_dir}" >&2
    exit 1
}
[[ -f "${repo_lock_support}" ]] || {
    printf 'error: missing repo verification lock helper at %s\n' "${repo_lock_support}" >&2
    exit 1
}

# shellcheck source=/dev/null
source "${repo_lock_support}"
trap cleanup_lock EXIT
acquire_lock

"${repo_hygiene_verifier}"
"${gradlew}" check coverage "$@"
"${gradlew}" -p "${build_logic_dir}" test "$@"
