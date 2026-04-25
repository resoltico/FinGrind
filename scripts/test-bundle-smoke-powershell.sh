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

[[ -f "${bundle_smoke_ps1}" ]] || die "missing PowerShell bundle smoke script at ${bundle_smoke_ps1}"
grep -Fq 'function Test-SameSequence' "${bundle_smoke_ps1}" || die \
    "bundle-smoke.ps1 no longer defines the sequence-comparison helper"
[[ "$(rg -c 'Compare-Object' "${bundle_smoke_ps1}")" == "1" ]] || die \
    "bundle-smoke.ps1 should keep Compare-Object usage isolated to the helper"

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
PWSH

pwsh -NoLogo -NoProfile -File "${pwsh_script}"

printf 'bundle smoke PowerShell regression: success\n'
