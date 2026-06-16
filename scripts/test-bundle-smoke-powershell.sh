#!/usr/bin/env bash
# Guard the PowerShell release-surface verifier against Compare-Object scalar/null behavior so
# Windows bundle smoke cannot be the first detector again.

set -euo pipefail

die() {
    printf 'error: %s\n' "$1" >&2
    exit 1
}

create_temp_file() {
    local prefix="$1"
    local suffix="$2"
    local temp_path
    temp_path="$(mktemp "${TMPDIR:-/tmp}/${prefix}.XXXXXX")"
    if [[ -n "${suffix}" ]]; then
        mv "${temp_path}" "${temp_path}${suffix}"
        temp_path="${temp_path}${suffix}"
    fi
    printf '%s\n' "${temp_path}"
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
readonly bundle_smoke_acceptance_ps1="${repo_root}/scripts/bundle-smoke-acceptance.ps1"
readonly bundle_smoke_office_worker_ps1="${repo_root}/scripts/bundle-smoke-office-worker.ps1"
readonly bundle_smoke_command_bridge_ps1="${repo_root}/scripts/bundle-smoke-command-bridge.ps1"
readonly bundle_launcher_ps1="${repo_root}/cli/src/bundle/bin/fingrind.ps1"
readonly bundle_contract_verifier_py="${repo_root}/scripts/verify-bundle-archive-contract.py"

[[ -f "${bundle_smoke_ps1}" ]] || die "missing PowerShell bundle smoke script at ${bundle_smoke_ps1}"
[[ -f "${bundle_smoke_support_ps1}" ]] || die \
    "missing PowerShell bundle smoke support script at ${bundle_smoke_support_ps1}"
[[ -f "${bundle_smoke_common_ps1}" ]] || die \
    "missing PowerShell bundle smoke common script at ${bundle_smoke_common_ps1}"
[[ -f "${bundle_smoke_acceptance_ps1}" ]] || die \
    "missing PowerShell bundle smoke acceptance script at ${bundle_smoke_acceptance_ps1}"
[[ -f "${bundle_smoke_office_worker_ps1}" ]] || die \
    "missing PowerShell bundle smoke office-worker script at ${bundle_smoke_office_worker_ps1}"
[[ -f "${bundle_smoke_command_bridge_ps1}" ]] || die \
    "missing PowerShell bundle smoke command bridge at ${bundle_smoke_command_bridge_ps1}"
[[ -f "${bundle_launcher_ps1}" ]] || die \
    "missing PowerShell bundle launcher template at ${bundle_launcher_ps1}"
[[ -f "${bundle_contract_verifier_py}" ]] || die \
    "missing bundle contract verifier at ${bundle_contract_verifier_py}"
grep -Fq 'bundle-smoke-support.ps1' "${bundle_smoke_ps1}" || die \
    "bundle-smoke.ps1 no longer delegates to the PowerShell support script"
grep -Fq 'bundle-smoke-common.ps1' "${bundle_smoke_support_ps1}" || die \
    "bundle-smoke-support.ps1 no longer delegates to the common helper owner"
grep -Fq 'bundle-smoke-acceptance.ps1' "${bundle_smoke_support_ps1}" || die \
    "bundle-smoke-support.ps1 no longer delegates to the acceptance helper owner"
grep -Fq 'bundle-smoke-office-worker.ps1' "${bundle_smoke_support_ps1}" || die \
    "bundle-smoke-support.ps1 no longer delegates to the office-worker helper owner"
if grep -Fq 'bundle-smoke-contract.ps1' "${bundle_smoke_support_ps1}"; then
    die "bundle-smoke-support.ps1 should no longer source the retired PowerShell contract helper"
fi
grep -Fq 'function Test-SameSequence' "${bundle_smoke_common_ps1}" || die \
    "bundle-smoke-common.ps1 no longer defines the sequence-comparison helper"
[[ "$(grep -Fo 'Compare-Object' "${bundle_smoke_common_ps1}" | wc -l | tr -d '[:space:]')" == "1" ]] || die \
    "bundle-smoke-common.ps1 should keep Compare-Object usage isolated to the helper"
grep -Fq 'verify-bundle-archive-contract.py' "${bundle_smoke_acceptance_ps1}" || die \
    "bundle-smoke-acceptance.ps1 no longer delegates bundle archive verification to the Python owner"
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
grep -Fq 'Get-Command pwsh' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer resolves pwsh explicitly for the fresh process bridge"
grep -Fq 'ProcessStartInfo' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer uses one dedicated subprocess owner"
grep -Fq 'RedirectStandardInput' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer replays bridged stdin through the subprocess boundary"
grep -Fq 'FINGRIND_INTERNAL_CLI_ARGUMENTS_FILE' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer stages the CLI argument vector through the internal UTF-8 file contract"
grep -Fq 'ConvertTo-Json -Compress $arguments' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer serializes staged CLI arguments as UTF-8 JSON"
grep -Fq '"-ExecutionPolicy", "Bypass"' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer invokes the launcher through the isolated PowerShell file path"
if grep -Fq 'FINGRIND_BUNDLE_RETURN_EXIT_CODE' "${bundle_smoke_command_bridge_ps1}"; then
    die "bundle-smoke-command-bridge.ps1 must not depend on the retired in-process bundle return contract"
fi
if grep -Fq 'FINGRIND_BUNDLE_ARGUMENTS_FILE' "${bundle_smoke_command_bridge_ps1}"; then
    die "bundle-smoke-command-bridge.ps1 must not depend on the retired staged arguments-file contract"
fi
if grep -Fq 'FINGRIND_BUNDLE_STDIN_FILE' "${bundle_smoke_command_bridge_ps1}"; then
    die "bundle-smoke-command-bridge.ps1 must not depend on the retired staged stdin-file contract"
fi
if grep -Fq '& $LauncherPath' "${bundle_smoke_command_bridge_ps1}"; then
    die "bundle-smoke-command-bridge.ps1 must not invoke the public launcher in-process anymore"
fi
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
    "fingrind.ps1 no longer forwards ordinary pipeline stdin into the bundled Java process"
grep -Fq '[Console]::IsInputRedirected' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer gates native stdin replay on the live console redirection state"
grep -Fq 'OpenStandardInput().CopyTo' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer copies ordinary pipeline stdin through to the bundled Java process"
grep -Fq 'Remove("FINGRIND_SQLITE_LIBRARY")' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer scrubs the retired SQLite override before launching Java"
grep -Fq 'FINGRIND_INTERNAL_CLI_ARGUMENTS_FILE' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer stages direct CLI arguments through the internal UTF-8 file contract"
grep -Fq 'New-StagedCliArgumentsFile' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer owns the staged CLI argument-file creation path"
grep -Fq '$PSScriptRoot' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer anchors bundle paths to the script root outside helper-function invocation scope"
grep -Fq '$scriptInvocationArguments = @($args)' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer preserves the public script-level CLI argument vector"
if grep -Fq '$MyInvocation.MyCommand.Path' "${bundle_launcher_ps1}"; then
    die "fingrind.ps1 continues to derive bundle paths from function-scoped MyInvocation metadata"
fi
if grep -Fq '& $runtimeJava @javaArguments' "${bundle_launcher_ps1}"; then
    die "fingrind.ps1 regressed to direct native invocation that can corrupt Unicode arguments"
fi
if grep -Fq 'ConvertFrom-Json' "${bundle_launcher_ps1}"; then
    die "fingrind.ps1 should no longer rehydrate staged bridge arguments inside PowerShell"
fi
if grep -Fq 'FINGRIND_LAUNCHER_ARGUMENTS_FILE' "${bundle_launcher_ps1}"; then
    die "fingrind.ps1 must not resurrect the retired launcher-arguments env seam"
fi
if grep -Fq 'FINGRIND_RELEASE_SMOKE_REQUEST_SALE_ARG' "${bundle_smoke_office_worker_ps1}"; then
    die "bundle-smoke-office-worker.ps1 continues to export legacy per-path release-smoke arguments"
fi

if ! command -v pwsh >/dev/null 2>&1; then
    printf 'bundle smoke PowerShell regression: skipped (pwsh unavailable)\n'
    exit 0
fi

pwsh_script="$(create_temp_file 'fingrind-bundle-smoke-powershell' '.ps1')"
bridge_request_json="$(create_temp_file 'fingrind-bundle-smoke-bridge' '.json')"
bridge_launcher_ps1="$(create_temp_file 'fingrind-bundle-smoke-launcher' '.ps1')"
launcher_bundle_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-bundle-launcher.XXXXXX")"
launcher_bridge_request_json="$(create_temp_file 'fingrind-bundle-launcher-bridge' '.json')"
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
$argumentsFile = $env:FINGRIND_INTERNAL_CLI_ARGUMENTS_FILE
$payload = [ordered]@{
    invocationArguments = @($args)
    stdinText = [Console]::In.ReadToEnd()
    stagedArgumentsFile = $argumentsFile
    stagedArguments =
        if ([string]::IsNullOrWhiteSpace($argumentsFile)) {
            $null
        } else {
            Get-Content -LiteralPath $argumentsFile -Raw -Encoding UTF8 | ConvertFrom-Json
        }
}
[Console]::Out.WriteLine(($payload | ConvertTo-Json -Compress))
exit 0
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
if payload["invocationArguments"]:
    raise SystemExit("bundle-smoke-command-bridge.ps1 leaked staged CLI arguments onto the launcher argv boundary")
if payload["stagedArgumentsFile"] is None:
    raise SystemExit("bundle-smoke-command-bridge.ps1 failed to publish the internal staged-arguments file contract")
if payload["stagedArguments"][2] != "/tmp/workspace odd/Rīga büro/bridge key.key":
    raise SystemExit("bundle-smoke-command-bridge.ps1 corrupted the staged Unicode CLI argument")
if payload["stdinText"] != "stdin through bridge\n":
    raise SystemExit("bundle-smoke-command-bridge.ps1 failed to replay stdin text")
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

arguments_file = os.environ.get("FINGRIND_INTERNAL_CLI_ARGUMENTS_FILE")
payload = {
    "argv": sys.argv[1:],
    "stdinText": sys.stdin.read(),
    "sqliteLibraryEnv": os.environ.get("FINGRIND_SQLITE_LIBRARY"),
    "internalCliArgumentsFileEnv": arguments_file,
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

launcher_output="$(
    printf 'stdin through public launcher\n' | \
        pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File \
            "${launcher_bundle_root}/bin/fingrind.ps1" \
            generate-book-key-file \
            --book-key-file \
            '/tmp/workspace odd/Rīga büro/bridge key.key'
)"
python3 - <<'PY' "${launcher_output}"
import json
import sys

payload = json.loads(sys.argv[1])
if payload["internalCliArgumentsFileEnv"] is None:
    raise SystemExit("fingrind.ps1 failed to hand staged CLI arguments to the JVM boundary")
if payload["stagedArguments"][-3:] != ["generate-book-key-file", "--book-key-file", "/tmp/workspace odd/Rīga büro/bridge key.key"]:
    raise SystemExit("fingrind.ps1 lost the staged Unicode CLI arguments before the JVM boundary")
if any(argument == "generate-book-key-file" for argument in payload["argv"]):
    raise SystemExit("fingrind.ps1 leaked staged CLI arguments back onto the native Java argv boundary")
if payload["stdinText"] != "stdin through public launcher\n":
    raise SystemExit("fingrind.ps1 failed to forward ordinary pipeline stdin to the JVM boundary")
if payload["sqliteLibraryEnv"] is not None:
    raise SystemExit("fingrind.ps1 leaked the retired SQLite override into the JVM boundary")
PY

printf 'bundle smoke PowerShell regression: success\n'
