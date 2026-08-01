#!/usr/bin/env bash
# Exercise pinned PowerShell quality-tool metadata and archive provisioning without Gradle.

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
readonly repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly python_runtime_support="${repo_root}/scripts/python-runtime-support.sh"
readonly quality_tool_test="${repo_root}/scripts/test_powershell_quality_tools.py"

[[ -f "${python_runtime_support}" ]] || {
    printf 'error: missing Python runtime support at %s\n' "${python_runtime_support}" >&2
    exit 1
}
[[ -f "${quality_tool_test}" ]] || {
    printf 'error: missing PowerShell quality-tool regression at %s\n' "${quality_tool_test}" >&2
    exit 1
}

# shellcheck source=/dev/null
source "${python_runtime_support}"
prepare_python_runtime_env
"${FINGRIND_PYTHON_EXECUTABLE}" "${quality_tool_test}"
