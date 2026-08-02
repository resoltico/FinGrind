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
        -Arguments (@("-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $ScriptPath) + $Arguments)
}

function New-FinGrindWindowsPublicationWorkspaceSmokeDirectory {
    [CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "Medium")]
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $resolvedRepositoryRoot = Resolve-FinGrindWindowsPublicationDirectory `
        -Path $RepositoryRoot `
        -Label "target repository"
    $directoryPath = Join-Path `
        $resolvedRepositoryRoot `
        (".fingrind-windows-bundle-smoke-" + [System.Guid]::NewGuid().ToString("N"))
    if (-not $PSCmdlet.ShouldProcess(
            $directoryPath,
            "create the isolated Windows bundle-smoke temporary directory"
        )) {
        return $null
    }
    return [System.IO.Directory]::CreateDirectory($directoryPath).FullName
}

function Remove-FinGrindWindowsPublicationWorkspaceSmokeDirectory {
    [CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "Medium")]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Directory,

        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $resolvedRepositoryRoot = Resolve-FinGrindWindowsPublicationDirectory `
        -Path $RepositoryRoot `
        -Label "target repository"
    $resolvedDirectory = [System.IO.Path]::GetFullPath($Directory)
    $expectedPrefix = $resolvedRepositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + `
        [System.IO.Path]::DirectorySeparatorChar + ".fingrind-windows-bundle-smoke-"
    $resolvedParentDirectory = Split-Path -Path $resolvedDirectory -Parent
    if (-not [string]::Equals(
            $resolvedParentDirectory,
            $resolvedRepositoryRoot,
            [System.StringComparison]::OrdinalIgnoreCase
        ) -or -not $resolvedDirectory.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Windows publication verification refused to remove a directory outside its private bundle-smoke root"
    }
    if ((Test-Path -LiteralPath $resolvedDirectory -PathType Container) -and
        $PSCmdlet.ShouldProcess(
            $resolvedDirectory,
            "remove the isolated Windows bundle-smoke temporary directory"
        )) {
        $directoryItem = Get-Item -LiteralPath $resolvedDirectory -Force
        if (($directoryItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Windows publication verification refused to remove a reparse-point bundle-smoke directory"
        }
        Remove-Item -LiteralPath $resolvedDirectory -Recurse -Force
    }
}

function New-FinGrindWindowsPublicationPrivateTestDirectory {
    [CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "Medium")]
    param(
        [Parameter(Mandatory = $true)]
        [string]$PrivateTestVolumeRoot
    )

    $resolvedPrivateTestVolumeRoot = Resolve-FinGrindWindowsPublicationDirectory `
        -Path $PrivateTestVolumeRoot `
        -Label "private test volume root"
    $directoryPath = Join-Path `
        $resolvedPrivateTestVolumeRoot `
        ("fingrind-private-test-" + [System.Guid]::NewGuid().ToString("N"))
    if (-not $PSCmdlet.ShouldProcess(
            $directoryPath,
            "create the private Windows publication-verification directory"
        )) {
        return $null
    }
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
    [CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "Medium")]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Directory,

        [Parameter(Mandatory = $true)]
        [string]$PrivateTestVolumeRoot
    )

    $resolvedPrivateTestVolumeRoot = Resolve-FinGrindWindowsPublicationDirectory `
        -Path $PrivateTestVolumeRoot `
        -Label "private test volume root"
    $resolvedDirectory = [System.IO.Path]::GetFullPath($Directory)
    $expectedPrefix = $resolvedPrivateTestVolumeRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + `
        [System.IO.Path]::DirectorySeparatorChar + "fingrind-private-test-"
    $resolvedParentDirectory = Split-Path -Path $resolvedDirectory -Parent
    if ((-not [string]::Equals(
            $resolvedParentDirectory,
            $resolvedPrivateTestVolumeRoot,
            [System.StringComparison]::OrdinalIgnoreCase
        )) -or -not $resolvedDirectory.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Windows publication verification refused to remove a directory outside its private test root"
    }
    if ((Test-Path -LiteralPath $resolvedDirectory -PathType Container) -and
        $PSCmdlet.ShouldProcess(
            $resolvedDirectory,
            "remove the private Windows publication-verification directory"
        )) {
        Remove-Item -LiteralPath $resolvedDirectory -Recurse -Force
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

    $privateTestVolumeRoot = [System.IO.Path]::GetPathRoot([System.Environment]::SystemDirectory)
    if ([string]::IsNullOrWhiteSpace($privateTestVolumeRoot)) {
        throw "Windows publication verification could not resolve the system-volume root"
    }
    $privateTestDirectory = New-FinGrindWindowsPublicationPrivateTestDirectory `
        -PrivateTestVolumeRoot $privateTestVolumeRoot
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
        Invoke-FinGrindWindowsPublicationNative `
            -Label "Windows deep Unicode SQLite path verification" `
            -CommandPath $gradleWrapper `
            -Arguments @(
                ":sqlite:test",
                "--tests", "dev.erst.fingrind.sqlite.SqliteNativeOpenAndRekeyTest.openCreatesAndReopensAProtectedBookAtADeepUnicodePath",
                "--no-daemon",
                "--console=plain"
            )
    } finally {
        [System.Environment]::SetEnvironmentVariable(
            $privateTestDirectoryProperty,
            $previousPrivateTestDirectory,
            [System.EnvironmentVariableTarget]::Process
        )
        Remove-FinGrindWindowsPublicationPrivateTestDirectory `
            -Directory $privateTestDirectory `
            -PrivateTestVolumeRoot $privateTestVolumeRoot
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
    $bundleSmokeTemporaryDirectory = New-FinGrindWindowsPublicationWorkspaceSmokeDirectory `
        -RepositoryRoot $RepositoryRoot
    $bundleSmokeTemporaryVariableNames = @("TEMP", "TMP")
    $previousBundleSmokeTemporaryValues = @{}
    foreach ($variableName in $bundleSmokeTemporaryVariableNames) {
        $previousBundleSmokeTemporaryValues[$variableName] = [System.Environment]::GetEnvironmentVariable(
            $variableName,
            [System.EnvironmentVariableTarget]::Process
        )
        [System.Environment]::SetEnvironmentVariable(
            $variableName,
            $bundleSmokeTemporaryDirectory,
            [System.EnvironmentVariableTarget]::Process
        )
    }
    try {
        Invoke-FinGrindWindowsPublicationPowerShellFile `
            -Label "Windows CLI bundle smoke verification" `
            -PowerShellExecutable $PowerShellExecutable `
            -ScriptPath $bundleSmokeVerifier `
            -Arguments @($publicationArtifacts.ArchivePath)
    } finally {
        foreach ($variableName in $bundleSmokeTemporaryVariableNames) {
            [System.Environment]::SetEnvironmentVariable(
                $variableName,
                $previousBundleSmokeTemporaryValues[$variableName],
                [System.EnvironmentVariableTarget]::Process
            )
        }
        Remove-FinGrindWindowsPublicationWorkspaceSmokeDirectory `
            -Directory $bundleSmokeTemporaryDirectory `
            -RepositoryRoot $RepositoryRoot
    }

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
