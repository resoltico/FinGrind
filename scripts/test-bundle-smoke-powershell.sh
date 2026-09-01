#!/usr/bin/env bash
# Guard the PowerShell release-surface verifier's ordered target comparison so Windows bundle smoke
# cannot be the first detector of a membership-only comparison regression.

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
grep -Fq 'function Get-FinGrindPowerShellExecutable' "${bundle_smoke_common_ps1}" || die \
    "bundle-smoke-common.ps1 no longer owns exact PowerShell executable selection"
grep -Fq 'FINGRIND_PWSH_EXECUTABLE' "${bundle_smoke_common_ps1}" || die \
    "bundle-smoke-common.ps1 no longer honors the explicit PowerShell executable contract"
grep -Fq 'for ($index = 0; $index -lt $Reference.Count; $index++)' "${bundle_smoke_common_ps1}" || die \
    "bundle-smoke-common.ps1 no longer compares every target position deterministically"
grep -Fq '[object]::Equals($Reference[$index], $Actual[$index])' "${bundle_smoke_common_ps1}" || die \
    "bundle-smoke-common.ps1 no longer compares ordered target values by exact object equality"
if grep -Fq 'Compare-Object' "${bundle_smoke_common_ps1}"; then
    die "bundle-smoke-common.ps1 must not reduce ordered target comparison to membership comparison"
fi
grep -Fq 'verify-bundle-archive-contract.py' "${bundle_smoke_acceptance_ps1}" || die \
    "bundle-smoke-acceptance.ps1 no longer delegates bundle archive verification to the Python owner"
grep -Fq 'release-smoke-workflow.py' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer delegates to the shared release smoke workflow owner"
grep -Fq 'Get-RepoUvExecutable' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer resolves the pinned uv launcher"
grep -Fq -- '--with-requirements' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer provisions release-smoke dependencies through uv"
grep -Fq 'requirements-release-smoke-workflow.txt' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer uses the isolated release-smoke requirements"
grep -Fq 'fingrindUvVersion=' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer derives the uv version from repository metadata"
grep -Fq '$versionOutput -eq "uv $requiredVersion"' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer rejects a mismatched uv launcher"
grep -Fq '$versionOutput.StartsWith("uv $requiredVersion ")' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer accepts the exact uv version with a supported suffix"
grep -Fq '[char]0x012B' "${bundle_smoke_acceptance_ps1}" || die \
    "bundle-smoke-acceptance.ps1 no longer keeps the Latvian Unicode workspace-path coverage seam alive"
grep -Fq '[char]0x00FC' "${bundle_smoke_acceptance_ps1}" || die \
    "bundle-smoke-acceptance.ps1 no longer keeps the non-ASCII workspace-path coverage seam alive"
grep -Fq 'function Initialize-BundleSmokeWorkspace' "${bundle_smoke_acceptance_ps1}" || die \
    "bundle-smoke-acceptance.ps1 no longer owns private workspace initialization"
grep -Fq 'secure-windows-owner-only-directory.ps1' "${bundle_smoke_acceptance_ps1}" || die \
    "bundle-smoke-acceptance.ps1 no longer secures each Windows workspace ancestor"
grep -Fq '@($smokeRoot, $workspaceRoot, $unicodeWorkspaceRoot, $workRoot)' "${bundle_smoke_acceptance_ps1}" || die \
    "bundle-smoke-acceptance.ps1 no longer secures the complete nested workspace ancestry"
grep -Fq -- '-PrivateWorkspaceDirectories $privateWorkspaceDirectories' "${bundle_smoke_acceptance_ps1}" || die \
    "bundle-smoke-acceptance.ps1 no longer initializes its workspace before bundle execution"
grep -Fq 'FINGRIND_RELEASE_SMOKE_WORK_ROOT' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the compact shared work-root contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_ARGUMENT_PATH_MODE' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the shared argument-path-mode contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_SCENARIO_ID' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the shared scenario-id contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_COMMAND_BRIDGE_PREFIX_JSON' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the PowerShell bridge command contract"
grep -Fq 'FINGRIND_RELEASE_SMOKE_POWERSHELL_EXECUTABLE' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer publishes the pinned PowerShell owner-only ACL boundary"
grep -Fq 'Get-FinGrindPowerShellExecutable' "${bundle_smoke_office_worker_ps1}" || die \
    "bundle-smoke-office-worker.ps1 no longer keeps release-smoke child processes on the explicit PowerShell executable"
grep -Fq 'Get-Content -LiteralPath $RequestPath -Raw -Encoding UTF8' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer reads bridge requests as UTF-8 JSON"
grep -Fq 'bundle-smoke-common.ps1' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer delegates PowerShell selection to the common owner"
grep -Fq 'Get-FinGrindPowerShellExecutable' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer uses the explicit PowerShell executable contract"
grep -Fq 'ProcessStartInfo' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer uses one dedicated subprocess owner"
grep -Fq 'RedirectStandardInput' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer replays bridged stdin through the subprocess boundary"
grep -Fq 'StandardInputEncoding = $utf8NoBom' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer encodes bridged stdin as UTF-8"
grep -Fq 'StandardOutputEncoding = $utf8NoBom' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer decodes bundled stdout as UTF-8"
grep -Fq 'StandardErrorEncoding = $utf8NoBom' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer decodes bundled stderr as UTF-8"
grep -Fq '[Console]::OutputEncoding = $utf8NoBom' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer emits bridge output as UTF-8"
grep -Fq 'FINGRIND_INTERNAL_CLI_ARGUMENTS_FILE' "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer stages the CLI argument vector through the internal UTF-8 file contract"
grep -Fq 'ConvertTo-Json -Compress -Depth 4 -EscapeHandling EscapeNonAscii $arguments' \
    "${bundle_smoke_command_bridge_ps1}" || die \
    "bundle-smoke-command-bridge.ps1 no longer serializes staged CLI arguments as ASCII-safe JSON"
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
grep -Fq -- '--add-opens=java.base/java.nio=dev.erst.fingrind.cli' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer opens java.base/java.nio to the canonical module identity"
grep -Fq -- '--add-exports=java.base/sun.nio=dev.erst.fingrind.cli' "${bundle_launcher_ps1}" || die \
    "fingrind.ps1 no longer exports java.base/sun.nio to the canonical module identity"
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
bridge_pwsh_wrapper="$(create_temp_file 'fingrind-bundle-smoke-pwsh-wrapper' '')"
bridge_pwsh_capture="$(create_temp_file 'fingrind-bundle-smoke-pwsh-capture' '')"
launcher_bundle_root="$(mktemp -d "${TMPDIR:-/tmp}/fingrind-bundle-launcher.XXXXXX")"
launcher_bridge_request_json="$(create_temp_file 'fingrind-bundle-launcher-bridge' '.json')"
trap 'rm -f "${pwsh_script}" "${bridge_request_json}" "${bridge_launcher_ps1}" "${bridge_pwsh_wrapper}" "${bridge_pwsh_capture}" "${launcher_bridge_request_json}"; rm -rf "${launcher_bundle_root}"' EXIT
cat >"${pwsh_script}" <<'PWSH'
function Test-SameSequence {
    param(
        [Parameter(Mandatory = $true)]
        [object[]] $Reference,
        [Parameter(Mandatory = $true)]
        [object[]] $Actual
    )

    if ($Reference.Count -ne $Actual.Count) {
        return $false
    }
    for ($index = 0; $index -lt $Reference.Count; $index++) {
        if (-not [object]::Equals($Reference[$index], $Actual[$index])) {
            return $false
        }
    }
    return $true
}

if (-not (Test-SameSequence -Reference @("linux-x86_64") -Actual @("linux-x86_64"))) {
    throw "same-sequence helper rejected identical singleton arrays"
}
if (Test-SameSequence -Reference @("linux-x86_64") -Actual @("windows-x86_64")) {
    throw "same-sequence helper accepted mismatched arrays"
}
if (Test-SameSequence -Reference @("linux-x86_64", "windows-x86_64") -Actual @("windows-x86_64", "linux-x86_64")) {
    throw "same-sequence helper accepted a reordered target sequence"
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
{"arguments":["generate-book-key-file","--new-book-key-file","/tmp/workspace odd/Rīga büro/bridge key.key"],"stdinText":"stdin through bridge\n"}
JSON

cat >"${bridge_pwsh_wrapper}" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$0" >> "${FINGRIND_PWSH_BRIDGE_CAPTURE}"
exec "${FINGRIND_REAL_PWSH}" "$@"
SH
chmod +x "${bridge_pwsh_wrapper}"
bridge_pwsh_wrapper_name="$(basename -- "${bridge_pwsh_wrapper}")"

bridge_output="$(
    FINGRIND_PWSH_EXECUTABLE="${bridge_pwsh_wrapper}" \
        FINGRIND_PWSH_BRIDGE_CAPTURE="${bridge_pwsh_capture}" \
        FINGRIND_REAL_PWSH="$(command -v pwsh)" \
        pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File \
        "${bundle_smoke_command_bridge_ps1}" \
        "${bridge_launcher_ps1}" \
        "${bridge_request_json}"
)"
[[ "$(basename -- "$(cat "${bridge_pwsh_capture}")")" == "${bridge_pwsh_wrapper_name}" ]] || die \
    "bundle-smoke-command-bridge.ps1 did not use FINGRIND_PWSH_EXECUTABLE for the bridge subprocess"
set +e
invalid_pwsh_output="$(
    FINGRIND_PWSH_EXECUTABLE="${bridge_pwsh_wrapper}.missing" \
        pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File \
        "${bundle_smoke_command_bridge_ps1}" \
        "${bridge_launcher_ps1}" \
        "${bridge_request_json}" 2>&1
)"
invalid_pwsh_status=$?
set -e
[[ ${invalid_pwsh_status} -ne 0 ]] || die \
    "bundle-smoke-command-bridge.ps1 fell back to PATH after FINGRIND_PWSH_EXECUTABLE named no executable"
printf '%s\n' "${invalid_pwsh_output}" | grep -Fq \
    'FINGRIND_PWSH_EXECUTABLE does not name' || die \
    "bundle-smoke-command-bridge.ps1 did not explain its rejected explicit PowerShell executable"
set +e
blank_pwsh_output="$(
    FINGRIND_PWSH_EXECUTABLE='   ' \
        pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File \
        "${bundle_smoke_command_bridge_ps1}" \
        "${bridge_launcher_ps1}" \
        "${bridge_request_json}" 2>&1
)"
blank_pwsh_status=$?
set -e
[[ ${blank_pwsh_status} -ne 0 ]] || die \
    "bundle-smoke-command-bridge.ps1 treated a set blank FINGRIND_PWSH_EXECUTABLE as permission to fall back to PATH"
printf '%s\n' "${blank_pwsh_output}" | grep -Fq \
    'FINGRIND_PWSH_EXECUTABLE must name one non-empty absolute' || die \
    "bundle-smoke-command-bridge.ps1 did not explain its rejected blank explicit PowerShell executable"
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
            --new-book-key-file \
            '/tmp/workspace odd/Rīga büro/bridge key.key'
)"
python3 - <<'PY' "${launcher_output}"
import json
import sys

payload = json.loads(sys.argv[1])
if payload["internalCliArgumentsFileEnv"] is None:
    raise SystemExit("fingrind.ps1 failed to hand staged CLI arguments to the JVM boundary")
for encoding_argument in (
    "-Dstdin.encoding=UTF-8",
    "-Dstdout.encoding=UTF-8",
    "-Dstderr.encoding=UTF-8",
):
    if encoding_argument not in payload["argv"]:
        raise SystemExit(
            "fingrind.ps1 did not force UTF-8 at the JVM standard-stream boundary: "
            + encoding_argument
        )
if payload["stagedArguments"][-3:] != ["generate-book-key-file", "--new-book-key-file", "/tmp/workspace odd/Rīga büro/bridge key.key"]:
    raise SystemExit("fingrind.ps1 lost the staged Unicode CLI arguments before the JVM boundary")
if any(argument == "generate-book-key-file" for argument in payload["argv"]):
    raise SystemExit("fingrind.ps1 leaked staged CLI arguments back onto the native Java argv boundary")
if payload["stdinText"] != "stdin through public launcher\n":
    raise SystemExit("fingrind.ps1 failed to forward ordinary pipeline stdin to the JVM boundary")
if payload["sqliteLibraryEnv"] is not None:
    raise SystemExit("fingrind.ps1 leaked the retired SQLite override into the JVM boundary")
PY

printf 'bundle smoke PowerShell regression: success\n'
