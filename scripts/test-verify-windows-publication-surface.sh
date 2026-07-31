#!/usr/bin/env bash
# Regress the cross-platform publication policy and its native Windows filesystem adapter.

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
readonly verifier_entry="${repo_root}/scripts/verify-windows-publication-surface.ps1"
readonly verifier_support="${repo_root}/scripts/verify-windows-publication-surface-support.ps1"
readonly policy_owner="${repo_root}/scripts/windows_publication_policy.py"
readonly plan_policy="${repo_root}/scripts/windows_publication_plan_policy.py"
readonly manifest_policy="${repo_root}/scripts/windows_publication_manifest_policy.py"
readonly protocol_policy="${repo_root}/scripts/windows_publication_policy_protocol.py"
readonly policy_boundary="${repo_root}/scripts/windows_publication_policy_boundary.py"
readonly policy_test="${repo_root}/scripts/test_windows_publication_policy.py"

[[ -f "${verifier_entry}" ]] || die "missing native Windows publication verifier at ${verifier_entry}"
[[ -f "${verifier_support}" ]] || die "missing Windows publication filesystem adapter at ${verifier_support}"
[[ -f "${policy_owner}" ]] || die "missing cross-platform Windows publication policy at ${policy_owner}"
[[ -f "${plan_policy}" ]] || die "missing canonical Windows publication plan policy at ${plan_policy}"
[[ -f "${manifest_policy}" ]] || die "missing Windows publication manifest policy at ${manifest_policy}"
[[ -f "${protocol_policy}" ]] || die "missing Windows publication protocol policy at ${protocol_policy}"
[[ -f "${policy_boundary}" ]] || die "missing Windows publication policy boundary at ${policy_boundary}"
[[ -f "${policy_test}" ]] || die "missing Windows publication policy fixtures at ${policy_test}"
grep -Fq 'StandardInputEncoding' "${verifier_support}" || die \
    'Windows publication adapter no longer pins UTF-8 for policy standard input'
grep -Fq 'StandardOutputEncoding' "${verifier_support}" || die \
    'Windows publication adapter no longer pins UTF-8 for policy standard output'
grep -Fq 'StandardErrorEncoding' "${verifier_support}" || die \
    'Windows publication adapter no longer pins UTF-8 for policy standard error'
grep -Fq 'ArgumentList.Add("-I")' "${verifier_support}" || die \
    'Windows publication adapter no longer isolates pure-policy imports from ambient Python state'
grep -Fq 'ArgumentList.Add("-B")' "${verifier_support}" || die \
    'Windows publication adapter no longer prevents isolated policy bytecode writes in the helper checkout'
grep -Fq 'ArgumentList.Add("-X")' "${verifier_support}" || die \
    'Windows publication adapter no longer forces UTF-8 for its isolated policy wire'
grep -Fq 'ArgumentList.Add("utf8")' "${verifier_support}" || die \
    'Windows publication adapter no longer selects Python UTF-8 mode for its policy wire'
command -v python3 >/dev/null 2>&1 || die 'Python 3 is required for Windows publication policy fixtures'
PYTHONDONTWRITEBYTECODE=1 python3 "${policy_test}" || die 'Windows publication policy fixtures failed'
grep -Fq 'verify-windows-publication-surface-support.ps1' "${verifier_entry}" || die \
    'native Windows verifier no longer delegates filesystem admission to its support adapter'
# shellcheck disable=SC2016
grep -Fq 'if (-not $IsWindows)' "${verifier_entry}" || die \
    'native Windows verifier no longer rejects non-Windows execution explicitly'
# shellcheck disable=SC2016
grep -Fq -- '-PowerShellExecutable $PowerShellExecutable' "${verifier_entry}" || die \
    'native Windows verifier no longer uses the supplied pinned PowerShell executable for child proofs'
grep -Fq 'New-FinGrindWindowsPublicationPrivateTestDirectory' "${verifier_entry}" || die \
    'native Windows verifier no longer establishes a private test directory before attestation verification'
grep -Fq 'Remove-FinGrindWindowsPublicationPrivateTestDirectory' "${verifier_entry}" || die \
    'native Windows verifier no longer removes its private test directory after attestation verification'
grep -Fq -- '-PfingrindTestTemporaryDirectory=$privateTestDirectory' "${verifier_entry}" || die \
    'native Windows verifier no longer supplies its private test directory to Gradle'
grep -Fq 'windows_publication_policy.py' "${verifier_entry}" || die \
    'native Windows verifier no longer resolves the cross-platform publication policy owner'
grep -Fq 'Get-FinGrindWindowsPublicationPlan' "${verifier_support}" || die \
    'Windows publication support adapter no longer invokes the canonical publication-plan policy'
grep -Fq 'Resolve-FinGrindWindowsPublicationArtifactSet' "${verifier_support}" || die \
    'Windows publication support adapter no longer admits artifacts validated by the policy owner'
if grep -Fq 'ReportedCliBuildDirectory' "${verifier_entry}" || \
    grep -Fq 'ReportedCliBuildDirectory' "${verifier_support}" || \
    rg -Fq 'ReportedCliBuildDirectory' \
        "${policy_owner}" \
        "${plan_policy}" \
        "${manifest_policy}" \
        "${protocol_policy}" \
        "${policy_boundary}"; then
    die 'native Windows publication verification still lets tagged source choose its build directory'
fi
grep -Fq 'windows_publication_policy_protocol' "${policy_owner}" || die \
    'isolated Windows publication entrypoint no longer delegates to the protocol owner'
grep -Fq 'build_publication_plan' "${plan_policy}" || die \
    'cross-platform publication policy no longer derives the canonical target cli/build directory'
grep -Fq 'validate_manifest_artifacts' "${manifest_policy}" || die \
    'cross-platform publication policy no longer validates the bundle manifest against the plan'
grep -Fq 'serialize_workflow_output' "${protocol_policy}" || die \
    'cross-platform publication policy no longer serializes GitHub workflow output records'
grep -Fq 'must not traverse a reparse point' "${verifier_support}" || die \
    'Windows publication filesystem adapter no longer rejects repository reparse points'
grep -Fq 'must be an absolute path' "${policy_boundary}" || die \
    'cross-platform publication policy no longer requires absolute manifest artifact paths'
if grep -Fq 'function Resolve-FinGrindWindowsPublicationManifestArtifact' "${verifier_support}"; then
    die 'PowerShell support adapter still owns manifest path-equality policy'
fi

if ! command -v pwsh >/dev/null 2>&1; then
    printf 'Windows publication-surface regression: skipped native adapter fixture (pwsh unavailable)\n'
    exit 0
fi

fixture_root="$(mktemp -d "${repo_root}/tmp/fingrind-windows-publication-surface.XXXXXX")"
cleanup_fixture() {
    rm -rf -- "${fixture_root}"
}
trap cleanup_fixture EXIT

# shellcheck disable=SC2016,SC2034
FINGRIND_WINDOWS_PUBLICATION_SUPPORT="${verifier_support}" \
FINGRIND_WINDOWS_PUBLICATION_FIXTURE_ROOT="${fixture_root}" \
FINGRIND_WINDOWS_PUBLICATION_POLICY="${policy_owner}" \
FINGRIND_WINDOWS_PUBLICATION_PYTHON="$(command -v python3)" \
pwsh -NoLogo -NoProfile -Command '
    $ErrorActionPreference = "Stop"
    Set-StrictMode -Version Latest

    . $env:FINGRIND_WINDOWS_PUBLICATION_SUPPORT
    $root = [System.IO.Path]::GetFullPath(
        (Join-Path $env:FINGRIND_WINDOWS_PUBLICATION_FIXTURE_ROOT "Rīga target-repository")
    )
    $python = $env:FINGRIND_WINDOWS_PUBLICATION_PYTHON
    $policyScript = $env:FINGRIND_WINDOWS_PUBLICATION_POLICY
    $maliciousPythonDirectory = Join-Path $root "malicious-python"
    [System.IO.Directory]::CreateDirectory($maliciousPythonDirectory) | Out-Null
    [System.IO.File]::WriteAllText(
        (Join-Path $maliciousPythonDirectory "json.py"),
        "raise RuntimeError(""ambient PYTHONPATH was imported"")`n",
        [System.Text.UTF8Encoding]::new($false)
    )
    $env:PYTHONPATH = $maliciousPythonDirectory
    $contractDirectory = Join-Path `
        $root `
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol"
    [System.IO.Directory]::CreateDirectory($contractDirectory) | Out-Null
    [System.IO.File]::WriteAllText(
        (Join-Path $root "gradle.properties"),
        "version=0.62.0$([System.Environment]::NewLine)",
        [System.Text.UTF8Encoding]::new($false)
    )
    [System.IO.File]::WriteAllText(
        (Join-Path $contractDirectory "bundle-layout-contract.json"),
        @"
{"bundleTargets":{"windows-x86_64":{"operatingSystemId":"windows","architectureId":"x86_64","archiveFormat":"zip"}}}
"@,
        [System.Text.UTF8Encoding]::new($false)
    )

    $canonicalCliBuildDirectory = Join-Path $root "cli/build"
    $plan = Get-FinGrindWindowsPublicationPlan `
        -RepositoryRoot $root `
        -ExpectedOperatingSystemId "windows" `
        -ExpectedArchitectureId "x86_64" `
        -BundleClassifier "windows-x86_64" `
        -PythonExecutable $python `
        -PolicyScriptPath $policyScript
    $expectedArchivePath = [System.IO.Path]::GetFullPath(
        (Join-Path $root "cli/build/distributions/fingrind-0.62.0-windows-x86_64.zip")
    )
    if ($plan.ArchivePath -ne $expectedArchivePath -or
        $plan.ChecksumPath -ne "$expectedArchivePath.sha256") {
        throw "canonical Windows publication plan did not derive exact archive paths"
    }
    if ((Get-Command Get-FinGrindWindowsPublicationPlan).Parameters.ContainsKey("ReportedCliBuildDirectory")) {
        throw "Windows publication policy still accepts a target-selected CLI build directory"
    }
    [System.IO.Directory]::CreateDirectory((Split-Path -Path $plan.ArchivePath -Parent)) | Out-Null
    [System.IO.File]::WriteAllText($plan.ArchivePath, "archive", [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($plan.ChecksumPath, "checksum", [System.Text.UTF8Encoding]::new($false))
    [System.IO.Directory]::CreateDirectory((Split-Path -Path $plan.ManifestPath -Parent)) | Out-Null
    [System.IO.File]::WriteAllText(
        $plan.ManifestPath,
        (@{ archivePath = $plan.ArchivePath; checksumPath = $plan.ChecksumPath } | ConvertTo-Json -Compress),
        [System.Text.UTF8Encoding]::new($false)
    )
    $artifacts = Resolve-FinGrindWindowsPublicationArtifactSet `
        -Plan $plan `
        -PythonExecutable $python `
        -PolicyScriptPath $policyScript
    if ($artifacts.ArchivePath -ne $plan.ArchivePath -or $artifacts.ChecksumPath -ne $plan.ChecksumPath) {
        throw "canonical Windows publication manifest did not resolve the expected artifacts"
    }

    [System.IO.File]::WriteAllText(
        $plan.ManifestPath,
        (@{ archivePath = (Join-Path $root "outside.zip"); checksumPath = $plan.ChecksumPath } | ConvertTo-Json -Compress),
        [System.Text.UTF8Encoding]::new($false)
    )
    $wrongPathRejected = $false
    try {
        Resolve-FinGrindWindowsPublicationArtifactSet `
            -Plan $plan `
            -PythonExecutable $python `
            -PolicyScriptPath $policyScript | Out-Null
    } catch {
        $wrongPathRejected = $_.Exception.Message -like "*does not match the canonical Windows publication path*"
    }
    if (-not $wrongPathRejected) {
        throw "Windows publication policy accepted a noncanonical manifest artifact path"
    }

    [System.IO.File]::WriteAllText(
        $plan.ManifestPath,
        (@{ archivePath = "relative.zip"; checksumPath = $plan.ChecksumPath } | ConvertTo-Json -Compress),
        [System.Text.UTF8Encoding]::new($false)
    )
    $relativePathRejected = $false
    try {
        Resolve-FinGrindWindowsPublicationArtifactSet `
            -Plan $plan `
            -PythonExecutable $python `
            -PolicyScriptPath $policyScript | Out-Null
    } catch {
        $relativePathRejected = $_.Exception.Message -like "*must be an absolute path*"
    }
    if (-not $relativePathRejected) {
        throw "Windows publication policy accepted a relative manifest artifact path"
    }

    $outputPath = Resolve-FinGrindWindowsPublicationWorkflowOutputFile `
        -Path (Join-Path $root "workflow-output.txt")
    Write-FinGrindWindowsPublicationWorkflowOutput `
        -Path $outputPath `
        -Name "archive-path" `
        -Value $plan.ArchivePath `
        -PythonExecutable $python `
        -PolicyScriptPath $policyScript
    if ((Get-Content -LiteralPath $outputPath -Raw -Encoding UTF8) -ne "archive-path=$($plan.ArchivePath)`n") {
        throw "Windows publication workflow output did not preserve the canonical archive path"
    }
    $reparseTargetDirectory = Join-Path $root "actual-output-directory"
    $reparseOutputParent = Join-Path $root "reparse-output-directory"
    [System.IO.Directory]::CreateDirectory($reparseTargetDirectory) | Out-Null
    New-Item -ItemType SymbolicLink -Path $reparseOutputParent -Target $reparseTargetDirectory | Out-Null
    $reparseParentRejected = $false
    try {
        Resolve-FinGrindWindowsPublicationWorkflowOutputFile `
            -Path (Join-Path $reparseOutputParent "workflow-output.txt") | Out-Null
    } catch {
        $reparseParentRejected = $_.Exception.Message -like "*must not traverse a reparse point*"
    }
    if (-not $reparseParentRejected) {
        throw "Windows publication policy accepted a workflow output under a reparse-point parent"
    }

' || die 'Windows publication-surface dynamic fixture failed'

printf 'Windows publication-surface regression: success\n'
