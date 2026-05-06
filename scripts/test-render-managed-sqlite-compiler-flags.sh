#!/usr/bin/env bash
# Verify that the Docker/compiler flag renderer stays synchronized with the canonical contract.

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
readonly renderer="${script_dir}/render-managed-sqlite-compiler-flags.py"
readonly contract_path="${script_dir}/../contract/src/main/resources/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json"

[[ -f "${renderer}" ]] || {
    printf 'error: missing managed SQLite compiler-flag renderer\n' >&2
    exit 1
}
[[ -f "${contract_path}" ]] || {
    printf 'error: missing managed SQLite contract at %s\n' "${contract_path}" >&2
    exit 1
}

actual="$(python3 "${renderer}")"
actual_from_explicit_contract="$(python3 "${renderer}" "${contract_path}")"
expected='-DSQLITE_THREADSAFE=1 -DSQLITE_OMIT_LOAD_EXTENSION=1 -DSQLITE_TEMP_STORE=3 -DSQLITE_SECURE_DELETE=1 -DSQLITE3MC_SECURE_MEMORY=1'
if [[ "${actual}" != "${expected}" ]]; then
    printf 'error: managed SQLite compiler flags drifted\nexpected: %s\nactual:   %s\n' \
        "${expected}" "${actual}" >&2
    exit 1
fi
if [[ "${actual_from_explicit_contract}" != "${expected}" ]]; then
    printf 'error: explicit-contract managed SQLite compiler flags drifted\nexpected: %s\nactual:   %s\n' \
        "${expected}" "${actual_from_explicit_contract}" >&2
    exit 1
fi

printf 'managed SQLite compiler-flag rendering: success\n'
