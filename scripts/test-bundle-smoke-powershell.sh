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
readonly bundle_smoke_command_bridge_ps1="${repo_root}/scripts/bundle-smoke-command-bridge.ps1"
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
[[ -f "${bundle_smoke_command_bridge_ps1}" ]] || die \
    "missing PowerShell bundle smoke command bridge at ${bundle_smoke_command_bridge_ps1}"
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
grep -Fq 'FINGRIND_RELEASE_SMOKE_COMMAND_BRIDGE_PREFIX_JSON' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the PowerShell bridge command contract"
grep -Fq 'Get-Content -LiteralPath $RequestPath -Raw -Encoding UTF8' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer reads bridge requests as UTF-8 JSON"
grep -Fq 'FINGRIND_BUNDLE_RETURN_EXIT_CODE' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer requests in-process launcher exit-code returns"
grep -Fq 'FINGRIND_BUNDLE_ARGUMENTS_FILE' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer stages bridged arguments through a UTF-8 temp file"
grep -Fq 'FINGRIND_BUNDLE_STDIN_FILE' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer stages bridged stdin through a UTF-8 temp file"
grep -Fq '& $LauncherPath' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer invokes the launcher in-process"
python3 - <<'PY' "${bundle_smoke_command_bridge_ps1}"
import pathlib
import sys

lines = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
meaningful = [line.strip() for line in lines if line.strip() and not line.lstrip().startswith("#")]
if not meaningful or meaningful[0] != "param(":
    raise SystemExit("bundle-smoke-command-bridge.ps1 must begin with a script-level param block")
if "Set-StrictMode -Version Latest" not in meaningful[1:]:
    raise SystemExit("bundle-smoke-command-bridge.ps1 lost its strict-mode guard after the param block")
PY
grep -Fq 'ProcessStartInfo' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer uses a ProcessStartInfo-based native launch path"
grep -Fq 'ArgumentList.Add' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer forwards Java arguments through ProcessStartInfo.ArgumentList"
grep -Fq 'RedirectStandardInput' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer stages bridged stdin into the bundled Java process"
grep -Fq 'FINGRIND_BUNDLE_RETURN_EXIT_CODE' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer supports the in-process bridge return-code contract"
grep -Fq 'FINGRIND_BUNDLE_STDIN_FILE' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer supports the in-process bridge stdin-file contract"
grep -Fq '$PSScriptRoot' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer anchors bundle paths to the script root outside helper-function invocation scope"
grep -Fq 'FINGRIND_BUNDLE_ARGUMENTS_FILE' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer supports the in-process bridge arguments-file contract"
grep -Fq 'FINGRIND_LAUNCHER_ARGUMENTS_FILE' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer hands staged bridge arguments to the JVM through the dedicated launcher env contract"
grep -Fq '$scriptInvocationArguments = @($args)' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer preserves the public script-level CLI argument vector"
if grep -Fq '$MyInvocation.MyCommand.Path' "${bundle_launcher_ps1}"; then
    die "fingrind.ps1 still derives bundle paths from function-scoped MyInvocation metadata"
fi
if grep -Fq '& $runtimeJava @javaArguments' "${bundle_launcher_ps1}"; then
    die "fingrind.ps1 regressed to direct native invocation that can corrupt Unicode arguments"
fi
if grep -Fq 'ConvertFrom-Json' "${bundle_launcher_ps1}"; then
    die "fingrind.ps1 should no longer rehydrate staged bridge arguments inside PowerShell"
fi
if grep -Fq 'FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG' "${bundle_smoke_office_worker_ps1}"; then
    die "bundle-smoke-office-worker.ps1 still exports legacy per-path release-smoke arguments"
fi

if ! command -v pwsh >/dev/null 2>&1; then
    printf 'bundle smoke PowerShell regression: skipped (pwsh unavailable)\n'
    exit 0
fi

pwsh_script="$(mktemp "${TMPDIR:-/tmp}/fingrind-bundle-smoke-powershell.XXXXXX.ps1")"
bridge_request_json="$(mktemp "${TMPDIR:-/tmp}/fingrind-bundle-smoke-bridge.XXXXXX.json")"
bridge_launcher_ps1="$(mktemp "${TMPDIR:-/tmp}/fingrind-bundle-smoke-launcher.XXXXXX.ps1")"
launcher_bundle_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-bundle-launcher.XXXXXX")"
launcher_bridge_request_json="$(mktemp "${TMPDIR:-/tmp}/fingrind-bundle-launcher-bridge.XXXXXX.json")"
trap 'rm -f "${pwsh_script}" "${bridge_request_json}" "${bridge_launcher_ps1}" "${launcher_bridge_request_json}"; rm -rf "${launcher_bundle_root}"' EXIT
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

cat >"${bridge_launcher_ps1}" <<'PWSH'
$payload = [ordered]@{
    arguments = Get-Content -LiteralPath $env:FINGRIND_BUNDLE_ARGUMENTS_FILE -Raw -Encoding UTF8 | ConvertFrom-Json
    invocationArguments = @($args)
    stdinText = Get-Content -LiteralPath $env:FINGRIND_BUNDLE_STDIN_FILE -Raw -Encoding UTF8
    returnMode = $env:FINGRIND_BUNDLE_RETURN_EXIT_CODE
}
[Console]::Out.WriteLine(($payload | ConvertTo-Json -Compress))
return 0
PWSH

cat >"${bridge_request_json}" <<'JSON'
{"arguments":["generate-book-key-file","--book-key-file","/tmp/workspace odd/Rīga büro/bridge key.key"],"stdinText":"stdin through bridge\n"}
JSON

bridge_output="$(
    pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File \
        "${bundle_smoke_command_bridge_ps1}" \
        "${bridge_launcher_ps1}" \
        "${bridge_request_json}"
)"
python3 - <<'PY' "${bridge_output}"
import json
import sys

payload = json.loads(sys.argv[1])
if payload["arguments"][2] != "/tmp/workspace odd/Rīga büro/bridge key.key":
    raise SystemExit("bundle-smoke-command-bridge.ps1 corrupted the Unicode CLI argument")
if payload["invocationArguments"] != []:
    raise SystemExit("bundle-smoke-command-bridge.ps1 leaked bridged CLI arguments through PowerShell script argument binding")
if payload["stdinText"] != "stdin through bridge\n":
    raise SystemExit("bundle-smoke-command-bridge.ps1 failed to replay stdin text")
if payload["returnMode"] != "true":
    raise SystemExit("bundle-smoke-command-bridge.ps1 failed to enable in-process launcher return mode")
PY

mkdir -p "${launcher_bundle_root}/bin" "${launcher_bundle_root}/runtime/bin" "${launcher_bundle_root}/lib/app"
python3 - <<'PY' "${bundle_launcher_ps1}" "${launcher_bundle_root}/bin/fingrind.ps1"
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
source = source.replace("{{bundleHomeSystemProperty}}", "fingrind.bundle.home")
source = source.replace("{{bundleRuntimeDistribution}}", "bundle")
pathlib.Path(sys.argv[2]).write_text(source, encoding="utf-8")
PY
cat >"${launcher_bundle_root}/runtime/bin/java.exe" <<'PY'
#!/usr/bin/env python3
import json
import os
import pathlib
import sys

arguments_file = os.environ.get("FINGRIND_LAUNCHER_ARGUMENTS_FILE")
payload = {
    "argv": sys.argv[1:],
    "launcherArgumentsFileEnv": arguments_file,
    "stagedArguments": (
        json.loads(pathlib.Path(arguments_file).read_text(encoding="utf-8"))
        if arguments_file
        else None
    ),
}
print(json.dumps(payload, ensure_ascii=False))
PY
chmod +x "${launcher_bundle_root}/runtime/bin/java.exe"
: >"${launcher_bundle_root}/lib/app/fingrind.jar"

cat >"${launcher_bridge_request_json}" <<'JSON'
{"arguments":["generate-book-key-file","--book-key-file","/tmp/workspace odd/Rīga büro/bridge key.key"],"stdinText":null}
JSON

launcher_output="$(
    pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File \
        "${bundle_smoke_command_bridge_ps1}" \
        "${launcher_bundle_root}/bin/fingrind.ps1" \
        "${launcher_bridge_request_json}"
)"
python3 - <<'PY' "${launcher_output}"
import json
import sys

payload = json.loads(sys.argv[1])
if payload["launcherArgumentsFileEnv"] is None:
    raise SystemExit("fingrind.ps1 failed to pass the staged launcher arguments file through the JVM env contract")
if payload["stagedArguments"][0] != "generate-book-key-file":
    raise SystemExit("fingrind.ps1 lost the staged command name before the JVM boundary")
if payload["stagedArguments"][2] != "/tmp/workspace odd/Rīga büro/bridge key.key":
    raise SystemExit("fingrind.ps1 lost the staged Unicode path before the JVM boundary")
if any("generate-book-key-file" == argument for argument in payload["argv"]):
    raise SystemExit("fingrind.ps1 leaked staged CLI arguments back onto the native Java argv boundary")
if any("Rīga büro" in argument for argument in payload["argv"]):
    raise SystemExit("fingrind.ps1 still forwards Unicode stress-path arguments directly through the native Java argv boundary")
PY

printf 'bundle smoke PowerShell regression: success\n'
