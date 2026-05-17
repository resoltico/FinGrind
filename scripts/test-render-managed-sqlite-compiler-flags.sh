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

render_expected_flags() {
    python3 - "${1}" <<'PY'
import json
import platform
import pathlib
import sys

document = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
compile_options = document["requiredCompileOptions"]
native_hardening = document["nativeHardening"]
requires_secure_memory_support = document["requiresSecureMemorySupport"]
flags = []
for option in compile_options:
    normalized = option.strip()
    macro = normalized if normalized.startswith("SQLITE_") else "SQLITE_" + normalized
    if "=" not in macro:
        macro += "=1"
    flags.append("-D" + macro)
if requires_secure_memory_support:
    flags.append("-DSQLITE3MC_SECURE_MEMORY=1")
flags.extend(native_hardening["unixCompilerFlags"])
platform_system = platform.system().lower()
if "linux" in platform_system:
    flags.extend(native_hardening["linuxLinkerFlags"])
elif "darwin" in platform_system:
    flags.extend(native_hardening["macosLinkerFlags"])
print(" ".join(flags))
PY
}

actual="$(python3 "${renderer}")"
actual_from_explicit_contract="$(python3 "${renderer}" "${contract_path}")"
expected="$(render_expected_flags "${contract_path}")"
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

temporary_directory="$(mktemp -d)"
trap 'rm -rf -- "${temporary_directory}"' EXIT
contract_without_secure_memory="${temporary_directory}/managed-sqlite-contract.json"
python3 - "${contract_path}" "${contract_without_secure_memory}" <<'PY'
import json
import pathlib
import sys

source = pathlib.Path(sys.argv[1])
target = pathlib.Path(sys.argv[2])
document = json.loads(source.read_text(encoding="utf-8"))
document["requiresSecureMemorySupport"] = False
target.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
PY
without_secure_memory="$(python3 "${renderer}" "${contract_without_secure_memory}")"
expected_without_secure_memory="$(render_expected_flags "${contract_without_secure_memory}")"
if [[ "${without_secure_memory}" != "${expected_without_secure_memory}" ]]; then
    printf 'error: secure-memory toggle rendering drifted\nexpected: %s\nactual:   %s\n' \
        "${expected_without_secure_memory}" "${without_secure_memory}" >&2
    exit 1
fi

printf 'managed SQLite compiler-flag rendering: success\n'
