$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Invoke-BundleSmoke {
    param(
        [string[]] $Arguments = @()
    )

    $script:RepoRoot = Split-Path -Path $PSScriptRoot -Parent
    $script:ContractValues = Read-ContractValues
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
    $workRoot = Join-Path $smokeRoot "workspace odd/Rīga büro/2026 Q2 close"
    $script:BundleLauncher = $null

    try {
        New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null
        New-Item -ItemType Directory -Path $workRoot -Force | Out-Null

        Expand-Archive -LiteralPath $bundleArchivePath -DestinationPath $extractRoot -Force
        $extractedRoots = @(Get-ChildItem -LiteralPath $extractRoot -Directory)
        if ($extractedRoots.Count -ne 1) {
            Fail "expected exactly one extracted bundle root under $extractRoot"
        }

        $bundleRoot = $extractedRoots[0].FullName
        $script:BundleLauncher = Join-Path $bundleRoot $hostBundleTarget.launcherPath

        Assert-BundleArchiveContract -BundleRoot $bundleRoot -HostBundleTarget $hostBundleTarget
        Invoke-SharedBundleOfficeWorkerWorkflow -WorkRoot $workRoot

        Write-Host "Bundle acceptance: success"
    } finally {
        if (Test-Path -LiteralPath $smokeRoot) {
            Remove-Item -LiteralPath $smokeRoot -Recurse -Force
        }
    }
}
