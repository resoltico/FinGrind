#!/usr/bin/env bash
# Prove the nested Jazzer build prunes orphaned processed seed resources without a manual clean.

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

[[ -f "${gradle_wrapper_support}" ]] || die \
    "missing Gradle wrapper support helper at ${gradle_wrapper_support}"

# shellcheck source=/dev/null
source "${gradle_wrapper_support}"

is_darwin=false
if [[ "$(uname -s)" == 'Darwin' ]]; then
    is_darwin=true
fi

readonly project_cache_dir="$(fg_gradle_project_cache_dir "${repo_root}" "${is_darwin}")"
readonly fuzz_resources_dir="${project_cache_dir}/jazzer-build/resources/fuzz/dev/erst/fingrind"
readonly stale_input_resource="${fuzz_resources_dir}/cli/SqliteBookRoundTripFuzzTestInputs/roundTripSingleBook/unicode_valid.json"
readonly stale_metadata_resource="${fuzz_resources_dir}/jazzer/regression-metadata/sqlite-book-roundtrip/unicode_valid.json"
readonly live_input_resource="${fuzz_resources_dir}/cli/SqliteBookRoundTripFuzzTestInputs/roundTripSingleBook/invalid_unicode_account_code.json"
readonly live_metadata_resource="${fuzz_resources_dir}/jazzer/regression-metadata/sqlite-book-roundtrip/invalid_unicode_account_code.json"
readonly process_log="$(mktemp "${TMPDIR:-/tmp}/fingrind-jazzer-process-resources.XXXXXX.log")"

cleanup() {
    rm -f "${process_log}"
}

trap cleanup EXIT

mkdir -p "$(dirname "${stale_input_resource}")" "$(dirname "${stale_metadata_resource}")"
printf 'stale processed seed\n' > "${stale_input_resource}"
printf 'stale processed metadata\n' > "${stale_metadata_resource}"

if ! ./gradlew \
    --project-dir jazzer \
    processFuzzResources \
    --rerun-tasks \
    --no-daemon \
    --no-configuration-cache \
    --console=plain >"${process_log}"; then
    cat "${process_log}" >&2
    exit 1
fi

[[ ! -e "${stale_input_resource}" ]] || die \
    "jazzer processFuzzResources left the seeded orphaned input resource behind"
[[ ! -e "${stale_metadata_resource}" ]] || die \
    "jazzer processFuzzResources left the seeded orphaned metadata resource behind"
[[ -f "${live_input_resource}" ]] || die \
    "jazzer processFuzzResources did not copy the live invalid_unicode_account_code input"
[[ -f "${live_metadata_resource}" ]] || die \
    "jazzer processFuzzResources did not copy the live invalid_unicode_account_code metadata"

printf 'jazzer stale-resource pruning regression: success\n'
