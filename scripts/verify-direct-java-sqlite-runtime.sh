#!/usr/bin/env bash
# Verify the direct-Java managed SQLite runtime contract against the developer direct-Java wrapper.

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
readonly direct_java_wrapper="${script_dir}/direct-java-cli.sh"

[[ -f "${verifier}" ]] || die "missing SQLite runtime verifier at ${verifier}"
[[ -x "${direct_java_wrapper}" ]] || die "missing direct Java wrapper at ${direct_java_wrapper}"

(
    cd "${repo_root}" &&
        ./gradlew :cli:shadowJar prepareManagedSqlite --no-daemon --console=plain >/dev/null
)

environment_output="$(
    cd "${repo_root}" &&
        "${direct_java_wrapper}" environment --output json
)"
if ! verifier_output="$(
    printf '%s\n' "${environment_output}" |
        python3 "${verifier}" \
            --expected-runtime-distribution-key directJavaRuntimeDistribution \
            --expected-runtime-provenance source-checkout-managed \
            --label direct-java-runtime 2>&1
)"; then
    printf '%s\n' "${environment_output}"
    printf '%s\n' "${verifier_output}" >&2
    exit 1
fi
printf '%s\n' "${verifier_output}"
