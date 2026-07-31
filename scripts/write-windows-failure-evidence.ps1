[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$RepositoryRoot,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$EvidenceDirectory,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$TrustedEvidenceRoot,

    [Parameter()]
    [string]$CommitSha = $env:GITHUB_SHA,

    [Parameter()]
    [string]$RunId = $env:GITHUB_RUN_ID
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# A failed diagnostic must never obscure the Windows proof that triggered collection. The sibling
# collector owns the rich allowlisted record; this owner guarantees its one safe fallback shape.
$collectorPath = Join-Path $PSScriptRoot "collect-windows-ci-failure-evidence.ps1"
if (-not (Test-Path -LiteralPath $collectorPath -PathType Leaf)) {
    throw "missing Windows failure-evidence collector at $collectorPath"
}
$outputPolicyPath = Join-Path $PSScriptRoot "windows-failure-evidence-output.ps1"
if (-not (Test-Path -LiteralPath $outputPolicyPath -PathType Leaf)) {
    throw "missing Windows failure-evidence output policy at $outputPolicyPath"
}
. $outputPolicyPath

$evidenceDirectoryPath = New-TrustedEvidenceDirectory `
    -EvidenceDirectory $EvidenceDirectory `
    -TrustedEvidenceRoot $TrustedEvidenceRoot
try {
    & $collectorPath `
        -RepositoryRoot $RepositoryRoot `
        -EvidenceDirectory $evidenceDirectoryPath `
        -TrustedEvidenceRoot $TrustedEvidenceRoot `
        -EvidenceDirectoryPrepared `
        -CommitSha $CommitSha `
        -RunId $RunId
    if (-not $?) {
        throw "Windows failure-evidence collector did not complete successfully"
    }
} catch {
    Write-Warning "Windows failure-evidence collection did not complete; writing a safe fallback document."
    $fallbackEvidence = [ordered]@{
        schemaVersion = 2
        collectionStatus = "fallback"
        privacy = [ordered]@{
            collectionMode = "allowlisted-normalized"
            rawLogsIncluded = $false
            workspaceFilesCopied = $false
            bookOrKeyFilesIncluded = $false
            environmentDumpIncluded = $false
        }
    }
    Write-NewTrustedEvidenceDocument `
        -EvidenceDirectory $evidenceDirectoryPath `
        -TrustedEvidenceRoot $TrustedEvidenceRoot `
        -Content (($fallbackEvidence | ConvertTo-Json -Depth 4 -Compress) + [System.Environment]::NewLine) | Out-Null
}
