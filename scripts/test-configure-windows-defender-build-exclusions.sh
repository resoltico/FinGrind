#!/usr/bin/env bash
# Guard the repo-owned Windows Defender exclusion owner and the CI workflow contract that uses it.

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
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly defender_script="${repo_root}/scripts/configure-windows-defender-build-exclusions.ps1"
readonly ci_workflow="${repo_root}/.github/workflows/ci.yml"
readonly developer_doc="${repo_root}/docs/DEVELOPER.md"

[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${defender_script}" ]] || die "missing repo-owned Windows Defender exclusion script at ${defender_script}"
[[ -f "${ci_workflow}" ]] || die "missing CI workflow at ${ci_workflow}"
[[ -f "${developer_doc}" ]] || die "missing developer guide at ${developer_doc}"

grep -Fq 'scripts/test-configure-windows-defender-build-exclusions.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the repo-owned Windows Defender exclusion regression"
grep -Fq 'Get-WindowsDefenderAddPreferenceCommand' "${defender_script}" || die \
    "repo-owned Windows Defender exclusion script no longer resolves the Add-MpPreference owner explicitly"
grep -Fq 'Test-NonFatalWindowsDefenderExclusionFailure' "${defender_script}" || die \
    "repo-owned Windows Defender exclusion script no longer classifies non-fatal Defender service failures"
grep -Fq '0x800106ba' "${defender_script}" || die \
    "repo-owned Windows Defender exclusion script no longer recognizes the unavailable Defender service HRESULT"
grep -Fq '.\scripts\configure-windows-defender-build-exclusions.ps1' "${ci_workflow}" || die \
    "CI workflow no longer delegates Windows Defender build exclusions to the repo-owned script"
grep -Fq 'Configure Windows Defender build exclusions' "${ci_workflow}" || die \
    "CI workflow no longer declares the Windows Defender build-exclusion step"
if grep -Fq 'Add-MpPreference -ExclusionPath "${{ github.workspace }}"' "${ci_workflow}"; then
    die "CI workflow still inlines the workspace Windows Defender exclusion instead of using the repo-owned script"
fi
if grep -Fq 'Add-MpPreference -ExclusionPath "$env:USERPROFILE\.gradle"' "${ci_workflow}"; then
    die "CI workflow still inlines the Gradle-home Windows Defender exclusion instead of using the repo-owned script"
fi
grep -Fq 'best-effort Windows Defender exclusion attempt' "${developer_doc}" || die \
    "developer guide no longer documents the best-effort Windows Defender exclusion theory"

if ! command -v pwsh >/dev/null 2>&1; then
    printf 'repo-owned Windows Defender exclusion regression: skipped (pwsh unavailable)\n'
    exit 0
fi

parse_probe="$(
    pwsh -NoLogo -NoProfile -Command \
        "\$tokens = \$null; \$errors = \$null; [System.Management.Automation.Language.Parser]::ParseFile('${defender_script}', [ref] \$tokens, [ref] \$errors) | Out-Null; if (\$errors.Count -gt 0) { \$errors | ForEach-Object Message; exit 1 }" \
        2>&1
)" || die "repo-owned Windows Defender exclusion script no longer parses as valid PowerShell: ${parse_probe}"

success_output="$(
    REPO_ROOT="${repo_root}" pwsh -NoLogo -NoProfile -Command '
        $scriptPath = Join-Path $env:REPO_ROOT "scripts/configure-windows-defender-build-exclusions.ps1"
        . $scriptPath
        $recorded = [System.Collections.Generic.List[string]]::new()
        Invoke-FinGrindWindowsDefenderBuildExclusionConfiguration `
            -WorkspacePath "C:\workspace" `
            -GradleUserHome "C:\Users\runneradmin\.gradle" `
            -AddPreferenceCommand ([pscustomobject]@{ Name = "Add-MpPreference" }) `
            -AddPreferenceInvoker {
                param([string]$candidatePath)
                $script:recorded.Add($candidatePath) | Out-Null
            }
        [ordered]@{ recorded = $recorded } | ConvertTo-Json -Compress
    '
)"
success_json="$(printf '%s\n' "${success_output}" | tail -n 1)"

python3 - <<'PY' "${success_json}"
import json
import sys

payload = json.loads(sys.argv[1])
expected = ["C:\\workspace", "C:\\Users\\runneradmin\\.gradle"]
if payload["recorded"] != expected:
    raise SystemExit(
        "repo-owned Windows Defender exclusion script drifted: "
        f"{payload['recorded']} != {expected}"
    )
PY

REPO_ROOT="${repo_root}" pwsh -NoLogo -NoProfile -Command '
    $scriptPath = Join-Path $env:REPO_ROOT "scripts/configure-windows-defender-build-exclusions.ps1"
    . $scriptPath
    Invoke-FinGrindWindowsDefenderBuildExclusionConfiguration `
        -WorkspacePath "C:\workspace" `
        -GradleUserHome "C:\Users\runneradmin\.gradle" `
        -AddPreferenceCommand ([pscustomobject]@{ Name = "Add-MpPreference" }) `
        -AddPreferenceInvoker {
            param([string]$candidatePath)
            throw [System.Runtime.InteropServices.COMException]::new(
                "Operation failed with the following error: 0x800106ba.",
                -2147416390
            )
        }
' >/dev/null

set +e
unexpected_failure_output="$(
    REPO_ROOT="${repo_root}" pwsh -NoLogo -NoProfile -Command '
        $scriptPath = Join-Path $env:REPO_ROOT "scripts/configure-windows-defender-build-exclusions.ps1"
        . $scriptPath
        Invoke-FinGrindWindowsDefenderBuildExclusionConfiguration `
            -WorkspacePath "C:\workspace" `
            -GradleUserHome "C:\Users\runneradmin\.gradle" `
            -AddPreferenceCommand ([pscustomobject]@{ Name = "Add-MpPreference" }) `
            -AddPreferenceInvoker {
                param([string]$candidatePath)
                throw "unexpected-defender-error"
            }
    ' 2>&1
)"
unexpected_failure_status=$?
set -e

if [[ ${unexpected_failure_status} -eq 0 ]]; then
    die "repo-owned Windows Defender exclusion script unexpectedly swallowed an unknown failure"
fi
printf '%s\n' "${unexpected_failure_output}" | grep -Fq 'unexpected-defender-error' || die \
    "repo-owned Windows Defender exclusion script no longer preserves unexpected failure evidence"

printf 'repo-owned Windows Defender exclusion regression: success\n'
