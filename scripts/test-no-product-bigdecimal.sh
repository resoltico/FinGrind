#!/usr/bin/env bash
# Guard the product Java surfaces against generic BigDecimal domain seams.

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

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

readonly script_dir="$(resolve_script_dir)"
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly product_java_dirs=(
    "${repo_root}/core/src/main/java"
    "${repo_root}/contract/src/main/java"
    "${repo_root}/executor/src/main/java"
    "${repo_root}/cli/src/main/java"
    "${repo_root}/sqlite/src/main/java"
    "${repo_root}/report-pdf/src/main/java"
    "${repo_root}/jazzer/src/main/java"
)

for directory in "${product_java_dirs[@]}"; do
    [[ -d "${directory}" ]] || die "missing product Java directory: ${directory}"
done

matches="$(
    grep -R -n -E --include='*.java' '(^|[^[:alnum:]_])BigDecimal([^[:alnum:]_]|$)' \
        "${product_java_dirs[@]}" || true
)"

if [[ -n "${matches}" ]]; then
    printf '%s\n' \
        'error: product Java surfaces must not reintroduce generic BigDecimal seams.' \
        '' \
        'Matched locations:' \
        "${matches}" >&2
    exit 1
fi

printf 'product Java BigDecimal guardrail: success\n'
