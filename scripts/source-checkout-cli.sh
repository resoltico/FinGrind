#!/usr/bin/env bash
# Resolve the active CLI build directory and invoke the generated source-checkout launcher.

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
readonly gradle_wrapper_support="${repo_root}/scripts/gradle-wrapper-support.sh"
is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

[[ -f "${gradle_wrapper_support}" ]] || die \
    "missing Gradle wrapper support helper at ${gradle_wrapper_support}"

# shellcheck source=/dev/null
source "${gradle_wrapper_support}"

readonly cli_build_dir="$(fg_gradle_project_build_dir "${repo_root}" 'cli' "${is_darwin}")"
readonly launcher="${cli_build_dir}/install/cli-shadow/bin/cli"

[[ -x "${launcher}" ]] || die \
    "missing generated source-checkout launcher at ${launcher}; run ./gradlew :cli:installShadowDist prepareManagedSqlite"

exec "${launcher}" "$@"
