#!/usr/bin/env bash
# Reproduce and guard the generic SQLite runtime verifier against contract drift.

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
readonly verifier="${script_dir}/verify-sqlite-runtime-contract.py"
readonly contract_values_reader="${script_dir}/read-contract-values.py"

[[ -f "${verifier}" ]] || die "missing SQLite runtime verifier"
[[ -f "${contract_values_reader}" ]] || die "missing contract-values reader"

readonly contract_values_json="$(python3 "${contract_values_reader}")"

verify_payload() {
    local distribution_key=$1
    local provenance=$2
    local label=$3
    local payload

    payload="$(
        FINGRIND_CONTRACT_VALUES_JSON="${contract_values_json}" python3 - "${distribution_key}" "${provenance}" <<'PY'
import json
import os
import sys

contract = json.loads(os.environ["FINGRIND_CONTRACT_VALUES_JSON"])
runtime_surface = contract["runtimeSurface"]
managed_sqlite = contract["managedSqlite"]
distribution_key = sys.argv[1]
runtime_provenance = sys.argv[2]
document = {
    "payload": {
        "distribution": {
            "runtimeDistribution": runtime_surface[distribution_key],
        },
        "sqlite": {
            "libraryMode": runtime_surface["sqliteLibraryMode"],
            "requiredMinimumSqliteVersion": managed_sqlite["requiredMinimumSqliteVersion"],
            "requiredSqliteSourceId": managed_sqlite["requiredSqliteSourceId"],
            "requiredCompileOptions": managed_sqlite["requiredCompileOptions"],
            "forbiddenCompileOptions": managed_sqlite["forbiddenCompileOptions"],
            "requiresSecureMemorySupport": managed_sqlite["requiresSecureMemorySupport"],
            "runtime": {
                "compileOptionsVerification": "verified",
                "status": "ready",
                "runtimeProvenance": runtime_provenance,
                "loadedLibraryPath": "/tmp/libsqlite3.so.0",
                "loadedSqliteVersion": managed_sqlite["requiredMinimumSqliteVersion"],
                "loadedSqlite3mcVersion": managed_sqlite["requiredSqlite3mcVersion"],
                "loadedSqliteSourceId": managed_sqlite["requiredSqliteSourceId"],
            },
        }
    }
}
print(json.dumps(document))
PY
    )"

    printf '%s\n' "${payload}" |
        python3 "${verifier}" \
            --expected-runtime-distribution-key "${distribution_key}" \
            --expected-runtime-provenance "${provenance}" \
            --label "${label}" >/dev/null
}

verify_payload sourceCheckoutRuntimeDistribution source-checkout-managed source-checkout-managed-runtime
verify_payload directJavaRuntimeDistribution environment-configured environment-configured-runtime

set +e
failure_output="$(
    payload="$(
        FINGRIND_CONTRACT_VALUES_JSON="${contract_values_json}" python3 - <<'PY'
import json
import os

contract = json.loads(os.environ["FINGRIND_CONTRACT_VALUES_JSON"])
runtime_surface = contract["runtimeSurface"]
managed_sqlite = contract["managedSqlite"]
document = {
    "payload": {
        "distribution": {
            "runtimeDistribution": runtime_surface["directJavaRuntimeDistribution"],
        },
        "sqlite": {
            "libraryMode": runtime_surface["sqliteLibraryMode"],
            "requiredMinimumSqliteVersion": managed_sqlite["requiredMinimumSqliteVersion"],
            "requiredSqliteSourceId": managed_sqlite["requiredSqliteSourceId"],
            "requiredCompileOptions": managed_sqlite["requiredCompileOptions"],
            "forbiddenCompileOptions": [],
            "requiresSecureMemorySupport": False,
            "runtime": {
                "compileOptionsVerification": "not-verified",
                "status": "missing",
                "runtimeProvenance": "environment-configured",
                "loadedLibraryPath": "/tmp/libsqlite3.so.0",
                "loadedSqliteVersion": managed_sqlite["requiredMinimumSqliteVersion"],
                "loadedSqlite3mcVersion": managed_sqlite["requiredSqlite3mcVersion"],
                "loadedSqliteSourceId": managed_sqlite["requiredSqliteSourceId"],
            },
        }
    }
}
print(json.dumps(document))
PY
    )"
    printf '%s\n' "${payload}" |
        python3 "${verifier}" \
            --expected-runtime-distribution-key sourceCheckoutRuntimeDistribution \
            --expected-runtime-provenance source-checkout-managed \
            --label source-checkout-managed-runtime 2>&1
)"
failure_exit=$?
set -e

if [[ ${failure_exit} -eq 0 ]]; then
    die "SQLite runtime verifier accepted an invalid runtime payload"
fi
printf '%s\n' "${failure_output}" | grep -Fq 'runtime distribution drifted from the canonical contract' ||
    die "SQLite runtime verifier did not report runtime-distribution drift"
printf '%s\n' "${failure_output}" | grep -Fq 'missing ready SQLite runtime status' ||
    die "SQLite runtime verifier did not report the runtime status failure"
printf '%s\n' "${failure_output}" | grep -Fq 'missing verified SQLite compile-options status' ||
    die "SQLite runtime verifier did not report compile-option verification drift"
printf '%s\n' "${failure_output}" | grep -Fq 'missing expected SQLite runtime provenance' ||
    die "SQLite runtime verifier did not report runtime provenance drift"
printf '%s\n' "${failure_output}" | grep -Fq 'missing canonical forbidden SQLite compile options' ||
    die "SQLite runtime verifier did not report forbidden compile-option drift"
printf '%s\n' "${failure_output}" | grep -Fq 'missing canonical SQLite3MC secure-memory requirement' ||
    die "SQLite runtime verifier did not report secure-memory requirement drift"

printf 'SQLite runtime verifier regression: success\n'
