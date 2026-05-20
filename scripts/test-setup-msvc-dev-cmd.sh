#!/usr/bin/env bash
# Guard the repo-owned Windows MSVC environment bootstrap and the workflows that depend on it.

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
readonly msvc_setup_script="${repo_root}/scripts/setup-msvc-dev-cmd.ps1"
readonly ci_workflow="${repo_root}/.github/workflows/ci.yml"
readonly release_workflow="${repo_root}/.github/workflows/release.yml"

[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${msvc_setup_script}" ]] || die "missing repo-owned MSVC setup script at ${msvc_setup_script}"
[[ -f "${ci_workflow}" ]] || die "missing CI workflow at ${ci_workflow}"
[[ -f "${release_workflow}" ]] || die "missing release workflow at ${release_workflow}"
grep -Fq 'scripts/test-setup-msvc-dev-cmd.sh' "${stage_contract_script}" || die \
    "check stage contract no longer exercises the repo-owned MSVC setup regression"
grep -Fq 'vswhere.exe' "${msvc_setup_script}" || die \
    "repo-owned MSVC setup script no longer locates Visual Studio through vswhere"
grep -Fq 'VsDevCmd.bat did not publish VSCMD_VER' "${msvc_setup_script}" || die \
    "repo-owned MSVC setup script no longer rejects partial developer-command environments"
grep -Fq 'GITHUB_ENV' "${msvc_setup_script}" || die \
    "repo-owned MSVC setup script no longer exports the configured environment to subsequent steps"
pwsh -NoLogo -NoProfile -Command \
    "[System.Management.Automation.Language.Parser]::ParseFile('${msvc_setup_script}', [ref] \$null, [ref] \$null) | Out-Null" >/dev/null || die \
    "repo-owned MSVC setup script no longer parses as valid PowerShell"

grep -Fq '.\scripts\setup-msvc-dev-cmd.ps1 -Arch x64' "${ci_workflow}" || die \
    "CI workflow no longer bootstraps the Windows MSVC environment through the repo-owned script"
grep -Fq '.\scripts\setup-msvc-dev-cmd.ps1 -Arch x64' "${release_workflow}" || die \
    "release workflow no longer bootstraps the Windows MSVC environment through the repo-owned script"
if grep -Fq 'ilammy/msvc-dev-cmd' "${ci_workflow}"; then
    die "CI workflow still depends on the deprecated third-party msvc-dev-cmd action"
fi
if grep -Fq 'ilammy/msvc-dev-cmd' "${release_workflow}"; then
    die "release workflow still depends on the deprecated third-party msvc-dev-cmd action"
fi

printf 'repo-owned MSVC setup regression: success\n'
