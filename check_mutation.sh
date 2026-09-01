#!/usr/bin/env bash
# Run the separately scheduled critical mutation-testing gate.

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
        [[ "${source_path}" == /* ]] || source_path="${source_dir}/${source_path}"
    done
    cd -P -- "$(dirname -- "${source_path}")" && pwd
}

require_java_26() {
    local java_version javac_version
    command -v java >/dev/null 2>&1 || die "java is required"
    command -v javac >/dev/null 2>&1 || die "a full Java 26 JDK is required"
    java_version="$(java --version 2>/dev/null | head -1 || true)"
    [[ "${java_version}" =~ ^[^[:space:]]+[[:space:]]+26([.]|[[:space:]]|$) ]] ||
        die "java must report version 26; found '${java_version:-unavailable}'"
    javac_version="$(javac --version 2>/dev/null | head -1 || true)"
    [[ "${javac_version}" =~ ^javac[[:space:]]+26([.]|[[:space:]]|$) ]] ||
        die "javac must report version 26; found '${javac_version:-unavailable}'"
}

validate_arguments() {
    local argument
    for argument in "$@"; do
        case "${argument}" in
            --dry-run|-m)
                die "dry-run is not supported by a mutation verification command: ${argument}"
                ;;
            -Dfingrind.gradle.project-build-root=*|-Dfingrind.gradle.build-logic-dir=*)
                die "build-layout overrides are not supported: ${argument}"
                ;;
            --no-daemon|--no-parallel|--stacktrace|--full-stacktrace|-s|-S|--info|--debug|--warn|--quiet|-i|-q|--scan|--profile|--continue|--no-continue|--build-cache|--no-build-cache|--configuration-cache|--no-configuration-cache|--rerun-tasks|--refresh-dependencies|--offline|--console=*|--warning-mode=*) ;;
            --project-dir|--project-dir=*|-p|-p=*|--build-file|--build-file=*|-b|-b=*|--settings-file|--settings-file=*|-c|-c=*)
                die "project-location overrides are not supported: ${argument}"
                ;;
            -D*|-P*|-*) die "unsupported Gradle option: ${argument}" ;;
            *) die "positional Gradle tasks or values are not supported: ${argument}" ;;
        esac
    done
}

readonly repo_root="$(resolve_script_dir)"
readonly gradlew="${repo_root}/gradlew"
readonly wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"
readonly lock_support="${repo_root}/scripts/repo-verification-lock-support.sh"

if [[ "${1:-}" == '-h' || "${1:-}" == '--help' ]]; then
    printf '%s\n' \
        'Usage: ./check_mutation.sh [supported Gradle options]' \
        '' \
        'Runs the reviewed critical PIT mutationCheck outside the normal ./check.sh gate.' \
        'The command prints the current core and executor report directories on success.'
    exit 0
fi

[[ -x "${gradlew}" ]] || die "missing executable Gradle wrapper at ${gradlew}"
[[ -f "${wrapper_support}" ]] || die "missing Gradle wrapper support at ${wrapper_support}"
[[ -f "${lock_support}" ]] || die "missing verification lock support at ${lock_support}"
require_java_26
validate_arguments "$@"

# shellcheck source=/dev/null
source "${wrapper_support}"
is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac
readonly gradle_user_home="${FINGRIND_MUTATION_GRADLE_USER_HOME:-$(fg_gradle_user_home_dir "${repo_root}" "${is_darwin}")}"
readonly core_report_directory="$(fg_gradle_project_build_dir "${repo_root}" core "${is_darwin}")/reports/pitest"
readonly executor_report_directory="$(fg_gradle_project_build_dir "${repo_root}" executor "${is_darwin}")/reports/pitest"
lock_scope_name='FinGrind mutation verification command'
lock_scope_advice='wait for the active verification command, then rerun ./check_mutation.sh'

# shellcheck source=/dev/null
source "${lock_support}"
trap cleanup_lock EXIT INT TERM
acquire_lock

require_current_report() {
    local report_directory=$1
    [[ -s "${report_directory}/mutations.xml" ]] ||
        die "PIT did not produce mutation evidence at ${report_directory}/mutations.xml"
    [[ -s "${report_directory}/index.html" ]] ||
        die "PIT did not produce HTML evidence at ${report_directory}/index.html"
}

mkdir -p "${gradle_user_home}"
export GRADLE_USER_HOME="${gradle_user_home}"
"${gradlew}" --no-daemon --no-parallel --console=plain mutationCheck "$@"
require_current_report "${core_report_directory}"
require_current_report "${executor_report_directory}"
printf '%s\n' \
    'Mutation check: success' \
    "Core report: ${core_report_directory}" \
    "Executor report: ${executor_report_directory}"
