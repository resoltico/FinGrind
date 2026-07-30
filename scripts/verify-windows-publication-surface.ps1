[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$RepositoryRoot,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$WorkflowHelperRoot,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ExpectedOperatingSystemId,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ExpectedArchitectureId,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$BundleClassifier,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$PowerShellExecutable,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$OutputFile
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $IsWindows) {
    throw "verify-windows-publication-surface.ps1 can only run on a native Windows runner"
}

. (Join-Path $PSScriptRoot "verify-windows-publication-surface-support.ps1")

function Invoke-FinGrindWindowsPublicationNative {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Label,

        [Parameter(Mandatory = $true)]
        [string]$CommandPath,

        [Parameter()]
        [string[]]$Arguments = @()
    )

    & $CommandPath @Arguments
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "$Label failed with exit code $exitCode"
    }
}

function Invoke-FinGrindWindowsPublicationPowerShellFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Label,

        [Parameter(Mandatory = $true)]
        [string]$PowerShellExecutable,

        [Parameter(Mandatory = $true)]
        [string]$ScriptPath,

        [Parameter()]
        [string[]]$Arguments = @()
    )

    Invoke-FinGrindWindowsPublicationNative `
        -Label $Label `
        -CommandPath $PowerShellExecutable `
        -Arguments @("-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $ScriptPath) + $Arguments
}

$RepositoryRoot = Resolve-FinGrindWindowsPublicationDirectory `
    -Path $RepositoryRoot `
    -Label "target repository"
$WorkflowHelperRoot = Resolve-FinGrindWindowsPublicationDirectory `
    -Path $WorkflowHelperRoot `
    -Label "workflow helper"
$scriptOwnerRoot = Resolve-FinGrindWindowsPublicationDirectory `
    -Path (Split-Path -Path $PSScriptRoot -Parent) `
    -Label "workflow verifier owner"
if (-not [string]::Equals(
        $WorkflowHelperRoot,
        $scriptOwnerRoot,
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
    throw "workflow helper root must own verify-windows-publication-surface.ps1"
}
$OutputFile = Resolve-FinGrindWindowsPublicationWorkflowOutputFile -Path $OutputFile
$PowerShellExecutable = Resolve-FinGrindWindowsPublicationFile `
    -Path $PowerShellExecutable `
    -Label "pinned PowerShell executable"

# The helper-root verifier is release-control policy. Every input that determines the published
# payload is resolved from the explicitly supplied target root, so a repaired workflow-dispatch
# run cannot silently substitute main's source for the immutable tagged release source.
$gradleWrapper = Resolve-FinGrindWindowsPublicationRepositoryFile `
    -RepositoryRoot $RepositoryRoot `
    -Path (Join-Path $RepositoryRoot "gradlew.bat") `
    -Label "target Gradle wrapper"
$directJavaRuntimeVerifier = Resolve-FinGrindWindowsPublicationRepositoryFile `
    -RepositoryRoot $RepositoryRoot `
    -Path (Join-Path $RepositoryRoot "scripts/verify-direct-java-sqlite-runtime.ps1") `
    -Label "target direct-Java SQLite runtime verifier"
$sourceCheckoutRuntimeVerifier = Resolve-FinGrindWindowsPublicationRepositoryFile `
    -RepositoryRoot $RepositoryRoot `
    -Path (Join-Path $RepositoryRoot "scripts/verify-source-checkout-sqlite-runtime.ps1") `
    -Label "target source-checkout SQLite runtime verifier"
$bundleSmokeVerifier = Resolve-FinGrindWindowsPublicationRepositoryFile `
    -RepositoryRoot $RepositoryRoot `
    -Path (Join-Path $RepositoryRoot "scripts/bundle-smoke.ps1") `
    -Label "target Windows bundle smoke verifier"
$runnerIdentityVerifier = Resolve-FinGrindWindowsPublicationRepositoryFile `
    -RepositoryRoot $WorkflowHelperRoot `
    -Path (Join-Path $WorkflowHelperRoot "scripts/verify-runner-identity.py") `
    -Label "workflow runner-identity verifier"
$windowsPublicationPolicy = Resolve-FinGrindWindowsPublicationRepositoryFile `
    -RepositoryRoot $WorkflowHelperRoot `
    -Path (Join-Path $WorkflowHelperRoot "scripts/windows_publication_policy.py") `
    -Label "workflow Windows publication policy"
$pythonExecutable = Resolve-FinGrindWindowsPublicationFile `
    -Path ((Get-Command python -CommandType Application -ErrorAction Stop).Source) `
    -Label "workflow Python executable"

Push-Location $RepositoryRoot
try {
    $actualArchitecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
    Invoke-FinGrindWindowsPublicationNative `
        -Label "Windows runner identity verification" `
        -CommandPath $pythonExecutable `
        -Arguments @(
            $runnerIdentityVerifier,
            "--expected-os-id", $ExpectedOperatingSystemId,
            "--expected-arch-id", $ExpectedArchitectureId,
            "--actual-os-name", "Windows",
            "--actual-architecture", $actualArchitecture
        )

    Invoke-FinGrindWindowsPublicationNative `
        -Label "Windows build-logic verification" `
        -CommandPath $gradleWrapper `
        -Arguments @("-p", "gradle/build-logic", "test", "--no-daemon", "--console=plain")

    Invoke-FinGrindWindowsPublicationNative `
        -Label "Windows attestation codec verification" `
        -CommandPath $gradleWrapper `
        -Arguments @(
            ":core:test",
            "--tests", "dev.erst.fingrind.core.attestation.*",
            "--no-daemon",
            "--console=plain"
        )

    Invoke-FinGrindWindowsPublicationPowerShellFile `
        -Label "Windows direct-Java SQLite runtime verification" `
        -PowerShellExecutable $PowerShellExecutable `
        -ScriptPath $directJavaRuntimeVerifier
    Invoke-FinGrindWindowsPublicationPowerShellFile `
        -Label "Windows source-checkout SQLite runtime verification" `
        -PowerShellExecutable $PowerShellExecutable `
        -ScriptPath $sourceCheckoutRuntimeVerifier

    $publicationPlan = Get-FinGrindWindowsPublicationPlan `
        -RepositoryRoot $RepositoryRoot `
        -ExpectedOperatingSystemId $ExpectedOperatingSystemId `
        -ExpectedArchitectureId $ExpectedArchitectureId `
        -BundleClassifier $BundleClassifier `
        -PythonExecutable $pythonExecutable `
        -PolicyScriptPath $windowsPublicationPolicy

    Invoke-FinGrindWindowsPublicationNative `
        -Label "Windows CLI bundle build" `
        -CommandPath $gradleWrapper `
        -Arguments @(
            ":cli:bundleCliArchive",
            "-PfingrindBundleClassifier=$BundleClassifier",
            "--no-daemon",
            "--console=plain"
        )

    $publicationArtifacts = Resolve-FinGrindWindowsPublicationArtifactSet `
        -Plan $publicationPlan `
        -PythonExecutable $pythonExecutable `
        -PolicyScriptPath $windowsPublicationPolicy
    Invoke-FinGrindWindowsPublicationPowerShellFile `
        -Label "Windows CLI bundle smoke verification" `
        -PowerShellExecutable $PowerShellExecutable `
        -ScriptPath $bundleSmokeVerifier `
        -Arguments @($publicationArtifacts.ArchivePath)

    Write-FinGrindWindowsPublicationWorkflowOutput `
        -Path $OutputFile `
        -Name "archive-path" `
        -Value $publicationArtifacts.ArchivePath `
        -PythonExecutable $pythonExecutable `
        -PolicyScriptPath $windowsPublicationPolicy
    Write-FinGrindWindowsPublicationWorkflowOutput `
        -Path $OutputFile `
        -Name "checksum-path" `
        -Value $publicationArtifacts.ChecksumPath `
        -PythonExecutable $pythonExecutable `
        -PolicyScriptPath $windowsPublicationPolicy
    Write-Information -MessageData "Windows publication surface: success" -InformationAction Continue
} finally {
    Pop-Location
}
