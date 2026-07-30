#!/usr/bin/env bash
# Guard the deliberately tiny Windows batch adapter. FinGrind-owned wrapper policy belongs to the
# testable PowerShell owner; cmd.exe only locates pwsh, anchors the repository, and relays argv and
# exit status.

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
readonly wrapper_path="${repo_root}/gradlew.bat"
readonly owner_path="${repo_root}/scripts/gradle-wrapper-owner.ps1"
readonly entrypoint_path="${repo_root}/scripts/gradlew.ps1"

[[ -f "${wrapper_path}" ]] || die "missing Windows Gradle wrapper at ${wrapper_path}"
[[ -f "${owner_path}" ]] || die "missing PowerShell Gradle wrapper owner at ${owner_path}"
[[ -f "${entrypoint_path}" ]] || die "missing PowerShell Gradle wrapper entrypoint at ${entrypoint_path}"

python3 - <<'PY' "${wrapper_path}" "${owner_path}" "${entrypoint_path}"
from pathlib import Path
import sys

wrapper_path = Path(sys.argv[1])
owner_path = Path(sys.argv[2])
entrypoint_path = Path(sys.argv[3])
wrapper = wrapper_path.read_text(encoding="utf-8")
owner = owner_path.read_text(encoding="utf-8")
entrypoint = entrypoint_path.read_text(encoding="utf-8")

required_wrapper_fragments = (
    "setlocal EnableExtensions DisableDelayedExpansion",
    "set \"APP_HOME=%~dp0\"",
    "for %%I in (\"%APP_HOME%\") do set \"APP_HOME=%%~fI\"",
    "set \"PWSH_EXE=%FINGRIND_PWSH_EXECUTABLE%\"",
    "FINGRIND_PWSH_EXECUTABLE does not name an existing PowerShell executable",
    "where.exe pwsh.exe",
    "PowerShell 7 or later as pwsh.exe on PATH",
    "-NoLogo -NoProfile -ExecutionPolicy Bypass -File \"%APP_HOME%\\scripts\\gradlew.ps1\" %*",
    "set \"EXIT_CODE=%ERRORLEVEL%\"",
    "if \"%GRADLE_EXIT_CONSOLE%\"==\"\" goto return",
    "endlocal & exit %EXIT_CODE%",
    "endlocal & exit /b %EXIT_CODE%",
)
for fragment in required_wrapper_fragments:
    if fragment not in wrapper:
        raise SystemExit(f"gradlew.bat lost adapter contract fragment: {fragment}")

forbidden_wrapper_fragments = (
    "JAVA_HOME",
    "JAVA_EXE",
    "DEFAULT_JVM_OPTS",
    "JAVA_OPTS",
    "GRADLE_OPTS",
    "FINGRIND_GRADLE_PROJECT_CACHE",
    "FINGRIND_GRADLE_BUILD_LOGIC",
    "FINGRIND_GRADLE_JACOCO",
    "FINGRIND_GRADLE_PROJECT_BUILD_ROOT",
    ":scanFinGrindArguments",
    ":ensureFinGrind",
    ":resolveFinGrind",
    "powershell.exe",
)
for fragment in forbidden_wrapper_fragments:
    if fragment in wrapper:
        raise SystemExit(f"gradlew.bat retained FinGrind policy instead of delegating it: {fragment}")

if wrapper.count("\n") > 56:
    raise SystemExit("gradlew.bat is no longer a small cmd.exe adapter")
if ". (Join-Path $PSScriptRoot \"gradle-wrapper-owner.ps1\")" not in entrypoint:
    raise SystemExit("PowerShell Gradle entrypoint no longer delegates to the wrapper owner")
if "$PSVersionTable.PSVersion.Major -lt 7" not in entrypoint:
    raise SystemExit("PowerShell Gradle entrypoint no longer refuses pre-7 PowerShell")
if "-GradleArguments @($args)" not in entrypoint:
    raise SystemExit("PowerShell Gradle entrypoint no longer forwards the raw caller argument vector")
if "ProcessStartInfo" not in owner or "ArgumentList.Add" not in owner:
    raise SystemExit("PowerShell Gradle owner no longer uses the lossless native argument boundary")
if "ConvertFrom-FinGrindWindowsCommandLine" not in owner:
    raise SystemExit("PowerShell Gradle owner no longer owns JAVA_OPTS and GRADLE_OPTS tokenization")
for fragment in (
    "RedirectStandardInput = [Console]::IsInputRedirected",
    "RedirectStandardOutput = $false",
    "RedirectStandardError = $false",
):
    if fragment not in owner:
        raise SystemExit(f"PowerShell Gradle owner no longer preserves the required standard-stream boundary: {fragment}")
if "OpenStandardInput().CopyTo($process.StandardInput.BaseStream)" not in owner:
    raise SystemExit("PowerShell Gradle owner no longer pumps redirected standard input into Java")
if "$process.StandardInput.Close()" not in owner:
    raise SystemExit("PowerShell Gradle owner no longer closes redirected Java standard input before waiting")
if "Get-FinGrindWindowsGradleWrapperPlan" not in owner:
    raise SystemExit("PowerShell Gradle owner no longer derives Windows paths from the canonical pure plan")
if "GradleInvocationLease.java" not in owner or "InvocationLeaseFile" not in owner:
    raise SystemExit("PowerShell Gradle owner no longer launches through the shared invocation lease")
PY

printf 'gradlew.bat adapter regression: success\n'
