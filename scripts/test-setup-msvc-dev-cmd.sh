#!/usr/bin/env bash
# Guard the repo-owned Windows MSVC environment bootstrap and its pure policy owner.

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

script_dir="$(resolve_script_dir)"
readonly script_dir
repo_root="$(cd -P -- "${script_dir}/.." && pwd)"
readonly repo_root
readonly stage_contract_script="${repo_root}/scripts/check-stage-contract.sh"
readonly msvc_setup_script="${repo_root}/scripts/setup-msvc-dev-cmd.ps1"
readonly msvc_setup_support_script="${repo_root}/scripts/setup-msvc-dev-cmd-support.ps1"
readonly msvc_setup_policy_transport_script="${repo_root}/scripts/msvc_setup_policy.py"
readonly msvc_setup_policy_models_script="${repo_root}/scripts/msvc_setup_policy_models.py"
readonly msvc_setup_policy_discovery_script="${repo_root}/scripts/msvc_setup_policy_discovery.py"
readonly msvc_setup_policy_environment_script="${repo_root}/scripts/msvc_setup_policy_environment.py"
readonly msvc_setup_policy_tests=(
    "${repo_root}/scripts/test_msvc_setup_policy.py"
    "${repo_root}/scripts/test_msvc_setup_policy_discovery.py"
    "${repo_root}/scripts/test_msvc_setup_policy_environment.py"
)
readonly ci_workflow="${repo_root}/.github/workflows/ci.yml"
readonly release_workflow="${repo_root}/.github/workflows/release.yml"

[[ -f "${stage_contract_script}" ]] || die "missing check stage contract helper at ${stage_contract_script}"
[[ -f "${msvc_setup_script}" ]] || die "missing repo-owned MSVC setup script at ${msvc_setup_script}"
[[ -f "${msvc_setup_support_script}" ]] || die \
    "missing repo-owned MSVC setup support script at ${msvc_setup_support_script}"
for msvc_setup_policy_owner in \
    "${msvc_setup_policy_transport_script}" \
    "${msvc_setup_policy_models_script}" \
    "${msvc_setup_policy_discovery_script}" \
    "${msvc_setup_policy_environment_script}"; do
    [[ -f "${msvc_setup_policy_owner}" ]] || die \
        "missing repo-owned pure MSVC setup policy owner at ${msvc_setup_policy_owner}"
done
for msvc_setup_policy_test in "${msvc_setup_policy_tests[@]}"; do
    [[ -f "${msvc_setup_policy_test}" ]] || die \
        "missing cross-platform MSVC setup policy regression at ${msvc_setup_policy_test}"
done
[[ -f "${ci_workflow}" ]] || die "missing CI workflow at ${ci_workflow}"
[[ -f "${release_workflow}" ]] || die "missing release workflow at ${release_workflow}"

parse_workflow_yaml() {
    local workflow_path="$1"
    local workflow_label="$2"
    ruby -e 'require "psych"; Psych.parse_stream(File.read(ARGV.fetch(0)))' "${workflow_path}" \
        >/dev/null 2>&1 || die "${workflow_label} no longer parses as valid YAML"
}

parse_workflow_yaml "${ci_workflow}" "CI workflow"
parse_workflow_yaml "${release_workflow}" "release workflow"

# shellcheck source=/dev/null
source "${stage_contract_script}"

contains_windows_contract_preflight_path() {
    local expected_path="$1"
    local candidate_path
    # shellcheck disable=SC2154
    for candidate_path in "${check_windows_contract_preflight_script_paths[@]}"; do
        if [[ "${candidate_path}" == "${expected_path}" ]]; then
            return 0
        fi
    done
    return 1
}

contains_windows_contract_preflight_path 'scripts/test-setup-msvc-dev-cmd.sh' || die \
    "canonical Windows-contract preflight inventory no longer exercises the repo-owned MSVC setup regression"
grep -Fq 'setup-msvc-dev-cmd-support.ps1' "${msvc_setup_script}" || die \
    "repo-owned MSVC setup entrypoint no longer delegates native work to the support adapter"
grep -Fq 'ConvertTo-FinGrindGitHubEnvironmentText' "${msvc_setup_script}" || die \
    "repo-owned MSVC setup entrypoint no longer consumes the policy-produced GitHub environment"
grep -Fq 'msvc_setup_policy.py' "${msvc_setup_support_script}" || die \
    "repo-owned MSVC support no longer delegates deterministic policy to the Python owner"
grep -Fq 'ProcessStartInfo' "${msvc_setup_support_script}" || die \
    "repo-owned MSVC support no longer invokes native processes through argument-safe process APIs"
grep -Fq 'ArgumentList.Add("-B")' "${msvc_setup_support_script}" || die \
    "repo-owned MSVC support no longer prevents isolated policy bytecode from polluting the checkout"
grep -Fq 'ArgumentList.Add("-I")' "${msvc_setup_support_script}" || die \
    "repo-owned MSVC support no longer isolates pure-policy imports from ambient Python state"
grep -Fq 'ArgumentList.Add("-X")' "${msvc_setup_support_script}" || die \
    "repo-owned MSVC support no longer forces UTF-8 for its isolated policy wire"
grep -Fq 'ArgumentList.Add("utf8")' "${msvc_setup_support_script}" || die \
    "repo-owned MSVC support no longer selects Python UTF-8 mode for its policy wire"
grep -Fq '/v:off /s /c $CommandLine' "${msvc_setup_support_script}" || die \
    "repo-owned MSVC support no longer disables delayed command expansion before running VsDevCmd"
grep -Fq 'StandardOutputEncoding ([System.Text.Encoding]::Unicode)' "${msvc_setup_support_script}" || die \
    "repo-owned MSVC support no longer decodes cmd /u environment output as UTF-16"
grep -Fq 'vswhere.exe' "${msvc_setup_support_script}" || die \
    "repo-owned MSVC support no longer performs native vswhere discovery"
grep -Fq 'GITHUB_ENV' "${msvc_setup_support_script}" || die \
    "repo-owned MSVC support no longer owns the GitHub Actions environment-file boundary"
grep -Fq 'msvc_setup_policy_discovery' "${msvc_setup_policy_transport_script}" || die \
    "MSVC policy transport no longer delegates Visual Studio discovery to its pure owner"
grep -Fq 'msvc_setup_policy_environment' "${msvc_setup_policy_transport_script}" || die \
    "MSVC policy transport no longer delegates environment serialization to its pure owner"
grep -Fq 'VSWHERE_ARGUMENTS' "${msvc_setup_policy_discovery_script}" || die \
    "MSVC discovery policy no longer owns the canonical vswhere argument vector"
grep -Fq 'Microsoft.VisualStudio.Component.VC.Tools.x86.x64' "${msvc_setup_policy_discovery_script}" || die \
    "MSVC discovery policy no longer requires the canonical MSVC tool capability"
grep -Fq 'VSCMD_VER' "${msvc_setup_policy_environment_script}" || die \
    "MSVC environment policy no longer validates the developer-command environment"
grep -Fq 'GITHUB_ENV_DELIMITER_PREFIX' "${msvc_setup_policy_environment_script}" || die \
    "MSVC environment policy no longer owns GitHub environment serialization"
if grep -Fq 'Microsoft.VisualStudio.Component.VC.Tools.x86.x64' "${msvc_setup_support_script}"; then
    die "PowerShell MSVC adapter retained the pure vswhere capability policy"
fi
if grep -Fq '__FINGRIND_ENV__' "${msvc_setup_support_script}"; then
    die "PowerShell MSVC adapter retained the pure GitHub environment serialization policy"
fi

if command -v python3 >/dev/null 2>&1; then
    policy_python="$(command -v python3)"
    readonly policy_python
elif command -v python >/dev/null 2>&1; then
    policy_python="$(command -v python)"
    readonly policy_python
else
    die "missing Python interpreter; expected python3 or python on PATH"
fi

for msvc_setup_policy_test in "${msvc_setup_policy_tests[@]}"; do
    "${policy_python}" "${msvc_setup_policy_test}" || die \
        "pure MSVC setup policy regression failed: ${msvc_setup_policy_test}"
done

if command -v pwsh >/dev/null 2>&1; then
    pwsh_command="$(command -v pwsh)"
    readonly pwsh_command
    policy_import_fixture_root="$(mktemp -d "${repo_root}/tmp/fingrind-msvc-policy-import.XXXXXX")"
    cleanup_policy_import_fixture() {
        rm -rf -- "${policy_import_fixture_root}"
    }
    trap cleanup_policy_import_fixture EXIT
    # shellcheck disable=SC2016
    parse_probe="$(
        REPO_ROOT="${repo_root}" "${pwsh_command}" -NoLogo -NoProfile -Command '
            $paths = @(
                Join-Path $env:REPO_ROOT "scripts/setup-msvc-dev-cmd.ps1"
                Join-Path $env:REPO_ROOT "scripts/setup-msvc-dev-cmd-support.ps1"
            )
            foreach ($path in $paths) {
                $tokens = $null
                $errors = $null
                [System.Management.Automation.Language.Parser]::ParseFile(
                    $path,
                    [ref] $tokens,
                    [ref] $errors
                ) | Out-Null
                if ($errors.Count -gt 0) {
                    $errors | ForEach-Object Message
                    exit 1
                }
            }
        ' \
            2>&1
    )" || die "repo-owned MSVC setup scripts no longer parse as valid PowerShell: ${parse_probe}"

    # shellcheck disable=SC2016
    adapter_behavior_json="$(
        REPO_ROOT="${repo_root}" \
        FINGRIND_MSVC_POLICY_IMPORT_FIXTURE_ROOT="${policy_import_fixture_root}" \
        "${pwsh_command}" -NoLogo -NoProfile -Command '
            $ErrorActionPreference = "Stop"
            . (Join-Path $env:REPO_ROOT "scripts/setup-msvc-dev-cmd-support.ps1")

            $maliciousPythonDirectory = $env:FINGRIND_MSVC_POLICY_IMPORT_FIXTURE_ROOT
            [System.IO.File]::WriteAllText(
                (Join-Path $maliciousPythonDirectory "json.py"),
                "raise RuntimeError(""ambient PYTHONPATH was imported"")`n",
                [System.Text.UTF8Encoding]::new($false)
            )
            [System.IO.File]::WriteAllText(
                (Join-Path $maliciousPythonDirectory "msvc_setup_policy_environment.py"),
                "raise RuntimeError(""ambient policy module was imported"")`n",
                [System.Text.UTF8Encoding]::new($false)
            )
            $env:PYTHONPATH = $maliciousPythonDirectory

            $commandLine = Get-FinGrindVsDevCmdCommandLine `
                -VsDevCmdPath "C:\Rīga Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" `
                -Arch "x64" `
                -HostArch "arm64"
            $script:vsDevCmdInvocationArguments = $null
            $script:vsDevCmdInvocationRawArgumentLine = $null
            $script:vsDevCmdInvocationOutputEncodingCodePage = $null
            $script:vsDevCmdInvocationErrorEncodingCodePage = $null
            $script:vsWhereInvocationArguments = $null
            function Invoke-FinGrindNativeProcess {
                param(
                    [string]$ExecutablePath,
                    [string[]]$Arguments,
                    [System.Text.Encoding]$StandardOutputEncoding,
                    [System.Text.Encoding]$StandardErrorEncoding,
                    [string]$RawArgumentLine
                )

                if ($ExecutablePath -eq "C:\Visual Studio Installer\vswhere.exe") {
                    $script:vsWhereInvocationArguments = @($Arguments)
                    return [pscustomobject]@{
                        ExitCode = 0
                        StandardOutput = "C:\Rīga Visual Studio\2022\BuildTools"
                        StandardError = ""
                    }
                }
                if ($ExecutablePath -ne "cmd.exe") {
                    throw "unexpected native executable: $ExecutablePath"
                }
                $script:vsDevCmdInvocationArguments = if ($null -eq $Arguments) { @() } else { @($Arguments) }
                $script:vsDevCmdInvocationRawArgumentLine = $RawArgumentLine
                $script:vsDevCmdInvocationOutputEncodingCodePage = $StandardOutputEncoding.CodePage
                $script:vsDevCmdInvocationErrorEncodingCodePage = $StandardErrorEncoding.CodePage
                return [pscustomobject]@{
                    ExitCode = 0
                    StandardOutput = "VSCMD_VER=17.12.3`nFINGRIND_UNICODE=Rīga"
                    StandardError = ""
                }
            }
            $vsWhereInstallation = Invoke-FinGrindVsWhere `
                -VsWherePath "C:\Visual Studio Installer\vswhere.exe"
            $environmentDump = @(Invoke-FinGrindVsDevCmdEnvironmentDump `
                -CommandLine $commandLine `
                -Arch "x64" `
                -HostArch "arm64")
            $environmentText = ConvertTo-FinGrindGitHubEnvironmentText -EnvironmentDump (
                @($environmentDump) + @(
                "PATH=C:\MSVC\bin;C:\Windows\System32"
                "FINGRIND_VALUE=left=right"
                "not-an-environment-entry"
                )
            )

            $environmentPath = [System.IO.Path]::GetTempFileName()
            try {
                Export-FinGrindGitHubEnvironmentText `
                    -EnvironmentText $environmentText `
                    -GitHubEnvironmentPath $environmentPath
                $exportedEnvironmentText = [System.IO.File]::ReadAllText($environmentPath) -replace "`r`n", "`n"
            } finally {
                Remove-Item -LiteralPath $environmentPath -Force -ErrorAction SilentlyContinue
            }

            $missingVersionFailure = $null
            try {
                ConvertTo-FinGrindGitHubEnvironmentText -EnvironmentDump @("PATH=C:\MSVC\bin") | Out-Null
            } catch {
                $missingVersionFailure = $_.Exception.Message
            }

            $unsafeArchitectureFailure = $null
            try {
                Get-FinGrindVsDevCmdCommandLine `
                    -VsDevCmdPath "C:\VS\VsDevCmd.bat" `
                    -Arch "x64 & unexpected" `
                    -HostArch "x64" | Out-Null
            } catch {
                $unsafeArchitectureFailure = $_.Exception.Message
            }

            $missingGitHubEnvironmentFailure = $null
            try {
                Export-FinGrindGitHubEnvironmentText `
                    -EnvironmentText "VSCMD_VER<<delimiter`n17.12.3`ndelimiter`n" `
                    -GitHubEnvironmentPath ""
            } catch {
                $missingGitHubEnvironmentFailure = $_.Exception.Message
            }

            $vsDevCmdFailure = $null
            try {
                function Invoke-FinGrindNativeProcess {
                    param(
                        [string]$ExecutablePath,
                        [string[]]$Arguments,
                        [System.Text.Encoding]$StandardOutputEncoding,
                        [System.Text.Encoding]$StandardErrorEncoding,
                        [string]$RawArgumentLine
                    )

                    return [pscustomobject]@{
                        ExitCode = 19
                        StandardOutput = ""
                        StandardError = "native compiler setup detail"
                    }
                }
                Invoke-FinGrindVsDevCmdEnvironmentDump `
                    -CommandLine $commandLine `
                    -Arch "x64" `
                    -HostArch "arm64" | Out-Null
            } catch {
                $vsDevCmdFailure = $_.Exception.Message
            }

            [ordered]@{
                commandLine = $commandLine
                vsWhereInstallation = $vsWhereInstallation
                vsWhereInvocationArguments = @($script:vsWhereInvocationArguments)
                vsDevCmdInvocationArguments = @($script:vsDevCmdInvocationArguments)
                vsDevCmdInvocationRawArgumentLine = $script:vsDevCmdInvocationRawArgumentLine
                vsDevCmdInvocationOutputEncodingCodePage = $script:vsDevCmdInvocationOutputEncodingCodePage
                vsDevCmdInvocationErrorEncodingCodePage = $script:vsDevCmdInvocationErrorEncodingCodePage
                environmentText = $environmentText
                exportedEnvironmentText = $exportedEnvironmentText
                missingVersionFailure = $missingVersionFailure
                unsafeArchitectureFailure = $unsafeArchitectureFailure
                missingGitHubEnvironmentFailure = $missingGitHubEnvironmentFailure
                vsDevCmdFailure = $vsDevCmdFailure
            } | ConvertTo-Json -Compress
        '
    )"

    "${policy_python}" - <<'PY' "${adapter_behavior_json}"
import json
import sys

payload = json.loads(sys.argv[1])
expected_command_line = (
    r'call "C:\Rīga Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" '
    r"-arch=x64 -host_arch=arm64 >nul && set"
)
if payload["commandLine"] != expected_command_line:
    raise SystemExit("MSVC adapter command line drifted from the pure policy contract")
expected_vswhere_invocation_arguments = [
    "-latest",
    "-products",
    "*",
    "-requires",
    "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
    "-property",
    "installationPath",
    "-utf8",
]
if payload["vsWhereInstallation"] != r"C:\Rīga Visual Studio\2022\BuildTools":
    raise SystemExit("MSVC adapter lost a Unicode vswhere installation path")
if payload["vsWhereInvocationArguments"] != expected_vswhere_invocation_arguments:
    raise SystemExit("MSVC adapter no longer requests UTF-8 vswhere output")
if payload["vsDevCmdInvocationArguments"]:
    raise SystemExit("MSVC adapter no longer reserves raw cmd command lines for the validated VsDevCmd boundary")
if payload["vsDevCmdInvocationRawArgumentLine"] != f"/d /u /v:off /s /c {expected_command_line}":
    raise SystemExit("MSVC adapter changed the validated raw cmd command line for VsDevCmd")
if payload["vsDevCmdInvocationOutputEncodingCodePage"] != 1200:
    raise SystemExit("MSVC adapter no longer decodes cmd /u environment output as UTF-16")
if payload["vsDevCmdInvocationErrorEncodingCodePage"] != 1200:
    raise SystemExit("MSVC adapter no longer decodes cmd /u diagnostics as UTF-16")
expected_environment = (
    "VSCMD_VER<<__FINGRIND_ENV__\n17.12.3\n__FINGRIND_ENV__\n"
    "FINGRIND_UNICODE<<__FINGRIND_ENV__\nRīga\n__FINGRIND_ENV__\n"
    "PATH<<__FINGRIND_ENV__\nC:\\MSVC\\bin;C:\\Windows\\System32\n__FINGRIND_ENV__\n"
    "FINGRIND_VALUE<<__FINGRIND_ENV__\nleft=right\n__FINGRIND_ENV__\n"
)
if payload["environmentText"] != expected_environment:
    raise SystemExit("MSVC adapter lost the policy-produced GitHub environment content")
if payload["exportedEnvironmentText"] != expected_environment:
    raise SystemExit("MSVC adapter changed the GitHub environment payload at the file boundary")
if "VSCMD_VER" not in payload["missingVersionFailure"]:
    raise SystemExit("MSVC adapter lost the partial-environment diagnostic")
if "without command syntax" not in payload["unsafeArchitectureFailure"]:
    raise SystemExit("MSVC adapter lost the architecture-token safety diagnostic")
if "missing GITHUB_ENV" not in payload["missingGitHubEnvironmentFailure"]:
    raise SystemExit("MSVC adapter lost the GitHub environment boundary diagnostic")
if "native compiler setup detail" not in payload["vsDevCmdFailure"]:
    raise SystemExit("MSVC adapter no longer preserves VsDevCmd native diagnostics")
PY

    set +e
    execution_probe="$("${pwsh_command}" -NoLogo -NoProfile -File "${msvc_setup_script}" 2>&1)"
    execution_status=$?
    set -e
    if [[ ${execution_status} -eq 0 ]]; then
        die "repo-owned MSVC setup script unexpectedly succeeded outside a Windows runner"
    fi
    printf '%s\n' "${execution_probe}" | grep -Fq 'can only run on Windows runners' || die \
        "repo-owned MSVC setup script no longer fails through its explicit non-Windows guard after parsing"
else
    printf '%s\n' \
        'repo-owned MSVC setup PowerShell adapter checks: skipped (pwsh unavailable)'
fi

grep -Fq -- '-File .\scripts\setup-msvc-dev-cmd.ps1' "${ci_workflow}" || die \
    "CI workflow no longer bootstraps the Windows MSVC environment through the repo-owned script"
# shellcheck disable=SC2016
grep -Fq '& $env:FINGRIND_PWSH_EXECUTABLE' "${ci_workflow}" || die \
    "CI workflow no longer launches the Windows MSVC owner through the verified exact PowerShell executable"
grep -Fq 'Configure MSVC developer command environment' "${release_workflow}" || die \
    "release workflow no longer declares the Windows MSVC bootstrap step"
grep -Fq 'setup-msvc-dev-cmd.ps1' "${release_workflow}" || die \
    "release workflow no longer bootstraps the Windows MSVC environment through the repo-owned script"
# shellcheck disable=SC2016
grep -Fq -- '-File "$env:FINGRIND_WORKFLOW_HELPER_ROOT/scripts/setup-msvc-dev-cmd.ps1"' "${release_workflow}" || die \
    "release workflow no longer bootstraps the Windows MSVC environment through the repo-owned script"
# shellcheck disable=SC2016
grep -Fq '& $env:FINGRIND_PWSH_EXECUTABLE' "${release_workflow}" || die \
    "release workflow no longer launches the Windows MSVC owner through the verified exact PowerShell executable"
if grep -Fq 'ilammy/msvc-dev-cmd' "${ci_workflow}"; then
    die "CI workflow still depends on the deprecated third-party msvc-dev-cmd action"
fi
if grep -Fq 'ilammy/msvc-dev-cmd' "${release_workflow}"; then
    die "release workflow still depends on the deprecated third-party msvc-dev-cmd action"
fi

printf 'repo-owned MSVC setup regression: success\n'
