$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Initialize-BundleSmokeWorkspace {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $PrivateWorkspaceDirectories,
        [Parameter(Mandatory = $true)]
        [string] $ExtractRoot
    )

    $securityScript = $null
    $powerShellExecutable = $null
    if ($IsWindows) {
        $securityScript = Join-Path $script:RepoRoot "scripts/secure-windows-owner-only-directory.ps1"
        if (-not (Test-Path -LiteralPath $securityScript -PathType Leaf)) {
            Fail "missing Windows owner-only directory security script at $securityScript"
        }
        $powerShellExecutable = Get-FinGrindPowerShellExecutable
    }

    foreach ($directory in $PrivateWorkspaceDirectories) {
        [System.IO.Directory]::CreateDirectory($directory) | Out-Null
        if ($IsWindows) {
            & $powerShellExecutable -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass `
                -File $securityScript $directory
            if ($LASTEXITCODE -ne 0) {
                Fail "could not establish the owner-only Windows bundle smoke directory: $directory"
            }
        }
    }
    [System.IO.Directory]::CreateDirectory($ExtractRoot) | Out-Null
}

function Invoke-BundleSmoke {
    param(
        [string[]] $Arguments = @()
    )

    $script:RepoRoot = Split-Path -Path $PSScriptRoot -Parent
    $script:ContractValues = Read-FinGrindContractValueSet
    $bundleContractVerifier = Join-Path $script:RepoRoot "scripts/verify-bundle-archive-contract.py"
    $hostBundleTarget = $script:ContractValues.bundleLayout.hostBundleTarget
    $expectedArchiveName =
        "fingrind-$(ProjectVersion)-$($hostBundleTarget.classifier).$($hostBundleTarget.archiveFormat)"
    $cliBuildDir =
        if ([string]::IsNullOrWhiteSpace($env:FINGRIND_GRADLE_PROJECT_BUILD_ROOT)) {
            Join-Path $script:RepoRoot "cli/build"
        } else {
            Join-Path $env:FINGRIND_GRADLE_PROJECT_BUILD_ROOT "cli"
        }
    $bundleArchivePath = if ($Arguments.Count -gt 0) { $Arguments[0] } else { Join-Path $cliBuildDir "distributions/$expectedArchiveName" }
    $bundleArchivePath = [System.IO.Path]::GetFullPath($bundleArchivePath)
    $bundleChecksumPath = "$bundleArchivePath.sha256"
    if (-not (Test-Path -LiteralPath $bundleContractVerifier -PathType Leaf)) {
        Fail "missing bundle contract verifier at $bundleContractVerifier"
    }

    if (-not (Test-Path -LiteralPath $bundleArchivePath -PathType Leaf)) {
        Fail "missing bundle archive at $bundleArchivePath"
    }
    if (-not (Test-Path -LiteralPath $bundleChecksumPath -PathType Leaf)) {
        Fail "missing bundle checksum file at $bundleChecksumPath"
    }

    $checksumLine = Get-Content -LiteralPath $bundleChecksumPath | Select-Object -First 1
    $checksumTokens = $checksumLine -split '\s+', 3
    if ($checksumTokens.Count -lt 2) {
        Fail "invalid bundle checksum file at $bundleChecksumPath"
    }
    $expectedArchiveSha256 = $checksumTokens[0]
    $expectedArchiveNameFromChecksum = $checksumTokens[1].TrimStart('*')
    if ($expectedArchiveNameFromChecksum -ne [System.IO.Path]::GetFileName($bundleArchivePath)) {
        Fail "bundle checksum file $bundleChecksumPath does not match archive $([System.IO.Path]::GetFileName($bundleArchivePath))"
    }

    $actualArchiveSha256 = (Get-FileHash -LiteralPath $bundleArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualArchiveSha256 -ne $expectedArchiveSha256.ToLowerInvariant()) {
        Fail "bundle archive checksum mismatch for $bundleArchivePath"
    }

    $smokeRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("fingrind-bundle-acceptance.{0}" -f [guid]::NewGuid().ToString("N"))
    $extractRoot = Join-Path $smokeRoot "extract"
    $workspaceRoot = Join-Path $smokeRoot "workspace odd"
    $unicodeWorkspaceRoot = Join-Path $workspaceRoot ("R" + [char]0x012B + "ga b" + [char]0x00FC + "ro")
    $workRoot = Join-Path $unicodeWorkspaceRoot "2026 Q2 close"
    $privateWorkspaceDirectories = @($smokeRoot, $workspaceRoot, $unicodeWorkspaceRoot, $workRoot)
    $script:BundleLauncher = $null
    $script:BundleRoot = $null

    try {
        Initialize-BundleSmokeWorkspace `
            -PrivateWorkspaceDirectories $privateWorkspaceDirectories `
            -ExtractRoot $extractRoot

        Expand-Archive -LiteralPath $bundleArchivePath -DestinationPath $extractRoot -Force
        $extractedRoots = @(Get-ChildItem -LiteralPath $extractRoot -Directory)
        if ($extractedRoots.Count -ne 1) {
            Fail "expected exactly one extracted bundle root under $extractRoot"
        }

        $bundleRoot = $extractedRoots[0].FullName
        $script:BundleRoot = $bundleRoot
        $script:BundleLauncher = Join-Path $bundleRoot $hostBundleTarget.launcherPath

        & python3 $bundleContractVerifier --repo-root $script:RepoRoot --bundle-root $bundleRoot
        if ($LASTEXITCODE -ne 0) {
            Fail "bundle contract verifier failed"
        }
        Invoke-SharedBundleOfficeWorkerWorkflow -WorkRoot $workRoot

        Write-Information -MessageData "Bundle acceptance: success" -InformationAction Continue
    } finally {
        if (Test-Path -LiteralPath $smokeRoot) {
            Remove-Item -LiteralPath $smokeRoot -Recurse -Force
        }
    }
}
