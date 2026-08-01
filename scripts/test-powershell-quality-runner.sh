#!/usr/bin/env bash
# Prove that the pinned PowerShell quality runner refuses a silently empty Pester suite.

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
readonly quality_runner="${repo_root}/scripts/invoke-powershell-quality.ps1"
readonly pwsh_executable="${FINGRIND_PWSH_EXECUTABLE:-}"

[[ -f "${quality_runner}" ]] || die "missing PowerShell quality runner at ${quality_runner}"
[[ -n "${pwsh_executable}" && -f "${pwsh_executable}" && -x "${pwsh_executable}" ]] || die \
    'test-powershell-quality-runner.sh requires FINGRIND_PWSH_EXECUTABLE to name the pinned pwsh executable'
production_inventory_json="$(
    FINGRIND_QUALITY_RUNNER_PATH="${quality_runner}" \
        "${pwsh_executable}" -NoLogo -NoProfile -NonInteractive -Command \
        'ConvertTo-Json -Compress -InputObject @($env:FINGRIND_QUALITY_RUNNER_PATH)'
)"

set +e
empty_inventory_output="$(
    "${pwsh_executable}" -NoLogo -NoProfile -NonInteractive -File "${quality_runner}" \
        -ProductionScriptPathsJson "${production_inventory_json}" \
        -PesterTestPathsJson '[]' \
        -PesterManifest unused \
        -PesterVersion 0 \
        -ScriptAnalyzerManifest unused \
        -ScriptAnalyzerVersion 0 \
        2>&1
)"
empty_inventory_status=$?
set -e
[[ ${empty_inventory_status} -ne 0 ]] || die \
    'PowerShell quality runner accepted an empty Pester test inventory'
printf '%s\n' "${empty_inventory_output}" | \
    grep -Fq 'PowerShell Pester-test inventory is empty' || die \
    'PowerShell quality runner did not report the empty Pester test inventory explicitly'

printf 'PowerShell quality runner regression: success\n'
