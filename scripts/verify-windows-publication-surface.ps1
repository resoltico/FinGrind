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
        [string[]]$Arguments = @(),

        [Parameter()]
        [switch]$SuppressOutput
    )

    if ($SuppressOutput) {
        & $CommandPath @Arguments *> $null
    } else {
        & $CommandPath @Arguments
    }
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

function New-FinGrindWindowsPublicationPrivateTestDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RunnerTemporaryRoot
    )

    $resolvedRunnerTemporaryRoot = Resolve-FinGrindWindowsPublicationDirectory `
        -Path $RunnerTemporaryRoot `
        -Label "runner temporary root"
    $directoryPath = Join-Path `
        $resolvedRunnerTemporaryRoot `
        ("fingrind-private-test-" + [System.Guid]::NewGuid().ToString("N"))
    $directory = [System.IO.Directory]::CreateDirectory($directoryPath)
    $currentTokenSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    if ($null -eq $currentTokenSid) {
        throw "Windows publication verification could not resolve the current token user"
    }
    $icaclsPath = Resolve-FinGrindWindowsPublicationFile `
        -Path (Join-Path $env:SystemRoot "System32\icacls.exe") `
        -Label "Windows ACL tool"
    Invoke-FinGrindWindowsPublicationNative `
        -Label "Windows private test-directory ACL initialization" `
        -CommandPath $icaclsPath `
        -Arguments @(
            $directory.FullName,
            "/inheritance:r",
            "/grant:r",
            "*$($currentTokenSid.Value):(OI)(CI)F",
            "/c"
        ) `
        -SuppressOutput
    return $directory.FullName
}

function Remove-FinGrindWindowsPublicationPrivateTestDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Directory,

        [Parameter(Mandatory = $true)]
        [string]$RunnerTemporaryRoot
    )

    $resolvedRunnerTemporaryRoot = Resolve-FinGrindWindowsPublicationDirectory `
        -Path $RunnerTemporaryRoot `
        -Label "runner temporary root"
    $resolvedDirectory = [System.IO.Path]::GetFullPath($Directory)
    $expectedPrefix = $resolvedRunnerTemporaryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + `
        [System.IO.Path]::DirectorySeparatorChar + "fingrind-private-test-"
    if (-not $resolvedDirectory.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Windows publication verification refused to remove a directory outside its private test root"
    }
    if (Test-Path -LiteralPath $resolvedDirectory -PathType Container) {
        Remove-Item -LiteralPath $resolvedDirectory -Recurse -Force
    }
}

function Write-FinGrindWindowsPublicationPrivateRuntimeOwnershipEvidence {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PrivateTestDirectory,

        [Parameter(Mandatory = $true)]
        [System.Security.Principal.SecurityIdentifier]$CurrentTokenSid
    )

    $candidateDirectories = @([System.IO.DirectoryInfo]::new($PrivateTestDirectory))
    if (Test-Path -LiteralPath $PrivateTestDirectory -PathType Container) {
        $fixtureDirectory = Get-ChildItem -LiteralPath $PrivateTestDirectory -Directory -Force |
            Where-Object { $_.Name.StartsWith(".fingrind-attestation-test-", [System.StringComparison]::Ordinal) } |
            Select-Object -First 1
        if ($null -ne $fixtureDirectory) {
            $candidateDirectories += $fixtureDirectory
        }
    }
    $candidateIndex = 0
    foreach ($candidateDirectory in $candidateDirectories) {
        $ancestryDepth = 0
        $ancestor = $candidateDirectory
        while ($null -ne $ancestor) {
            $ownerKind = "UNRESOLVED"
            $ownerSidEvidence = "REDACTED"
            try {
                $owner = (Get-Acl -LiteralPath $ancestor.FullName -ErrorAction Stop).Owner
                $ownerSid = ([System.Security.Principal.NTAccount]::new($owner)).Translate(
                    [System.Security.Principal.SecurityIdentifier]
                )
                if ($ownerSid.Value -eq $CurrentTokenSid.Value) {
                    $ownerKind = "CURRENT_TOKEN"
                } elseif ($ownerSid.Value -in @(
                    "S-1-5-18",
                    "S-1-5-32-544",
                    "S-1-5-80-956008885-3418522649-1831038044-1853292631-2271478464"
                )) {
                    $ownerKind = "TRUSTED_OPERATING_SYSTEM"
                } else {
                    $ownerKind = "OTHER"
                    $ownerSidEvidence = $ownerSid.Value
                }
            } catch {
                $ownerKind = "UNRESOLVED"
            }
            Write-Host (
                "[FINGRIND_WINDOWS_PRIVATE_RUNTIME_EVIDENCE] " +
                "candidate=$candidateIndex ancestryDepth=$ancestryDepth ownerKind=$ownerKind ownerSid=$ownerSidEvidence"
            )
            $ancestor = $ancestor.Parent
            $ancestryDepth++
        }
        $candidateIndex++
    }
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

    $privateTestDirectory = New-FinGrindWindowsPublicationPrivateTestDirectory `
        -RunnerTemporaryRoot $env:RUNNER_TEMP
    $privateTestDirectoryProperty = "ORG_GRADLE_PROJECT_fingrindTestPrivateRoot"
    $previousPrivateTestDirectory = [System.Environment]::GetEnvironmentVariable(
        $privateTestDirectoryProperty,
        [System.EnvironmentVariableTarget]::Process
    )
    [System.Environment]::SetEnvironmentVariable(
        $privateTestDirectoryProperty,
        $privateTestDirectory,
        [System.EnvironmentVariableTarget]::Process
    )
    try {
        Invoke-FinGrindWindowsPublicationNative `
            -Label "Windows attestation codec verification" `
            -CommandPath $gradleWrapper `
            -Arguments @(
                ":core:test",
                "--tests", "dev.erst.fingrind.core.attestation.*",
                "--no-daemon",
                "--console=plain"
            )
    } catch {
        Write-FinGrindWindowsPublicationPrivateRuntimeOwnershipEvidence `
            -PrivateTestDirectory $privateTestDirectory `
            -CurrentTokenSid ([System.Security.Principal.WindowsIdentity]::GetCurrent().User)
        throw
    } finally {
        [System.Environment]::SetEnvironmentVariable(
            $privateTestDirectoryProperty,
            $previousPrivateTestDirectory,
            [System.EnvironmentVariableTarget]::Process
        )
        Remove-FinGrindWindowsPublicationPrivateTestDirectory `
            -Directory $privateTestDirectory `
            -RunnerTemporaryRoot $env:RUNNER_TEMP
    }

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
