#!/usr/bin/env bash
# Regress the shared PowerShell Gradle wrapper helper against the active host wrapper contract and
# the Windows-specific batch-owner branches it must mirror.

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
readonly shell_helper="${repo_root}/scripts/gradle-wrapper-support.sh"
readonly powershell_helper="${repo_root}/scripts/gradle-wrapper-support.ps1"

[[ -f "${shell_helper}" ]] || die "missing shell Gradle wrapper helper at ${shell_helper}"
[[ -f "${powershell_helper}" ]] || die \
    "missing PowerShell Gradle wrapper helper at ${powershell_helper}"
grep -Fq 'RUNNER_TEMP' "${powershell_helper}" || die \
    "PowerShell Gradle wrapper helper no longer keeps the Windows RUNNER_TEMP cache-root branch"
grep -Fq 'LOCALAPPDATA' "${powershell_helper}" || die \
    "PowerShell Gradle wrapper helper no longer keeps the Windows LOCALAPPDATA cache-root branch"
grep -Fq 'ShouldExternalizeProjectBuildRoot' "${powershell_helper}" || die \
    "PowerShell Gradle wrapper helper no longer exposes the Windows UNC externalization decision"
grep -Fq '& cksum' "${powershell_helper}" || die \
    "PowerShell Gradle wrapper helper no longer mirrors the POSIX cksum-based cache key"
grep -Fq 'function Get-FinGrindWindowsGradleWrapperPlanForEnvironment' "${powershell_helper}" || die \
    "PowerShell Gradle wrapper helper no longer owns a pure Windows wrapper-path plan"
grep -Fq '[hashtable]$Environment' "${powershell_helper}" || die \
    "PowerShell Gradle wrapper helper no longer accepts explicit Windows environment fixtures"
grep -Fq 'function Get-FinGrindWindowsGradleWrapperPlan' "${powershell_helper}" || die \
    "PowerShell Gradle wrapper helper no longer exposes the process-environment adapter"

if ! command -v pwsh >/dev/null 2>&1; then
    printf 'PowerShell Gradle wrapper helper regression: skipped (pwsh unavailable)\n'
    exit 0
fi

# shellcheck source=/dev/null
source "${shell_helper}"

is_darwin=false
case "$(uname -s)" in
    Darwin) is_darwin=true ;;
esac

readonly expected_cache_key="$(fg_gradle_cache_key "${repo_root}")"
readonly expected_cache_dir="$(fg_gradle_project_cache_dir "${repo_root}" "${is_darwin}")"
readonly expected_build_root="$(fg_gradle_project_build_root "${repo_root}" "${is_darwin}")"
readonly expected_root_build_dir="$(fg_gradle_project_build_dir "${repo_root}" root "${is_darwin}")"
readonly expected_cli_build_dir="$(fg_gradle_project_build_dir "${repo_root}" cli "${is_darwin}")"

actual_json="$(
    REPO_ROOT="${repo_root}" pwsh -NoLogo -NoProfile -Command '
        . (Join-Path $PWD "scripts/gradle-wrapper-support.ps1")
        $repoRoot = $env:REPO_ROOT
        $payload = [ordered]@{
            cacheKey = Get-FinGrindProjectCacheKey -RepositoryRoot $repoRoot
            cacheDir = Get-FinGrindProjectCacheDir -RepositoryRoot $repoRoot
            buildRoot = Get-FinGrindProjectBuildRoot -RepositoryRoot $repoRoot
            rootBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "root"
            cliBuildDir = Get-FinGrindProjectBuildDir -RepositoryRoot $repoRoot -ProjectSegment "cli"
            externalize = Test-FinGrindProjectBuildExternalization -RepositoryRoot $repoRoot
        }
        $payload | ConvertTo-Json -Compress
    '
)"

python3 - <<'PY' \
    "${expected_cache_key}" \
    "${expected_cache_dir}" \
    "${expected_build_root}" \
    "${expected_root_build_dir}" \
    "${expected_cli_build_dir}" \
    "${actual_json}"
import json
import sys

expected_cache_key = sys.argv[1]
expected_cache_dir = sys.argv[2]
expected_build_root = sys.argv[3]
expected_root_build_dir = sys.argv[4]
expected_cli_build_dir = sys.argv[5]
payload = json.loads(sys.argv[6])

if payload["cacheKey"] != expected_cache_key:
    raise SystemExit(
        f"PowerShell helper cache key drifted: {payload['cacheKey']} != {expected_cache_key}"
    )
if payload["cacheDir"] != expected_cache_dir:
    raise SystemExit(
        f"PowerShell helper cache dir drifted: {payload['cacheDir']} != {expected_cache_dir}"
    )
if payload["buildRoot"] != expected_build_root:
    raise SystemExit(
        f"PowerShell helper build root drifted: {payload['buildRoot']} != {expected_build_root}"
    )
if payload["rootBuildDir"] != expected_root_build_dir:
    raise SystemExit(
        "PowerShell helper root build dir drifted: "
        f"{payload['rootBuildDir']} != {expected_root_build_dir}"
    )
if payload["cliBuildDir"] != expected_cli_build_dir:
    raise SystemExit(
        "PowerShell helper cli build dir drifted: "
        f"{payload['cliBuildDir']} != {expected_cli_build_dir}"
    )
if payload["externalize"] is not True:
    raise SystemExit("PowerShell helper lost the POSIX project-build externalization contract")
PY

windows_plan_json="$(
    REPO_ROOT="${repo_root}" pwsh -NoLogo -NoProfile -Command '
        . (Join-Path $env:REPO_ROOT "scripts/gradle-wrapper-support.ps1")
        $plans = [ordered]@{
            runner = Get-FinGrindWindowsGradleWrapperPlanForEnvironment `
                -RepositoryRoot "C:\work dir\FinGrind" `
                -Environment @{
                    RUNNER_TEMP = "D:\runner temp"
                    TEMP = "E:\temp"
                    LOCALAPPDATA = "C:\Users\runner\AppData\Local"
                }
            temp = Get-FinGrindWindowsGradleWrapperPlanForEnvironment `
                -RepositoryRoot "C:\work dir\FinGrind" `
                -Environment @{
                    TEMP = "E:\temp"
                    LOCALAPPDATA = "C:\Users\runner\AppData\Local"
                }
            localAppData = Get-FinGrindWindowsGradleWrapperPlanForEnvironment `
                -RepositoryRoot "C:\work dir\FinGrind" `
                -Environment @{ LOCALAPPDATA = "C:\Users\runner\AppData\Local" }
            fallback = Get-FinGrindWindowsGradleWrapperPlanForEnvironment `
                -RepositoryRoot "C:\work dir\FinGrind" `
                -Environment @{}
            cacheRootOverride = Get-FinGrindWindowsGradleWrapperPlanForEnvironment `
                -RepositoryRoot "C:\work dir\FinGrind" `
                -Environment @{ FINGRIND_GRADLE_PROJECT_CACHE_ROOT = "Q:\shared cache" }
            overridden = Get-FinGrindWindowsGradleWrapperPlanForEnvironment `
                -RepositoryRoot "C:\work dir\FinGrind" `
                -Environment @{
                    FINGRIND_PROJECT_CACHE_KEY = "chosen key"
                    FINGRIND_GRADLE_PROJECT_CACHE_ROOT = "Z:\root"
                    FINGRIND_GRADLE_PROJECT_CACHE_DIR = "Z:\cache-dir"
                    FINGRIND_GRADLE_BUILD_LOGIC_DIR = "Z:\logic"
                    FINGRIND_GRADLE_JACOCO_ROOT = "Z:\jacoco"
                    FINGRIND_GRADLE_PROJECT_BUILD_ROOT = "Z:\build"
                    FINGRIND_GRADLE_INVOCATION_LEASE_ROOT = "Y:\lease"
                }
            unc = Get-FinGrindWindowsGradleWrapperPlanForEnvironment `
                -RepositoryRoot "\\server\share\repo" `
                -Environment @{}
            normalizedJoin = Join-FinGrindWindowsPath `
                -ParentPath "C:\cache\\" `
                -ChildPath "\\nested\leaf"
        }
        $plans | ConvertTo-Json -Depth 3 -Compress
    '
)"

python3 - <<'PY' "${windows_plan_json}"
import json
import sys

payload = json.loads(sys.argv[1])


def assert_plan(
    name,
    *,
    cache_key,
    cache_root,
    cache_dir,
    build_logic_dir,
    jacoco_root,
    should_externalize,
    build_root,
    invocation_lease_root,
    invocation_lease_file,
):
    actual = payload[name]
    expected = {
        "ProjectCacheKey": cache_key,
        "ProjectCacheRoot": cache_root,
        "ProjectCacheDir": cache_dir,
        "BuildLogicDir": build_logic_dir,
        "JacocoRoot": jacoco_root,
        "ShouldExternalizeProjectBuildRoot": should_externalize,
        "ProjectBuildRoot": build_root,
        "InvocationLeaseRoot": invocation_lease_root,
        "InvocationLeaseFile": invocation_lease_file,
    }
    if actual != expected:
        raise SystemExit(
            f"Windows wrapper plan {name} drifted:\nexpected={expected!r}\nactual={actual!r}"
        )


runner_root = r"D:\runner temp\fingrind-gradle-project-cache"
runner_dir = runner_root + r"\C__work_dir_FinGrind"
assert_plan(
    "runner",
    cache_key="C__work_dir_FinGrind",
    cache_root=runner_root,
    cache_dir=runner_dir,
    build_logic_dir=runner_dir + r"\build-logic",
    jacoco_root=runner_dir + r"\jacoco",
    should_externalize=False,
    build_root=runner_dir + r"\project-build",
    invocation_lease_root=r"D:\runner temp\fingrind-gradle-invocation-leases",
    invocation_lease_file=r"D:\runner temp\fingrind-gradle-invocation-leases\C__work_dir_FinGrind.lease",
)

temp_root = r"E:\temp\fingrind-gradle-project-cache"
temp_dir = temp_root + r"\C__work_dir_FinGrind"
assert_plan(
    "temp",
    cache_key="C__work_dir_FinGrind",
    cache_root=temp_root,
    cache_dir=temp_dir,
    build_logic_dir=temp_dir + r"\build-logic",
    jacoco_root=temp_dir + r"\jacoco",
    should_externalize=False,
    build_root=temp_dir + r"\project-build",
    invocation_lease_root=r"E:\temp\fingrind-gradle-invocation-leases",
    invocation_lease_file=r"E:\temp\fingrind-gradle-invocation-leases\C__work_dir_FinGrind.lease",
)

local_root = r"C:\Users\runner\AppData\Local\FinGrind\gradle-project-cache"
local_dir = local_root + r"\C__work_dir_FinGrind"
assert_plan(
    "localAppData",
    cache_key="C__work_dir_FinGrind",
    cache_root=local_root,
    cache_dir=local_dir,
    build_logic_dir=local_dir + r"\build-logic",
    jacoco_root=local_dir + r"\jacoco",
    should_externalize=False,
    build_root=local_dir + r"\project-build",
    invocation_lease_root=r"C:\Users\runner\AppData\Local\FinGrind\gradle-invocation-leases",
    invocation_lease_file=r"C:\Users\runner\AppData\Local\FinGrind\gradle-invocation-leases\C__work_dir_FinGrind.lease",
)

fallback_root = r"C:\work dir\FinGrind\.gradle-project-cache"
fallback_dir = fallback_root + r"\C__work_dir_FinGrind"
assert_plan(
    "fallback",
    cache_key="C__work_dir_FinGrind",
    cache_root=fallback_root,
    cache_dir=fallback_dir,
    build_logic_dir=fallback_dir + r"\build-logic",
    jacoco_root=fallback_dir + r"\jacoco",
    should_externalize=False,
    build_root=fallback_dir + r"\project-build",
    invocation_lease_root=r"C:\work dir\FinGrind\.gradle-invocation-leases",
    invocation_lease_file=r"C:\work dir\FinGrind\.gradle-invocation-leases\C__work_dir_FinGrind.lease",
)

cache_root_override_dir = r"Q:\shared cache\C__work_dir_FinGrind"
assert_plan(
    "cacheRootOverride",
    cache_key="C__work_dir_FinGrind",
    cache_root=r"Q:\shared cache",
    cache_dir=cache_root_override_dir,
    build_logic_dir=cache_root_override_dir + r"\build-logic",
    jacoco_root=cache_root_override_dir + r"\jacoco",
    should_externalize=False,
    build_root=cache_root_override_dir + r"\project-build",
    invocation_lease_root=r"C:\work dir\FinGrind\.gradle-invocation-leases",
    invocation_lease_file=r"C:\work dir\FinGrind\.gradle-invocation-leases\C__work_dir_FinGrind.lease",
)

assert_plan(
    "overridden",
    cache_key="chosen_key",
    cache_root=r"Z:\root",
    cache_dir=r"Z:\cache-dir",
    build_logic_dir=r"Z:\logic",
    jacoco_root=r"Z:\jacoco",
    should_externalize=True,
    build_root=r"Z:\build",
    invocation_lease_root=r"Y:\lease",
    invocation_lease_file=r"Y:\lease\chosen_key.lease",
)

unc_root = r"\\server\share\repo\.gradle-project-cache"
unc_dir = unc_root + r"\__server_share_repo"
assert_plan(
    "unc",
    cache_key="__server_share_repo",
    cache_root=unc_root,
    cache_dir=unc_dir,
    build_logic_dir=unc_dir + r"\build-logic",
    jacoco_root=unc_dir + r"\jacoco",
    should_externalize=True,
    build_root=unc_dir + r"\project-build",
    invocation_lease_root=r"\\server\share\repo\.gradle-invocation-leases",
    invocation_lease_file=r"\\server\share\repo\.gradle-invocation-leases\__server_share_repo.lease",
)

if payload["normalizedJoin"] != r"C:\cache\nested\leaf":
    raise SystemExit("Windows wrapper plan no longer normalizes one parent/child separator boundary")
PY

printf 'PowerShell Gradle wrapper helper regression: success\n'
