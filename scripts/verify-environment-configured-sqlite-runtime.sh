#!/usr/bin/env bash
# Verify the environment-configured SQLite runtime contract against Gradle JavaExec.

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
readonly verifier="${script_dir}/verify-sqlite-runtime-contract.py"
readonly contract_values_reader="${script_dir}/read-contract-values.py"
readonly gradle_wrapper_support="${script_dir}/gradle-wrapper-support.sh"
readonly direct_java_wrapper="${script_dir}/direct-java-cli.sh"

[[ -f "${verifier}" ]] || die "missing SQLite runtime verifier at ${verifier}"
[[ -f "${contract_values_reader}" ]] || die "missing contract-values reader at ${contract_values_reader}"
[[ -f "${gradle_wrapper_support}" ]] || die "missing Gradle wrapper support helper at ${gradle_wrapper_support}"
[[ -x "${direct_java_wrapper}" ]] || die "missing direct Java wrapper at ${direct_java_wrapper}"

# shellcheck source=/dev/null
source "${gradle_wrapper_support}"

is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

readonly root_build_dir="$(fg_gradle_project_build_dir "${repo_root}" 'root' "${is_darwin}")"
readonly host_sqlite_library_path="$(
    FINGRIND_CONTRACT_VALUES_JSON="$(python3 "${contract_values_reader}")" \
        python3 - "${root_build_dir}" <<'PY'
import json
import os
import pathlib
import sys

contract = json.loads(os.environ["FINGRIND_CONTRACT_VALUES_JSON"])
bundle_target = contract["bundleLayout"]["hostBundleTarget"]
root_build_dir = pathlib.Path(sys.argv[1])
print(
    root_build_dir
    / "managed-sqlite"
    / bundle_target["classifier"]
    / bundle_target["sqliteLibraryFileName"]
)
PY
)"

(
    cd "${repo_root}" &&
        ./gradlew :cli:shadowJar prepareManagedSqlite --no-daemon --console=plain >/dev/null
)
[[ -f "${host_sqlite_library_path}" ]] || die \
    "missing managed SQLite library for environment-configured runtime at ${host_sqlite_library_path}"

capabilities_output="$(
    cd "${repo_root}" &&
        FINGRIND_SQLITE_LIBRARY="${host_sqlite_library_path}" \
            JAVA_TOOL_OPTIONS='-Dfingrind.sqlite.allowEnvironmentConfiguredRuntime=true' \
            "${direct_java_wrapper}" capabilities --output json
)"
if ! verifier_output="$(
    printf '%s\n' "${capabilities_output}" |
        python3 "${verifier}" \
            --expected-runtime-distribution-key directJavaRuntimeDistribution \
            --expected-runtime-provenance environment-configured \
            --label environment-configured-runtime 2>&1
)"; then
    printf '%s\n' "${capabilities_output}"
    printf '%s\n' "${verifier_output}" >&2
    exit 1
fi
printf '%s\n' "${verifier_output}"
