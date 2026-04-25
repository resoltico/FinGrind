#!/usr/bin/env bash
# Reproduce and guard the managed SQLite runtime verifier against contract drift.

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
readonly verifier="${script_dir}/verify-managed-sqlite-runtime.py"
readonly contract_values_reader="${script_dir}/read-contract-values.py"

[[ -f "${verifier}" ]] || die "missing managed SQLite runtime verifier"
[[ -f "${contract_values_reader}" ]] || die "missing contract-values reader"

readonly contract_values_json="$(python3 "${contract_values_reader}")"

FINGRIND_CONTRACT_VALUES_JSON="${contract_values_json}" python3 - <<'PY' | python3 "${verifier}" >/dev/null
import json
import os

contract = json.loads(os.environ["FINGRIND_CONTRACT_VALUES_JSON"])
runtime_surface = contract["runtimeSurface"]
managed_sqlite = contract["managedSqlite"]
document = {
    "payload": {
        "environment": {
            "sqlite": {
                "libraryMode": runtime_surface["sqliteLibraryMode"],
                "requiredMinimumSqliteVersion": managed_sqlite["requiredMinimumSqliteVersion"],
                "runtimeStatus": "ready",
                "loadedSqliteVersion": managed_sqlite["requiredMinimumSqliteVersion"],
                "loadedSqlite3mcVersion": managed_sqlite["requiredSqlite3mcVersion"],
            }
        }
    }
}
print(json.dumps(document))
PY

set +e
failure_output="$(
    FINGRIND_CONTRACT_VALUES_JSON="${contract_values_json}" python3 - <<'PY' | python3 "${verifier}" 2>&1
import json
import os

contract = json.loads(os.environ["FINGRIND_CONTRACT_VALUES_JSON"])
runtime_surface = contract["runtimeSurface"]
managed_sqlite = contract["managedSqlite"]
document = {
    "payload": {
        "environment": {
            "sqlite": {
                "libraryMode": runtime_surface["sqliteLibraryMode"],
                "requiredMinimumSqliteVersion": managed_sqlite["requiredMinimumSqliteVersion"],
                "runtimeStatus": "missing",
                "loadedSqliteVersion": managed_sqlite["requiredMinimumSqliteVersion"],
                "loadedSqlite3mcVersion": managed_sqlite["requiredSqlite3mcVersion"],
            }
        }
    }
}
print(json.dumps(document))
PY
)"
failure_exit=$?
set -e

if [[ ${failure_exit} -eq 0 ]]; then
    die "managed SQLite runtime verifier accepted an invalid runtimeStatus"
fi
printf '%s\n' "${failure_output}" | grep -Fq 'missing ready SQLite runtime status' ||
    die "managed SQLite runtime verifier did not report the runtimeStatus failure"

printf 'managed SQLite runtime verifier regression: success\n'
