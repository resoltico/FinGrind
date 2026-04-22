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

[[ -f "${verifier}" ]] || die "missing managed SQLite runtime verifier"

cat <<'JSON' | python3 "${verifier}" >/dev/null
{
  "payload": {
    "environment": {
      "sqlite": {
        "libraryMode": "managed-only",
        "requiredMinimumSqliteVersion": "3.53.0",
        "runtimeStatus": "ready",
        "loadedSqliteVersion": "3.53.0",
        "loadedSqlite3mcVersion": "2.3.3"
      }
    }
  }
}
JSON

set +e
failure_output="$(
    cat <<'JSON' | python3 "${verifier}" 2>&1
{
  "payload": {
    "environment": {
      "sqlite": {
        "libraryMode": "managed-only",
        "requiredMinimumSqliteVersion": "3.53.0",
        "runtimeStatus": "missing",
        "loadedSqliteVersion": "3.53.0",
        "loadedSqlite3mcVersion": "2.3.3"
      }
    }
  }
}
JSON
)"
failure_exit=$?
set -e

if [[ ${failure_exit} -eq 0 ]]; then
    die "managed SQLite runtime verifier accepted an invalid runtimeStatus"
fi
printf '%s\n' "${failure_output}" | grep -Fq 'missing ready SQLite runtime status' ||
    die "managed SQLite runtime verifier did not report the runtimeStatus failure"

printf 'managed SQLite runtime verifier regression: success\n'
