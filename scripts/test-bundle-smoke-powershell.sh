#!/usr/bin/env bash
# Guard the PowerShell release-surface verifier against Compare-Object scalar/null behavior so
# Windows bundle smoke cannot be the first detector again.

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
readonly bundle_smoke_ps1="${repo_root}/scripts/bundle-smoke.ps1"
readonly bundle_smoke_support_ps1="${repo_root}/scripts/bundle-smoke-support.ps1"
readonly bundle_smoke_common_ps1="${repo_root}/scripts/bundle-smoke-common.ps1"
readonly bundle_smoke_contract_ps1="${repo_root}/scripts/bundle-smoke-contract.ps1"
readonly bundle_smoke_acceptance_ps1="${repo_root}/scripts/bundle-smoke-acceptance.ps1"
readonly bundle_smoke_office_worker_ps1="${repo_root}/scripts/bundle-smoke-office-worker.ps1"
readonly bundle_launcher_ps1="${repo_root}/cli/src/bundle/bin/fingrind.ps1"

[[ -f "${bundle_smoke_ps1}" ]] || die "missing PowerShell bundle smoke script at ${bundle_smoke_ps1}"
[[ -f "${bundle_smoke_support_ps1}" ]] || die \
    "missing PowerShell bundle smoke support script at ${bundle_smoke_support_ps1}"
[[ -f "${bundle_smoke_common_ps1}" ]] || die \
    "missing PowerShell bundle smoke common script at ${bundle_smoke_common_ps1}"
[[ -f "${bundle_smoke_contract_ps1}" ]] || die \
    "missing PowerShell bundle smoke contract script at ${bundle_smoke_contract_ps1}"
[[ -f "${bundle_smoke_acceptance_ps1}" ]] || die \
    "missing PowerShell bundle smoke acceptance script at ${bundle_smoke_acceptance_ps1}"
[[ -f "${bundle_smoke_office_worker_ps1}" ]] || die \
    "missing PowerShell bundle smoke office-worker script at ${bundle_smoke_office_worker_ps1}"
[[ -f "${bundle_launcher_ps1}" ]] || die \
    "missing PowerShell bundle launcher template at ${bundle_launcher_ps1}"
grep -Fq 'bundle-smoke-support.ps1' "${bundle_smoke_ps1}" || die \
    "bundle-smoke.ps1 no longer delegates to the PowerShell support script"
grep -Fq 'bundle-smoke-common.ps1' "${bundle_smoke_support_ps1}" || die \
    "bundle-smoke-support.ps1 no longer delegates to the common helper owner"
grep -Fq 'bundle-smoke-contract.ps1' "${bundle_smoke_support_ps1}" || die \
    "bundle-smoke-support.ps1 no longer delegates to the contract helper owner"
grep -Fq 'bundle-smoke-acceptance.ps1' "${bundle_smoke_support_ps1}" || die \
    "bundle-smoke-support.ps1 no longer delegates to the acceptance helper owner"
grep -Fq 'bundle-smoke-office-worker.ps1' "${bundle_smoke_support_ps1}" || die \
    "bundle-smoke-support.ps1 no longer delegates to the office-worker helper owner"
grep -Fq 'function Test-SameSequence' "${bundle_smoke_common_ps1}" || die \
    "bundle-smoke-common.ps1 no longer defines the sequence-comparison helper"
[[ "$(rg -c 'Compare-Object' "${bundle_smoke_common_ps1}")" == "1" ]] || die \
    "bundle-smoke-common.ps1 should keep Compare-Object usage isolated to the helper"
grep -Fq 'release-smoke-workflow.py' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer delegates to the shared release smoke workflow owner"
grep -Fq 'Rīga büro' "${bundle_smoke_acceptance_ps1}" || die \
    "bundle-smoke-acceptance.ps1 no longer keeps the Unicode workspace-path coverage seam alive"
grep -Fq 'FINGRIND_RELEASE_SMOKE_WORK_ROOT' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the compact shared work-root contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the shared argument-path-mode contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_SCENARIO_ID' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the shared scenario-id contract"
grep -Fq 'ProcessStartInfo' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer uses a ProcessStartInfo-based native launch path"
grep -Fq 'ArgumentList.Add' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer forwards Java arguments through ProcessStartInfo.ArgumentList"
if grep -Fq '& $runtimeJava @javaArguments' "${bundle_launcher_ps1}"; then
    die "fingrind.ps1 regressed to direct native invocation that can corrupt Unicode arguments"
fi
if grep -Fq 'FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG' "${bundle_smoke_office_worker_ps1}"; then
    die "bundle-smoke-office-worker.ps1 still exports legacy per-path release-smoke arguments"
fi

if ! command -v pwsh >/dev/null 2>&1; then
    printf 'bundle smoke PowerShell regression: skipped (pwsh unavailable)\n'
    exit 0
fi

pwsh_script="$(mktemp "${TMPDIR:-/tmp}/fingrind-bundle-smoke-powershell.XXXXXX.ps1")"
trap 'rm -f "${pwsh_script}"' EXIT
cat >"${pwsh_script}" <<'PWSH'
function Test-SameSequence {
    param(
        [Parameter(Mandatory = $true)]
        [object[]] $Reference,
        [Parameter(Mandatory = $true)]
        [object[]] $Actual
    )

    return @(
        Compare-Object -ReferenceObject $Reference -DifferenceObject $Actual
    ).Count -eq 0
}

if (-not (Test-SameSequence -Reference @("linux-x86_64") -Actual @("linux-x86_64"))) {
    throw "same-sequence helper rejected identical singleton arrays"
}
if (Test-SameSequence -Reference @("linux-x86_64") -Actual @("windows-x86_64")) {
    throw "same-sequence helper accepted mismatched arrays"
}
$unicodePath = Join-Path ([System.IO.Path]::GetTempPath()) "workspace odd/Rīga büro/2026 Q2 close"
if ($unicodePath -notmatch 'Rīga büro') {
    throw "PowerShell Unicode workspace path lost the expected non-ASCII coverage segment"
}
PWSH

pwsh -NoLogo -NoProfile -File "${pwsh_script}"

printf 'bundle smoke PowerShell regression: success\n'
